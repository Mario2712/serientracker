package io.github.mario2712.serientracker.ui.serieslist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.mario2712.serientracker.data.local.SeriesOverview
import io.github.mario2712.serientracker.data.repository.SeriesRepository
import io.github.mario2712.serientracker.domain.WatchStatus
import io.github.mario2712.serientracker.domain.WatcherFilter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SeriesListUiState(
    val items: List<SeriesOverview> = emptyList(),
    val filter: WatchStatus? = null,
    val watcherFilter: WatcherFilter? = null,
    val query: String = "",
    val unsyncedCount: Int = 0,
    val counts: Map<WatchStatus, Int> = emptyMap(),
    val isLoading: Boolean = true,
)

class SeriesListViewModel(private val repository: SeriesRepository) : ViewModel() {

    private val filter = MutableStateFlow<WatchStatus?>(WatchStatus.WATCHING)
    private val watcherFilter = MutableStateFlow<WatcherFilter?>(null)
    private val query = MutableStateFlow("")

    val uiState: StateFlow<SeriesListUiState> =
        combine(
            repository.observeOverview(),
            filter,
            watcherFilter,
            query,
            repository.observeUnsyncedCount(),
        ) { all, f, w, q, unsynced ->
            // Status counts follow the "Wer" filter and the search, so the chips match the list.
            val visible = all
                .filter { w == null || w.accepts(it.series.watcher) }
                .filter {
                    q.isBlank() ||
                        it.series.title.contains(q.trim(), ignoreCase = true) ||
                        it.series.originalTitle?.contains(q.trim(), ignoreCase = true) == true
                }
            SeriesListUiState(
                items = visible.filter { f == null || it.series.status == f },
                filter = f,
                watcherFilter = w,
                query = q,
                unsyncedCount = unsynced,
                counts = visible.groupingBy { it.series.status }.eachCount(),
                isLoading = false,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SeriesListUiState())

    fun setFilter(status: WatchStatus?) { filter.value = status }

    fun setWatcherFilter(value: WatcherFilter?) { watcherFilter.value = value }

    fun setQuery(text: String) { query.value = text }

    fun watchNext(seriesId: Long) {
        viewModelScope.launch { repository.watchNextEpisode(seriesId) }
    }
}
