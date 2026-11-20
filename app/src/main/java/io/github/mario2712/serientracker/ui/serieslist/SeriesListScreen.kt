package io.github.mario2712.serientracker.ui.serieslist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.mario2712.serientracker.data.local.SeriesOverview
import io.github.mario2712.serientracker.data.local.displayTitle
import io.github.mario2712.serientracker.domain.Progress
import io.github.mario2712.serientracker.domain.WatchStatus
import io.github.mario2712.serientracker.domain.WatcherFilter
import io.github.mario2712.serientracker.ui.AppViewModelProvider
import io.github.mario2712.serientracker.ui.components.CatchUpBanner
import io.github.mario2712.serientracker.ui.components.Poster
import io.github.mario2712.serientracker.ui.components.StatusChip

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeriesListScreen(
    onSeriesClick: (Long) -> Unit,
    onAddClick: () -> Unit,
    onSyncClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onAboutClick: () -> Unit,
    onStatsClick: () -> Unit,
    onUpcomingClick: () -> Unit,
    viewModel: SeriesListViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val catchUp by viewModel.catchUpMode.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Serientracker") },
                actions = {
                    IconButton(onClick = onUpcomingClick) {
                        Icon(Icons.Filled.DateRange, contentDescription = "Demnächst")
                    }
                    var menuOpen by remember { mutableStateOf(false) }
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "Menü")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Demnächst") },
                            leadingIcon = { Icon(Icons.Filled.DateRange, contentDescription = null) },
                            onClick = { menuOpen = false; onUpcomingClick() },
                        )
                        DropdownMenuItem(
                            text = { Text("TMDB-Abgleich") },
                            leadingIcon = { Icon(Icons.Filled.Refresh, contentDescription = null) },
                            onClick = { menuOpen = false; onSyncClick() },
                        )
                        DropdownMenuItem(
                            text = { Text("Nachtragen-Modus") },
                            leadingIcon = { if (catchUp) Icon(Icons.Filled.Check, contentDescription = "aktiv") },
                            onClick = { menuOpen = false; viewModel.setCatchUpMode(!catchUp) },
                        )
                        DropdownMenuItem(
                            text = { Text("Statistik") },
                            onClick = { menuOpen = false; onStatsClick() },
                        )
                        DropdownMenuItem(
                            text = { Text("Einstellungen") },
                            onClick = { menuOpen = false; onSettingsClick() },
                        )
                        DropdownMenuItem(
                            text = { Text("Über") },
                            onClick = { menuOpen = false; onAboutClick() },
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddClick) {
                Icon(Icons.Filled.Add, contentDescription = "Serie hinzufügen")
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::setQuery,
                placeholder = { Text("Suchen") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setQuery("") }) {
                            Icon(Icons.Filled.Clear, contentDescription = "Suche leeren")
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            if (catchUp) {
                CatchUpBanner(onStop = { viewModel.setCatchUpMode(false) }, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            }
            if (state.unsyncedCount > 0) {
                SyncBanner(count = state.unsyncedCount, onClick = onSyncClick)
            }
            StatusFilterRow(
                selected = state.filter,
                counts = state.counts,
                onSelect = viewModel::setFilter,
            )
            Spacer(Modifier.height(4.dp))
            WatcherFilterRow(selected = state.watcherFilter, onSelect = viewModel::setWatcherFilter)
            SortAndGenreRow(
                sort = state.sort,
                genre = state.genre,
                genres = state.genres,
                onSort = viewModel::setSort,
                onGenre = viewModel::setGenre,
            )
            if (!state.isLoading && state.items.isEmpty()) {
                EmptyState(hasFilter = state.filter != null || state.query.isNotBlank() || state.genre != null)
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.items, key = { it.series.id }) { item ->
                        SeriesRow(
                            item = item,
                            onClick = { onSeriesClick(item.series.id) },
                            onWatchNext = { viewModel.watchNext(item.series.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusFilterRow(
    selected: WatchStatus?,
    counts: Map<WatchStatus, Int>,
    onSelect: (WatchStatus?) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            FilterChip(
                selected = selected == null,
                onClick = { onSelect(null) },
                label = { Text("Alle (${counts.values.sum()})") },
            )
        }
        items(WatchStatus.entries) { status ->
            FilterChip(
                selected = selected == status,
                onClick = { onSelect(status) },
                label = { Text("${status.label} (${counts[status] ?: 0})") },
            )
        }
    }
}

@Composable
private fun SortAndGenreRow(
    sort: SeriesSort,
    genre: String?,
    genres: List<String>,
    onSort: (SeriesSort) -> Unit,
    onGenre: (String?) -> Unit,
) {
    var sortOpen by remember { mutableStateOf(false) }
    var genreOpen by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box {
            AssistChip(
                onClick = { sortOpen = true },
                label = { Text("Sortierung: ${sort.label}") },
                trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
            )
            DropdownMenu(expanded = sortOpen, onDismissRequest = { sortOpen = false }) {
                SeriesSort.entries.forEach { s ->
                    DropdownMenuItem(
                        text = { Text(s.label) },
                        leadingIcon = { if (s == sort) Icon(Icons.Filled.Check, contentDescription = null) },
                        onClick = { sortOpen = false; onSort(s) },
                    )
                }
            }
        }
        Box {
            AssistChip(
                onClick = { genreOpen = true },
                label = { Text("Genre: ${genre ?: "Alle"}", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
            )
            DropdownMenu(expanded = genreOpen, onDismissRequest = { genreOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Alle") },
                    leadingIcon = { if (genre == null) Icon(Icons.Filled.Check, contentDescription = null) },
                    onClick = { genreOpen = false; onGenre(null) },
                )
                genres.forEach { g ->
                    DropdownMenuItem(
                        text = { Text(g) },
                        leadingIcon = { if (g == genre) Icon(Icons.Filled.Check, contentDescription = null) },
                        onClick = { genreOpen = false; onGenre(g) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SyncBanner(count: Int, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(Modifier.padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "$count Serien noch ohne exakte Folgen aus TMDB",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onClick) { Text("Abgleichen") }
        }
    }
}

@Composable
private fun WatcherFilterRow(selected: WatcherFilter?, onSelect: (WatcherFilter?) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            FilterChip(selected = selected == null, onClick = { onSelect(null) }, label = { Text("Alle") })
        }
        items(WatcherFilter.entries) { w ->
            FilterChip(selected = selected == w, onClick = { onSelect(w) }, label = { Text(w.label) })
        }
    }
}

@Composable
private fun SeriesRow(item: SeriesOverview, onClick: () -> Unit, onWatchNext: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            item.series.posterUrl?.let { url ->
                Poster(url = url, modifier = Modifier.width(44.dp).height(66.dp))
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    item.series.displayTitle,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                item.series.originalTitle?.takeIf { !it.equals(item.series.title, ignoreCase = true) }?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusChip(item.series.status)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = progressText(item),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (item.totalEpisodes > 0) {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { Progress.fraction(item.watchedEpisodes, item.totalEpisodes) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            if (item.nextSeason != null && item.nextEpisode != null) {
                Spacer(Modifier.width(12.dp))
                FilledTonalIconButton(onClick = onWatchNext) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = "${Progress.episodeCode(item.nextSeason, item.nextEpisode)} gesehen",
                    )
                }
            }
        }
    }
}

private fun progressText(item: SeriesOverview): String {
    val base = if (item.totalEpisodes > 0) "${item.watchedEpisodes}/${item.totalEpisodes} Folgen" else "keine Folgen"
    return when {
        item.nextSeason != null && item.nextEpisode != null ->
            "$base · als Nächstes ${Progress.episodeCode(item.nextSeason, item.nextEpisode)}"
        item.needsSync && item.excelSeason != null && item.excelEpisode != null ->
            "$base · zuletzt ${Progress.episodeCode(item.excelSeason, item.excelEpisode)}"
        else -> base
    }
}

@Composable
private fun EmptyState(hasFilter: Boolean) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(
            if (hasFilter) "Keine Serien für diesen Filter." else "Noch keine Serien. Tippe auf +, um eine hinzuzufügen.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
