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
import androidx.compose.material.icons.filled.Refresh
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
import io.github.mario2712.serientracker.data.local.ImportProgressEntity
import io.github.mario2712.serientracker.data.local.SeasonWithEpisodes
import io.github.mario2712.serientracker.data.local.SeriesWithSeasons
import io.github.mario2712.serientracker.data.local.isWatched
import io.github.mario2712.serientracker.domain.Progress
import io.github.mario2712.serientracker.ui.AppViewModelProvider
import io.github.mario2712.serientracker.ui.components.Poster
import io.github.mario2712.serientracker.ui.components.StatusChip

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeriesDetailScreen(
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onMatch: (Long) -> Unit,
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
                    val loaded = state as? SeriesDetailUiState.Loaded
                    if (loaded?.data?.series?.tmdbId != null) {
                        IconButton(onClick = viewModel::refreshFromTmdb, enabled = !viewModel.refreshing) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Von TMDB aktualisieren")
                        }
                    }
                    if (loaded != null) {
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
                    message = viewModel.message,
                    onMatch = { onMatch(viewModel.seriesId) },
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
    message: String?,
    onMatch: () -> Unit,
    onWatchNext: () -> Unit,
    onToggleEpisode: (Long, Boolean) -> Unit,
    onToggleSeason: (Long, Boolean) -> Unit,
) {
    val all = data.seasons.flatMap { s -> s.episodes.map { s.season.number to it } }
    val watched = all.count { it.second.isWatched }
    // "Next" follows the regular seasons; specials are ticked by hand.
    val next = all.firstOrNull { it.first > 0 && !it.second.isWatched }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Header(data = data, watched = watched, total = all.size, message = message)
        }
        data.importProgress?.let { ip ->
            item { ExcelCard(ip, onMatch) }
        }
        if (next != null) {
            item {
                Button(onClick = onWatchNext, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Check, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("${Progress.episodeCode(next.first, next.second.number)} gesehen")
                }
            }
        }
        if (data.seasons.isEmpty() && data.importProgress == null) {
            item {
                Text(
                    "Noch keine Staffeln angelegt. Tippe auf Bearbeiten, um Staffeln und Folgen hinzuzufügen.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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

@Composable
private fun Header(data: SeriesWithSeasons, watched: Int, total: Int, message: String?) {
    val series = data.series
    Column {
        Row {
            series.posterUrl?.let {
                Poster(it, Modifier.width(96.dp).height(144.dp))
                Spacer(Modifier.width(16.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                StatusChip(series.status)
                val facts = listOfNotNull(
                    series.watcher?.let { "Wer: ${it.label}" },
                    series.genre,
                    series.streamingService?.takeIf { it.isNotBlank() },
                    series.rating?.let { "★ $it/10" },
                )
                if (facts.isNotEmpty()) Text(facts.joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
                series.nextAirDate?.let {
                    Text("Nächste Ausstrahlung: ${formatDate(it)}", style = MaterialTheme.typography.bodyMedium)
                }
                if (series.isEnded == true) Text("Serie beendet", style = MaterialTheme.typography.bodySmall)
            }
        }
        Spacer(Modifier.height(12.dp))
        if (total > 0) {
            Text("$watched von $total Folgen gesehen", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(progress = { Progress.fraction(watched, total) }, modifier = Modifier.fillMaxWidth())
        }
        message?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }
        series.notes?.takeIf { it.isNotBlank() }?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ExcelCard(ip: ImportProgressEntity, onMatch: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Stand aus der Excel", style = MaterialTheme.typography.titleMedium)
            val lastSeason = ip.lastSeason
            val lastEpisode = ip.lastEpisode
            val last = if (lastSeason != null && lastSeason > 0 && lastEpisode != null) {
                Progress.episodeCode(lastSeason, lastEpisode) +
                    (ip.lastSpecial?.takeIf { it > 0 }?.let { " + Special $it" } ?: "")
            } else {
                "noch nichts"
            }
            Text("Zuletzt gesehen: $last")
            if (ip.maxSeason != null) {
                Text(
                    "Verfügbar: ${ip.maxSeason} Staffeln, letzte mit ${ip.maxEpisode ?: "?"} Folgen" +
                        (ip.maxSpecial?.takeIf { it > 0 }?.let { ", $it Specials" } ?: ""),
                )
            }
            if (ip.total != null) Text("Gesehen: ${ip.watched ?: 0} von ${ip.total}")
            Text(
                "Die genaue Folgenliste kommt von TMDB.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onMatch) { Text("Mit TMDB abgleichen") }
        }
    }
}

private fun formatDate(epochDay: Long): String =
    java.time.LocalDate.ofEpochDay(epochDay).format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy"))

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
                    season.season.title ?: if (season.season.number == 0) "Specials" else "Staffel ${season.season.number}",
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
