package io.github.mario2712.serientracker.ui.editseries

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.mario2712.serientracker.data.local.SeriesEntity
import io.github.mario2712.serientracker.data.remote.TmdbSearchResult
import io.github.mario2712.serientracker.data.repository.SeriesRepository
import io.github.mario2712.serientracker.data.repository.TmdbSyncRepository
import io.github.mario2712.serientracker.domain.WatchStatus
import io.github.mario2712.serientracker.domain.Watcher
import io.github.mario2712.serientracker.ui.navigation.Routes
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

data class EditSeriesForm(
    val title: String = "",
    val status: WatchStatus = WatchStatus.PLAN_TO_WATCH,
    val watcher: Watcher? = null,
    val genre: String = "",
    val originalTitle: String = "",
    /** Extra name to tell duplicates apart (e.g. "Rewatch 2026"). */
    val label: String = "",
    val rating: Int? = null,
    val notes: String = "",
    /** Episode count per season as typed, index 0 = season 1. */
    val seasons: List<String> = listOf(""),
    /** New series: TMDB show to take the seasons and episodes from. */
    val tmdbPick: TmdbSearchResult? = null,
) {
    val isValid: Boolean get() = title.isNotBlank()
}

class EditSeriesViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: SeriesRepository,
    private val syncRepository: TmdbSyncRepository,
) : ViewModel() {

    private val seriesId: Long = savedStateHandle[Routes.ARG_SERIES_ID] ?: 0L
    val isNew: Boolean = seriesId == 0L

    private var original: SeriesEntity? = null

    var form by mutableStateOf(EditSeriesForm())
        private set
    var isLoading by mutableStateOf(!isNew)
        private set
    var isSaving by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    /** Existing series linked to TMDB: seasons come from there and are not edited here. */
    val seasonsFromTmdb: Boolean get() = original?.tmdbId != null || form.tmdbPick != null

    var tmdbQuery by mutableStateOf("")
        private set
    var tmdbResults by mutableStateOf<List<TmdbSearchResult>>(emptyList())
        private set

    init {
        if (!isNew) {
            viewModelScope.launch {
                val s = repository.getSeries(seriesId)
                original = s
                if (s != null) {
                    val counts = repository.getEpisodeCounts(seriesId)
                    form = EditSeriesForm(
                        title = s.title,
                        status = s.status,
                        watcher = s.watcher,
                        genre = s.genre.orEmpty(),
                        originalTitle = s.originalTitle.orEmpty(),
                        label = s.label.orEmpty(),
                        rating = s.rating,
                        notes = s.notes.orEmpty(),
                        seasons = counts.map { it.toString() }.ifEmpty { listOf("") },
                    )
                }
                isLoading = false
            }
        }
    }

    fun update(transform: (EditSeriesForm) -> EditSeriesForm) { form = transform(form) }

    fun onTmdbQueryChange(text: String) { tmdbQuery = text }

    fun searchTmdb() {
        val q = tmdbQuery.trim().ifEmpty { form.title.trim() }
        if (q.isEmpty()) return
        viewModelScope.launch {
            error = null
            tmdbResults = try {
                syncRepository.search(q)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = "TMDB-Suche fehlgeschlagen: ${e.message ?: e::class.simpleName}"
                emptyList()
            }
        }
    }

    fun pickTmdb(result: TmdbSearchResult?) {
        tmdbResults = emptyList()
        update { it.copy(tmdbPick = result, title = if (result != null && it.title.isBlank()) result.name else it.title) }
    }

    fun addSeason() = update { it.copy(seasons = it.seasons + "") }

    fun removeLastSeason() = update { it.copy(seasons = it.seasons.dropLast(1)) }

    fun setSeasonEpisodes(index: Int, text: String) = update {
        val digits = text.filter(Char::isDigit).take(3)
        it.copy(seasons = it.seasons.toMutableList().also { list -> list[index] = digits })
    }

    /** Existing series that look like the new one (with their progress text); non-null shows the dialog. */
    var duplicates by mutableStateOf<List<Pair<SeriesEntity, String>>?>(null)
        private set

    fun dismissDuplicates() { duplicates = null }

    /**
     * Saves. A new series is first checked against existing ones (same TMDB show or title);
     * if there are any, the user is asked ([duplicates]) and [createAnyway] continues.
     */
    fun save(onSaved: (Long) -> Unit) {
        val f = form
        if (!f.isValid || isSaving) return
        if (!isNew) return doSave(onSaved)
        viewModelScope.launch {
            val found = repository.findDuplicates(f.tmdbPick?.id, f.title.trim(), f.originalTitle.trim().ifEmpty { null })
            if (found.isEmpty()) doSave(onSaved) else duplicates = found.map { it to repository.progressText(it.id) }
        }
    }

    /** New entry although the show exists: copies seasons and info of a matched duplicate, no progress. */
    fun createAnyway(onSaved: (Long) -> Unit) {
        val f = form
        val source = duplicates?.map { it.first }?.firstOrNull {
            it.tmdbId != null && (f.tmdbPick == null || f.tmdbPick.id == it.tmdbId)
        }
        duplicates = null
        if (source == null) return doSave(onSaved)
        viewModelScope.launch {
            isSaving = true
            val id = repository.duplicateSeries(source.id, f.watcher, f.label, f.status)
            isSaving = false
            onSaved(id)
        }
    }

    private fun doSave(onSaved: (Long) -> Unit) {
        val f = form
        val base = original ?: SeriesEntity(title = f.title)
        val entity = base.copy(
            title = f.title.trim(),
            label = f.label.trim().ifEmpty { null },
            status = f.status,
            watcher = f.watcher,
            genre = f.genre.trim().ifEmpty { null },
            originalTitle = f.originalTitle.trim().ifEmpty { null },
            rating = f.rating,
            notes = f.notes.trim().ifEmpty { null },
        )
        // Trailing empty season fields are ignored; empty fields in between count as 0 episodes.
        val counts = f.seasons.map { it.toIntOrNull() ?: 0 }.dropLastWhile { it == 0 }
        viewModelScope.launch {
            isSaving = true
            val id = repository.save(entity, if (f.tmdbPick != null) emptyList() else counts)
            f.tmdbPick?.let { pick ->
                try {
                    syncRepository.link(id, pick.id)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Saved anyway; the series can be matched later from its detail screen.
                }
            }
            isSaving = false
            onSaved(id)
        }
    }
}
