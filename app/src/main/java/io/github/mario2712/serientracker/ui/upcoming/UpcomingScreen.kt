package io.github.mario2712.serientracker.ui.upcoming

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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.mario2712.serientracker.data.repository.SeriesRepository
import io.github.mario2712.serientracker.data.settings.SettingsRepository
import io.github.mario2712.serientracker.data.upcoming.NewlyAiredItem
import io.github.mario2712.serientracker.data.upcoming.UpcomingData
import io.github.mario2712.serientracker.data.upcoming.UpcomingItem
import io.github.mario2712.serientracker.data.upcoming.UpcomingRepository
import io.github.mario2712.serientracker.ui.AppViewModelProvider
import io.github.mario2712.serientracker.ui.components.Poster
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

class UpcomingViewModel(
    private val repository: UpcomingRepository,
    private val seriesRepository: SeriesRepository,
    private val settings: SettingsRepository,
) : ViewModel() {
    var data by mutableStateOf<UpcomingData?>(null)
        private set
    var lastCheck by mutableStateOf<Long?>(null)
        private set

    fun load() {
        viewModelScope.launch {
            data = repository.load()
            lastCheck = settings.currentBackground().lastCheckAt
        }
    }

    fun watchNext(seriesId: Long) {
        viewModelScope.launch {
            seriesRepository.watchNextEpisode(seriesId)
            data = repository.load()
        }
    }
}

private val dayFormat = DateTimeFormatter.ofPattern("dd.MM.")
private val dateTimeFormat = DateTimeFormatter.ofPattern("dd.MM.yyyy, HH:mm")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpcomingScreen(
    onBack: () -> Unit,
    onOpenSeries: (Long) -> Unit,
    viewModel: UpcomingViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    LaunchedEffect(Unit) { viewModel.load() }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Demnächst") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück") }
                },
                actions = {
                    IconButton(onClick = viewModel::load) { Icon(Icons.Filled.Refresh, contentDescription = "Neu laden") }
                },
            )
        },
    ) { padding ->
        val data = viewModel.data
        Box(Modifier.padding(padding).fillMaxSize()) {
            if (data == null) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            } else {
                Content(data, viewModel.lastCheck, onOpenSeries, viewModel::watchNext)
            }
        }
    }
}

@Composable
private fun Content(data: UpcomingData, lastCheck: Long?, onOpen: (Long) -> Unit, onWatchNext: (Long) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text(
                "Für laufende, pausierte, aufgeholte und fertige Serien (nicht für abgebrochene oder geplante). " +
                    (lastCheck?.let {
                        "Zuletzt bei TMDB geprüft: " + Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(dateTimeFormat) + "."
                    } ?: "Aktuell halten: Einstellungen → Benachrichtigung oder „Alle aktualisieren“."),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (data.newlyAired.isNotEmpty()) {
            item { SectionTitle("Neu erschienen") }
            items(data.newlyAired, key = { "n${it.seriesId}" }) { NewRow(it, onOpen, onWatchNext) }
        }
        data.upcoming.forEach { (section, items) ->
            item(key = "h$section") { SectionTitle(section) }
            items(items, key = { "u${it.seriesId}" }) { UpcomingRow(it, onOpen) }
        }
        if (data.newlyAired.isEmpty() && data.upcoming.isEmpty()) {
            item {
                Text(
                    "Keine angekündigten Folgen. Sobald TMDB neue Termine kennt, erscheinen sie hier.",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun NewRow(item: NewlyAiredItem, onOpen: (Long) -> Unit, onWatchNext: (Long) -> Unit) {
    Card(Modifier.fillMaxWidth().clickable { onOpen(item.seriesId) }) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Poster(item.posterUrl, Modifier.width(40.dp).height(60.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(item.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(
                        if (item.unwatched == 1) "1 neue Folge" else "${item.unwatched} neue Folgen",
                        "zuletzt ${item.latestAirDate.format(dayFormat)}",
                        item.nextCode?.let { "als Nächstes $it" },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (item.nextCode != null) {
                FilledTonalIconButton(onClick = { onWatchNext(item.seriesId) }) {
                    Icon(Icons.Filled.Check, contentDescription = "${item.nextCode} gesehen")
                }
            }
        }
    }
}

@Composable
private fun UpcomingRow(item: UpcomingItem, onOpen: (Long) -> Unit) {
    Card(Modifier.fillMaxWidth().clickable { onOpen(item.seriesId) }) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.width(56.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(item.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.GERMAN), style = MaterialTheme.typography.labelMedium)
                Text(item.date.format(dayFormat), style = MaterialTheme.typography.titleSmall)
            }
            Poster(item.posterUrl, Modifier.width(40.dp).height(60.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(item.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val episode = listOfNotNull(item.code, item.episodeName).joinToString(" – ")
                if (episode.isNotEmpty()) Text(episode, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                Text(
                    when (item.daysAway) {
                        0L -> "heute"
                        1L -> "morgen"
                        else -> "in ${item.daysAway} Tagen"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
