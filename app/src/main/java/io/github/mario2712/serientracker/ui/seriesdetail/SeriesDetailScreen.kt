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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import io.github.mario2712.serientracker.data.local.EpisodeEntity
import io.github.mario2712.serientracker.data.local.ImportProgressEntity
import io.github.mario2712.serientracker.data.local.SeasonWithEpisodes
import io.github.mario2712.serientracker.data.local.SeriesWithSeasons
import io.github.mario2712.serientracker.data.local.displayTitle
import io.github.mario2712.serientracker.data.local.counts
import io.github.mario2712.serientracker.data.local.isWatched
import io.github.mario2712.serientracker.domain.EpisodeKind
import io.github.mario2712.serientracker.domain.Progress
import io.github.mario2712.serientracker.ui.AppViewModelProvider
import io.github.mario2712.serientracker.ui.components.CatchUpBanner
import io.github.mario2712.serientracker.ui.components.Poster
import io.github.mario2712.serientracker.ui.components.StatusChip

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeriesDetailScreen(
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onMatch: (Long) -> Unit,
    onOpenSeries: (Long) -> Unit = {},
    viewModel: SeriesDetailViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }
    var selectedEpisodeId by rememberSaveable { mutableStateOf<Long?>(null) }
    var addSpecial by rememberSaveable { mutableStateOf(false) }
    val showExcel by viewModel.showExcel.collectAsStateWithLifecycle()
    val catchUp by viewModel.catchUpMode.collectAsStateWithLifecycle()
    val title = (state as? SeriesDetailUiState.Loaded)?.data?.series?.displayTitle.orEmpty()
    var newRun by rememberSaveable { mutableStateOf(false) }
    val rewatchCounts by viewModel.rewatchCounts.collectAsStateWithLifecycle()

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
                        var menuOpen by remember { mutableStateOf(false) }
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "Mehr")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Als neuen Durchgang anlegen") },
                                onClick = { menuOpen = false; newRun = true },
                            )
                            DropdownMenuItem(
                                text = { Text("Löschen") },
                                leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                                onClick = { menuOpen = false; confirmDelete = true },
                            )
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
                    showExcel = showExcel,
                    catchUp = catchUp,
                    onStopCatchUp = viewModel::stopCatchUp,
                    onMatch = { onMatch(viewModel.seriesId) },
                    onWatchNext = viewModel::watchNext,
                    onToggleEpisode = viewModel::toggleEpisode,
                    onToggleSeason = viewModel::toggleSeason,
                    onCountAndWatch = viewModel::countAndWatch,
                    onEpisodeDetails = { selectedEpisodeId = it },
                    onAddSpecial = { addSpecial = true },
                    rewatchCounts = rewatchCounts,
                    onWatchSeasonAgain = viewModel::watchSeasonAgain,
                )
            }
        }
    }

    if (newRun) {
        val series = (state as? SeriesDetailUiState.Loaded)?.data?.series
        DuplicateRunDialog(
            title = series?.title.orEmpty(),
            initialWatcher = series?.watcher,
            onDismiss = { newRun = false },
            onCreate = { watcher, label ->
                newRun = false
                viewModel.duplicate(watcher, label) { onOpenSeries(it) }
            },
        )
    }

    if (addSpecial) {
        AddSpecialDialog(
            onDismiss = { addSpecial = false },
            onAdd = { name, airDate, watched ->
                addSpecial = false
                viewModel.addCustomSpecial(name, airDate, watched)
            },
        )
    }

    // Look the episode up in the live data, so the sheet updates after toggling.
    val selectedSeason = selectedEpisodeId?.let { id ->
        (state as? SeriesDetailUiState.Loaded)?.data?.seasons?.firstOrNull { season -> season.episodes.any { it.id == id } }
    }
    val selectedEpisode = selectedSeason?.episodes?.firstOrNull { it.id == selectedEpisodeId }
    if (selectedSeason != null && selectedEpisode != null) {
        val episode = selectedEpisode
        EpisodeDetailSheet(
            seasonNumber = selectedSeason.season.number,
            episode = episode,
            // Specials are numbered 1, 2, 3 … by air date among the counting ones.
            specialNumber = if (selectedSeason.season.number == 0) {
                selectedSeason.episodes.filter { it.counts }.indexOfFirst { it.id == episode.id }.takeIf { it >= 0 }?.plus(1)
            } else {
                null
            },
            views = viewModel.views(episode.id).collectAsStateWithLifecycle(emptyList()).value,
            onDismiss = { selectedEpisodeId = null },
            onToggle = { viewModel.toggleEpisode(episode.id, it) },
            onWatchAgain = { viewModel.watchAgain(episode.id) },
            onDeleteViewing = { viewModel.deleteViewing(episode.id, it) },
            onSetViewingTime = { viewId, at, zone -> viewModel.setViewingTime(episode.id, viewId, at, zone) },
            onRate = { viewModel.setRating(episode.id, it) },
            onKind = { viewModel.setKind(episode.id, it) },
            onDelete = {
                selectedEpisodeId = null
                viewModel.deleteCustomEpisode(episode.id)
            },
        )
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
    showExcel: Boolean,
    catchUp: Boolean,
    onStopCatchUp: () -> Unit,
    onMatch: () -> Unit,
    onWatchNext: () -> Unit,
    onToggleEpisode: (Long, Boolean) -> Unit,
    onToggleSeason: (Long, Boolean) -> Unit,
    onCountAndWatch: (Long, Int) -> Unit,
    onEpisodeDetails: (Long) -> Unit,
    onAddSpecial: () -> Unit,
    rewatchCounts: Map<Long, Int> = emptyMap(),
    onWatchSeasonAgain: (Long) -> Unit = {},
) {
    // Hidden episodes do not count anywhere.
    val all = data.seasons.flatMap { s -> s.episodes.filter { it.counts }.map { s.season.number to it } }
    val watched = all.count { it.second.isWatched }
    // "Next" follows the regular episodes; specials are ticked by hand.
    val next = all.firstOrNull { it.second.kind == EpisodeKind.REGULAR && !it.second.isWatched }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (catchUp) {
            item { CatchUpBanner(onStop = onStopCatchUp) }
        }
        item {
            Header(data = data, watched = watched, total = all.size, message = message, showExcel = showExcel)
        }
        val s = data.series
        if (s.overview != null || s.tmdbRating != null || s.castSummary != null || s.trailerKey != null) {
            item { AboutCard(data) }
        }
        data.importProgress?.let { ip ->
            item { ExcelCard(ip, onMatch, showExcel) }
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
                // Tapping a hidden episode makes it count again and ticks it.
                onTapEpisode = { ep ->
                    if (ep.kind == EpisodeKind.HIDDEN) {
                        onCountAndWatch(ep.id, season.season.number)
                    } else {
                        onToggleEpisode(ep.id, !ep.isWatched)
                    }
                },
                onToggleSeason = onToggleSeason,
                onEpisodeDetails = onEpisodeDetails,
                rewatchCounts = rewatchCounts,
                onWatchSeasonAgain = { onWatchSeasonAgain(season.season.id) },
            )
        }
        if (data.importProgress == null && data.seasons.isNotEmpty()) {
            item {
                OutlinedButton(onClick = onAddSpecial) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Eigenes Special hinzufügen")
                }
            }
        }
        if (all.isNotEmpty()) {
            item {
                Text(
                    "Tippen = abhaken · lange drücken = Details, Bewertung, als Special zählen oder ausblenden",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Header(data: SeriesWithSeasons, watched: Int, total: Int, message: String?, showExcel: Boolean) {
    val series = data.series
    val rated = data.seasons.flatMap { it.episodes }.filter { it.counts }.mapNotNull { it.rating }
    Column {
        // Full title, wrapping; the top bar shows it in one line only.
        Text(series.title, style = MaterialTheme.typography.headlineSmall)
        series.label?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        }
        series.originalTitle?.takeIf { !it.equals(series.title, ignoreCase = true) }?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(12.dp))
        Row {
            series.posterUrl?.let {
                Poster(it, Modifier.width(96.dp).height(144.dp))
                Spacer(Modifier.width(16.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                StatusChip(series.status)
                val ratingText = when {
                    series.rating != null -> "★ ${series.rating}/10"
                    rated.isNotEmpty() -> "Ø %.1f aus %d bewerteten Folgen".format(rated.average(), rated.size)
                    else -> null
                }
                val facts = listOfNotNull(
                    series.watcher?.let { "Wer: ${it.label}" },
                    series.genre,
                    ratingText,
                )
                if (facts.isNotEmpty()) Text(facts.joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
                series.nextAirDate?.let {
                    val code = if (series.nextEpisodeSeason != null && series.nextEpisodeNumber != null) {
                        " · " + Progress.episodeCode(series.nextEpisodeSeason, series.nextEpisodeNumber)
                    } else {
                        ""
                    }
                    Text("Nächste Ausstrahlung: ${formatDate(it)}$code", style = MaterialTheme.typography.bodyMedium)
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
        // Differences to the Excel list (e.g. TMDB lists an extra special).
        val excelDiffers = showExcel && data.importProgress == null && total > 0 &&
            (series.excelTotal != null || series.excelWatched != null) &&
            (series.excelTotal != total || series.excelWatched != watched)
        if (excelDiffers) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Abweichung zur Excel: Excel ${series.excelWatched ?: "–"}/${series.excelTotal ?: "–"} · " +
                    "App $watched/$total. Lange auf eine Folge drücken, um sie als Special zu zählen oder auszublenden.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
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

/** TMDB info: description, score, cast, trailer. */
@Composable
private fun AboutCard(data: SeriesWithSeasons) {
    val s = data.series
    val uriHandler = LocalUriHandler.current
    var expanded by rememberSaveable(s.id) { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Über die Serie", style = MaterialTheme.typography.titleMedium)
            s.tmdbRating?.let { score ->
                val votes = s.tmdbVoteCount?.let { " (%,d Stimmen)".format(java.util.Locale.GERMANY, it) }.orEmpty()
                Text("TMDB ★ %.1f%s".format(java.util.Locale.GERMANY, score, votes), style = MaterialTheme.typography.bodyMedium)
            }
            s.overview?.let { text ->
                Text(
                    text,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = if (expanded) Int.MAX_VALUE else 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable { expanded = !expanded },
                )
                if (!expanded && text.length > 200) {
                    TextButton(onClick = { expanded = true }) { Text("Mehr anzeigen") }
                }
            }
            s.castSummary?.let {
                Text("Besetzung", style = MaterialTheme.typography.labelLarge)
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
            s.trailerKey?.let { key ->
                OutlinedButton(onClick = { uriHandler.openUri("https://www.youtube.com/watch?v=$key") }) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Trailer ansehen")
                }
            }
            Text(
                "Daten von TMDB",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ExcelCard(ip: ImportProgressEntity, onMatch: () -> Unit, showExcel: Boolean) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (!showExcel) {
                // Excel details switched off: only what matters for the app.
                Text("Noch nicht mit TMDB abgeglichen", style = MaterialTheme.typography.titleMedium)
                Text("Die genaue Folgenliste kommt von TMDB.", style = MaterialTheme.typography.bodySmall)
                Button(onClick = onMatch) { Text("Mit TMDB abgleichen") }
                return@Column
            }
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
    onTapEpisode: (EpisodeEntity) -> Unit,
    onToggleSeason: (Long, Boolean) -> Unit,
    onEpisodeDetails: (Long) -> Unit,
    rewatchCounts: Map<Long, Int> = emptyMap(),
    onWatchSeasonAgain: () -> Unit = {},
) {
    var expanded by rememberSaveable(season.season.id) { mutableStateOf(initiallyExpanded) }
    val counting = season.episodes.filter { it.counts }
    val watched = counting.count { it.isWatched }
    val total = counting.size
    val allWatched = total > 0 && watched == total
    val ratings = counting.mapNotNull { it.rating }

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
                val avg = ratings.takeIf { it.isNotEmpty() }?.let { " · Ø %.1f (%d bewertet)".format(it.average(), it.size) }
                Text("$watched/$total${avg.orEmpty()}", style = MaterialTheme.typography.bodySmall)
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
        if (expanded && season.season.number == 0) {
            // Specials as a list with title and air date, in order of first air date.
            var counter = 0
            Column(Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp)) {
                season.episodes.forEach { ep ->
                    val number = if (ep.counts) ++counter else null
                    SpecialRow(
                        number = number,
                        episode = ep,
                        onClick = { onTapEpisode(ep) },
                        onLongClick = { onEpisodeDetails(ep.id) },
                        viewings = 1 + (rewatchCounts[ep.id] ?: 0),
                    )
                }
                if (allWatched) {
                    TextButton(onClick = onWatchSeasonAgain) { Text("Alle Specials nochmal gesehen") }
                }
            }
        } else if (expanded) {
            FlowRow(
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                season.episodes.forEach { ep ->
                    EpisodeChip(
                        label = if (ep.kind == EpisodeKind.SPECIAL) "S ${ep.number}" else ep.number.toString(),
                        watched = ep.isWatched,
                        hidden = ep.kind == EpisodeKind.HIDDEN,
                        onClick = { onTapEpisode(ep) },
                        onLongClick = { onEpisodeDetails(ep.id) },
                        viewings = 1 + (rewatchCounts[ep.id] ?: 0),
                    )
                }
            }
            if (allWatched) {
                TextButton(onClick = onWatchSeasonAgain, modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)) {
                    Text("Staffel nochmal gesehen")
                }
            }
        }
    }
}
