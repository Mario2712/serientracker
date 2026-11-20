package io.github.mario2712.serientracker.ui.tmdbsync

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.mario2712.serientracker.domain.Progress
import io.github.mario2712.serientracker.ui.AppViewModelProvider

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncScreen(
    onBack: () -> Unit,
    onResolve: (Long) -> Unit,
    onOpenSeries: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: SyncViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val unsynced by viewModel.unsyncedCount.collectAsStateWithLifecycle()
    val showExcel by viewModel.showExcel.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("TMDB-Abgleich") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Holt für jede Serie die echten Staffeln und Folgen von TMDB und hakt alles bis zu " +
                            "deinem Excel-Stand ab. Eindeutige Treffer werden automatisch übernommen, " +
                            "den Rest ordnest du selbst zu.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text("Noch nicht abgeglichen: $unsynced", style = MaterialTheme.typography.titleMedium)
                    if (state.running) {
                        LinearProgressIndicator(
                            progress = { Progress.fraction(state.done, state.total) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text("${state.done} / ${state.total}", style = MaterialTheme.typography.bodySmall)
                        OutlinedButton(onClick = viewModel::stop) { Text("Anhalten") }
                    } else {
                        Button(onClick = viewModel::start, enabled = unsynced > 0) {
                            Text(if (state.total > 0) "Weiter abgleichen" else "Abgleich starten")
                        }
                    }
                    state.error?.let { msg ->
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                            Column(Modifier.padding(12.dp)) {
                                Text(msg, color = MaterialTheme.colorScheme.onErrorContainer)
                                OutlinedButton(onClick = onOpenSettings) { Text("Einstellungen") }
                            }
                        }
                    }
                    if (state.matched.isNotEmpty()) {
                        Text("Automatisch bzw. von dir zugeordnet: ${state.matched.size}")
                    }
                }
            }

            if (state.review.isNotEmpty()) {
                item { SectionTitle("Bitte prüfen (${state.review.size})") }
                items(state.review, key = { "r${it.seriesId}" }) { item ->
                    ListRow(
                        title = item.title,
                        subtitle = if (item.candidateCount == 0) "Nichts gefunden: selbst suchen" else "${item.candidateCount} Vorschläge",
                        onClick = { onResolve(item.seriesId) },
                    )
                }
            }

            val diffs = state.differences
            if (showExcel && diffs.isNotEmpty()) {
                item { SectionTitle("Abweichungen zur Excel (${diffs.size})") }
                items(diffs, key = { "d${it.seriesId}" }) { r ->
                    ListRow(
                        title = r.title,
                        subtitle = "Excel ${r.excelWatched ?: "–"}/${r.excelTotal ?: "–"} · TMDB ${r.watched}/${r.total}",
                        onClick = { onOpenSeries(r.seriesId) },
                    )
                }
            }

            if (state.failed.isNotEmpty()) {
                item { SectionTitle("Fehler (${state.failed.size})") }
                items(state.failed) { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Column {
        HorizontalDivider()
        Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
    }
}

@Composable
private fun ListRow(title: String, subtitle: String, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(subtitle, style = MaterialTheme.typography.bodySmall)
            }
            Icon(Icons.Filled.KeyboardArrowRight, contentDescription = null)
        }
    }
}
