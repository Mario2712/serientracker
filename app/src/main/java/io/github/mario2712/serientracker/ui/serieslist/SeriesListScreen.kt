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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.mario2712.serientracker.data.local.SeriesOverview
import io.github.mario2712.serientracker.domain.Progress
import io.github.mario2712.serientracker.domain.WatchStatus
import io.github.mario2712.serientracker.ui.AppViewModelProvider
import io.github.mario2712.serientracker.ui.components.StatusChip

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeriesListScreen(
    onSeriesClick: (Long) -> Unit,
    onAddClick: () -> Unit,
    viewModel: SeriesListViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Serientracker") }) },
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
            StatusFilterRow(
                selected = state.filter,
                counts = state.counts,
                onSelect = viewModel::setFilter,
            )
            if (!state.isLoading && state.items.isEmpty()) {
                EmptyState(hasFilter = state.filter != null || state.query.isNotBlank())
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
private fun SeriesRow(item: SeriesOverview, onClick: () -> Unit, onWatchNext: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    item.series.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
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
    val base = "${item.watchedEpisodes}/${item.totalEpisodes} Folgen"
    return if (item.nextSeason != null && item.nextEpisode != null) {
        "$base · als Nächstes ${Progress.episodeCode(item.nextSeason, item.nextEpisode)}"
    } else {
        base
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
