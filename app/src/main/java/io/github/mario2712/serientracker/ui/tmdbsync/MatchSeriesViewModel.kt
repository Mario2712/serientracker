package io.github.mario2712.serientracker.ui.tmdbsync

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.mario2712.serientracker.data.remote.TmdbKeyMissingException
import io.github.mario2712.serientracker.data.remote.TmdbSearchResult
import io.github.mario2712.serientracker.data.repository.SeriesRepository
import io.github.mario2712.serientracker.data.repository.TmdbSyncCoordinator
import io.github.mario2712.serientracker.data.repository.TmdbSyncRepository
import io.github.mario2712.serientracker.ui.navigation.Routes
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/** Lets the user pick the right TMDB show for one series. */
class MatchSeriesViewModel(
    savedStateHandle: SavedStateHandle,
    private val seriesRepository: SeriesRepository,
    private val syncRepository: TmdbSyncRepository,
    private val coordinator: TmdbSyncCoordinator,
) : ViewModel() {

    private val seriesId: Long = checkNotNull(savedStateHandle[Routes.ARG_SERIES_ID])

    var title by mutableStateOf("")
        private set
    var query by mutableStateOf("")
        private set
    var results by mutableStateOf<List<TmdbSearchResult>>(emptyList())
        private set
    var busy by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    init {
        viewModelScope.launch {
            title = seriesRepository.getSeries(seriesId)?.title.orEmpty()
            query = title
            search()
        }
    }

    fun onQueryChange(text: String) { query = text }

    fun search() {
        if (query.isBlank()) return
        viewModelScope.launch {
            guarded { results = syncRepository.search(query.trim()) }
        }
    }

    fun pick(result: TmdbSearchResult, onDone: () -> Unit) {
        viewModelScope.launch {
            guarded {
                val report = syncRepository.link(seriesId, result.id)
                coordinator.resolved(seriesId, report)
                onDone()
            }
        }
    }

    /** Keep the series without TMDB data (e.g. not listed there). */
    fun skip(onDone: () -> Unit) {
        coordinator.resolved(seriesId, null)
        onDone()
    }

    private suspend fun guarded(block: suspend () -> Unit) {
        busy = true
        error = null
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: TmdbKeyMissingException) {
            error = "Bitte zuerst in den Einstellungen einen TMDB-API-Key eintragen."
        } catch (e: Exception) {
            error = "Fehler: ${e.message ?: e::class.simpleName}"
        } finally {
            busy = false
        }
    }
}
