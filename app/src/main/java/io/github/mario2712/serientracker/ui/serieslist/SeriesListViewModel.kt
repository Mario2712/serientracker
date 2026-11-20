package io.github.mario2712.serientracker.ui.serieslist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.mario2712.serientracker.data.local.SeriesOverview
import io.github.mario2712.serientracker.data.repository.SeriesRepository
import io.github.mario2712.serientracker.data.settings.SettingsRepository
import io.github.mario2712.serientracker.domain.WatchStatus
import io.github.mario2712.serientracker.domain.WatcherFilter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SeriesListUiState(
    val items: List<SeriesOverview> = emptyList(),
    val filter: WatchStatus? = null,
    val watcherFilter: WatcherFilter? = null,
    val query: String = "",
    val sort: SeriesSort = SeriesSort.RECENT,
    val genre: String? = null,
    val genres: List<String> = emptyList(),
    val unsyncedCount: Int = 0,
    val counts: Map<WatchStatus, Int> = emptyMap(),
    val isLoading: Boolean = true,
)

private data class ListFilters(
    val status: WatchStatus? = WatchStatus.WATCHING,
    val watcher: WatcherFilter? = null,
    val query: String = "",
)

class SeriesListViewModel(
    private val repository: SeriesRepository,
    private val settings: SettingsRepository,
) : ViewModel() {

    private val filters = MutableStateFlow(ListFilters())

    val uiState: StateFlow<SeriesListUiState> =
        combine(
            repository.observeOverview(),
            filters,
            repository.observeUnsyncedCount(),
            settings.listPreferences,
        ) { all, f, unsynced, (sortName, genre) ->
            val sort = SeriesSort.from(sortName)
            // Status counts follow the other filters and the search, so the chips match the list.
            val visible = all
                .filter { f.watcher == null || f.watcher.accepts(it.series.watcher) }
                .filter { SeriesListLogic.matchesGenre(it, genre) }
                .filter {
                    f.query.isBlank() ||
                        it.series.title.contains(f.query.trim(), ignoreCase = true) ||
                        it.series.originalTitle?.contains(f.query.trim(), ignoreCase = true) == true
                }
            SeriesListUiState(
                items = SeriesListLogic.sort(visible.filter { f.status == null || it.series.status == f.status }, sort),
                filter = f.status,
                watcherFilter = f.watcher,
                query = f.query,
                sort = sort,
                genre = genre,
                genres = SeriesListLogic.genres(all),
                unsyncedCount = unsynced,
                counts = visible.groupingBy { it.series.status }.eachCount(),
                isLoading = false,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SeriesListUiState())

    fun setFilter(status: WatchStatus?) = filters.update { it.copy(status = status) }

    fun setWatcherFilter(value: WatcherFilter?) = filters.update { it.copy(watcher = value) }

    fun setQuery(text: String) = filters.update { it.copy(query = text) }

    fun setSort(sort: SeriesSort) {
        viewModelScope.launch { settings.setListSort(sort.name) }
    }

    fun setGenre(genre: String?) {
        viewModelScope.launch { settings.setListGenre(genre) }
    }

    fun watchNext(seriesId: Long) {
        viewModelScope.launch { repository.watchNextEpisode(seriesId) }
    }
}
