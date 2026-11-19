package io.github.mario2712.serientracker.ui.seriesdetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.mario2712.serientracker.data.local.SeriesWithSeasons
import io.github.mario2712.serientracker.data.repository.SeriesRepository
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
) : ViewModel() {

    val seriesId: Long = checkNotNull(savedStateHandle[Routes.ARG_SERIES_ID])

    val uiState: StateFlow<SeriesDetailUiState> = repository.observeSeries(seriesId)
        .map { if (it == null) SeriesDetailUiState.NotFound else SeriesDetailUiState.Loaded(sorted(it)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SeriesDetailUiState.Loading)

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
        seasons = data.seasons
            .sortedBy { it.season.number }
            .map { s -> s.copy(episodes = s.episodes.sortedBy { it.number }) },
    )
}
