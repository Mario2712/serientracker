package io.github.mario2712.serientracker.ui.seriesdetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.mario2712.serientracker.data.local.SeriesWithSeasons
import io.github.mario2712.serientracker.data.repository.SeriesRepository
import io.github.mario2712.serientracker.data.repository.TmdbSyncRepository
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.coroutines.cancellation.CancellationException
import io.github.mario2712.serientracker.ui.navigation.Routes
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface SeriesDetailUiState {
    data object Loading : SeriesDetailUiState
    data object NotFound : SeriesDetailUiState
    data class Loaded(val data: SeriesWithSeasons) : SeriesDetailUiState
}

class SeriesDetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: SeriesRepository,
    private val syncRepository: TmdbSyncRepository,
) : ViewModel() {

    /** Feedback for "Von TMDB aktualisieren". */
    var message by mutableStateOf<String?>(null)
        private set
    var refreshing by mutableStateOf(false)
        private set

    fun refreshFromTmdb() {
        if (refreshing) return
        viewModelScope.launch {
            refreshing = true
            message = try {
                val before = (uiState.value as? SeriesDetailUiState.Loaded)?.data?.seasons?.sumOf { it.episodes.size } ?: 0
                val report = syncRepository.refresh(seriesId)
                when {
                    report == null -> "Serie ist nicht mit TMDB verknüpft."
                    report.total > before -> "${report.total - before} neue Folgen."
                    else -> "Keine neuen Folgen."
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                "Fehler: ${e.message ?: e::class.simpleName}"
            } finally {
                refreshing = false
            }
        }
    }

    fun clearMessage() { message = null }

    val seriesId: Long = checkNotNull(savedStateHandle[Routes.ARG_SERIES_ID])

    val uiState: StateFlow<SeriesDetailUiState> = repository.observeSeries(seriesId)
        .map { if (it == null) SeriesDetailUiState.NotFound else SeriesDetailUiState.Loaded(sorted(it)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SeriesDetailUiState.Loading)

    fun setWatchedAt(episodeId: Long, watchedAt: Long) {
        viewModelScope.launch { repository.setEpisodeWatchedAt(seriesId, episodeId, watchedAt) }
    }

    fun toggleEpisode(episodeId: Long, watched: Boolean) {
        viewModelScope.launch { repository.setEpisodeWatched(seriesId, episodeId, watched) }
    }

    fun toggleSeason(seasonId: Long, watched: Boolean) {
        viewModelScope.launch { repository.setSeasonWatched(seriesId, seasonId, watched) }
    }

    fun watchNext() {
        viewModelScope.launch { repository.watchNextEpisode(seriesId) }
    }

    fun delete(onDeleted: () -> Unit) {
        viewModelScope.launch {
            repository.delete(seriesId)
            onDeleted()
        }
    }

    private fun sorted(data: SeriesWithSeasons) = data.copy(
        // Regular seasons first, specials (season 0) at the end.
        seasons = data.seasons
            .sortedBy { if (it.season.number == 0) Int.MAX_VALUE else it.season.number }
            .map { s -> s.copy(episodes = s.episodes.sortedBy { it.number }) },
    )
}
