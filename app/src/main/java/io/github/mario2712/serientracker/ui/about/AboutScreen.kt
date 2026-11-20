package io.github.mario2712.serientracker.ui.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.mario2712.serientracker.data.export.ExportRepository
import io.github.mario2712.serientracker.data.local.DataStats
import io.github.mario2712.serientracker.data.repository.SeriesRepository
import io.github.mario2712.serientracker.ui.AppViewModelProvider
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class AboutViewModel(val appVersion: String, repository: SeriesRepository) : ViewModel() {
    val stats: StateFlow<DataStats?> = repository.observeStats()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    onBack: () -> Unit,
    viewModel: AboutViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val stats by viewModel.stats.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Über") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Serientracker", style = MaterialTheme.typography.headlineMedium)
            InfoCard(
                "App",
                listOf(
                    "Version" to viewModel.appVersion,
                    "Entwickler" to ExportRepository.DEVELOPER,
                ),
            )
            stats?.let { s ->
                InfoCard(
                    "Datenstand",
                    listOf(
                        "Serien" to s.series.toString(),
                        "mit TMDB abgeglichen" to s.synced.toString(),
                        "Folgen" to "%,d".format(s.episodes).replace(',', '.'),
                        "davon gesehen" to "%,d".format(s.watched).replace(',', '.'),
                    ),
                )
            }
            Text(
                "Serien- und Folgendaten sowie Poster stammen von TMDB (themoviedb.org). " +
                    "Diese App nutzt die TMDB-API, ist aber nicht von TMDB unterstützt oder zertifiziert.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun InfoCard(title: String, rows: List<Pair<String, String>>) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            rows.forEach { (key, value) ->
                Row {
                    Text(key, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text(value, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
