package io.github.mario2712.serientracker.ui.seriesdetail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.mario2712.serientracker.data.local.SeasonWithEpisodes
import io.github.mario2712.serientracker.data.local.SeriesWithSeasons
import io.github.mario2712.serientracker.data.local.isWatched
import io.github.mario2712.serientracker.domain.Progress
import io.github.mario2712.serientracker.ui.AppViewModelProvider
import io.github.mario2712.serientracker.ui.components.StatusChip

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeriesDetailScreen(
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    viewModel: SeriesDetailViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }
    val title = (state as? SeriesDetailUiState.Loaded)?.data?.series?.title.orEmpty()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
                actions = {
                    if (state is SeriesDetailUiState.Loaded) {
                        IconButton(onClick = { onEdit(viewModel.seriesId) }) {
                            Icon(Icons.Filled.Edit, contentDescription = "Bearbeiten")
                        }
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Löschen")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (val s = state) {
                SeriesDetailUiState.Loading ->
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                SeriesDetailUiState.NotFound ->
                    Text("Serie nicht gefunden.", Modifier.align(Alignment.Center))
                is SeriesDetailUiState.Loaded -> DetailContent(
                    data = s.data,
                    onWatchNext = viewModel::watchNext,
                    onToggleEpisode = viewModel::toggleEpisode,
                    onToggleSeason = viewModel::toggleSeason,
                )
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Serie löschen?") },
            text = { Text("„$title“ und der gesamte Fortschritt werden gelöscht.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.delete(onDeleted = onBack)
                }) { Text("Löschen") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Abbrechen") } },
        )
    }
}

@Composable
private fun DetailContent(
    data: SeriesWithSeasons,
    onWatchNext: () -> Unit,
    onToggleEpisode: (Long, Boolean) -> Unit,
    onToggleSeason: (Long, Boolean) -> Unit,
) {
    val all = data.seasons.flatMap { s -> s.episodes.map { s.season.number to it } }
    val watched = all.count { it.second.isWatched }
    val next = all.firstOrNull { !it.second.isWatched }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusChip(data.series.status)
                    data.series.streamingService?.takeIf { it.isNotBlank() }?.let {
                        Spacer(Modifier.width(8.dp))
                        Text(it, style = MaterialTheme.typography.bodyMedium)
                    }
                    data.series.rating?.let {
                        Spacer(Modifier.width(8.dp))
                        Text("★ $it/10", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("$watched von ${all.size} Folgen gesehen", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { Progress.fraction(watched, all.size) },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (next != null) {
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = onWatchNext, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.Check, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("${Progress.episodeCode(next.first, next.second.number)} gesehen")
                    }
                }
                data.series.notes?.takeIf { it.isNotBlank() }?.let {
                    Spacer(Modifier.height(12.dp))
                    Text(it, style = MaterialTheme.typography.bodyMedium)
                }
                if (data.seasons.isEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Noch keine Staffeln angelegt. Tippe auf Bearbeiten, um Staffeln und Folgen hinzuzufügen.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        items(data.seasons, key = { it.season.id }) { season ->
            SeasonCard(
                season = season,
                // Open the season that holds the next episode by default.
                initiallyExpanded = season.season.number == next?.first,
                onToggleEpisode = onToggleEpisode,
                onToggleSeason = onToggleSeason,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SeasonCard(
    season: SeasonWithEpisodes,
    initiallyExpanded: Boolean,
    onToggleEpisode: (Long, Boolean) -> Unit,
    onToggleSeason: (Long, Boolean) -> Unit,
) {
    var expanded by rememberSaveable(season.season.id) { mutableStateOf(initiallyExpanded) }
    val watched = season.episodes.count { it.isWatched }
    val total = season.episodes.size
    val allWatched = total > 0 && watched == total

    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f).padding(vertical = 12.dp)) {
                Text(
                    season.season.title ?: "Staffel ${season.season.number}",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text("$watched/$total", style = MaterialTheme.typography.bodySmall)
            }
            Checkbox(
                checked = allWatched,
                onCheckedChange = { onToggleSeason(season.season.id, it) },
                enabled = total > 0,
            )
            Icon(
                if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = if (expanded) "Zuklappen" else "Aufklappen",
                modifier = Modifier.padding(8.dp),
            )
        }
        if (expanded) {
            FlowRow(
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                season.episodes.forEach { ep ->
                    FilterChip(
                        selected = ep.isWatched,
                        onClick = { onToggleEpisode(ep.id, !ep.isWatched) },
                        label = { Text(ep.number.toString()) },
                    )
                }
            }
        }
    }
}
