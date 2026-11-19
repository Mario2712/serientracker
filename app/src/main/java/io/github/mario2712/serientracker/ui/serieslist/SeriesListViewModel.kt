package io.github.mario2712.serientracker.ui.serieslist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.mario2712.serientracker.data.local.SeriesOverview
import io.github.mario2712.serientracker.data.repository.SeriesRepository
import io.github.mario2712.serientracker.domain.WatchStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SeriesListUiState(
    val items: List<SeriesOverview> = emptyList(),
    val filter: WatchStatus? = null,
    val query: String = "",
    val counts: Map<WatchStatus, Int> = emptyMap(),
    val isLoading: Boolean = true,
)

class SeriesListViewModel(private val repository: SeriesRepository) : ViewModel() {

    private val filter = MutableStateFlow<WatchStatus?>(WatchStatus.WATCHING)
    private val query = MutableStateFlow("")

    val uiState: StateFlow<SeriesListUiState> =
        combine(repository.observeOverview(), filter, query) { all, f, q ->
            SeriesListUiState(
                items = all
                    .filter { f == null || it.series.status == f }
                    .filter { q.isBlank() || it.series.title.contains(q.trim(), ignoreCase = true) },
                filter = f,
                query = q,
                counts = all.groupingBy { it.series.status }.eachCount(),
                isLoading = false,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SeriesListUiState())

    fun setFilter(status: WatchStatus?) { filter.value = status }

    fun setQuery(text: String) { query.value = text }

    fun watchNext(seriesId: Long) {
        viewModelScope.launch { repository.watchNextEpisode(seriesId) }
    }
}
