package io.github.mario2712.serientracker.ui.editseries

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.mario2712.serientracker.domain.WatchStatus
import io.github.mario2712.serientracker.domain.Watcher
import io.github.mario2712.serientracker.ui.tmdbsync.CandidateRow
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import io.github.mario2712.serientracker.ui.AppViewModelProvider

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditSeriesScreen(
    onDone: (savedId: Long, isNew: Boolean) -> Unit,
    onBack: () -> Unit,
    viewModel: EditSeriesViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val form = viewModel.form

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (viewModel.isNew) "Neue Serie" else "Serie bearbeiten") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
                actions = {
                    TextButton(
                        enabled = form.isValid && !viewModel.isLoading && !viewModel.isSaving,
                        onClick = { viewModel.save { id -> onDone(id, viewModel.isNew) } },
                    ) { Text("Speichern") }
                },
            )
        },
    ) { padding ->
        if (viewModel.isLoading) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else Column(
            Modifier
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = form.title,
                onValueChange = { v -> viewModel.update { it.copy(title = v) } },
                label = { Text("Titel") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Next,
                ),
                modifier = Modifier.fillMaxWidth(),
            )

            if (viewModel.isNew) {
                TmdbSection(viewModel)
            }

            Text("Status", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WatchStatus.entries.forEach { status ->
                    FilterChip(
                        selected = form.status == status,
                        onClick = { viewModel.update { it.copy(status = status) } },
                        label = { Text(status.label) },
                    )
                }
            }

            Text("Wer schaut?", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Watcher.entries.forEach { w ->
                    FilterChip(
                        selected = form.watcher == w,
                        onClick = { viewModel.update { it.copy(watcher = if (it.watcher == w) null else w) } },
                        label = { Text(w.label) },
                    )
                }
            }

            OutlinedTextField(
                value = form.originalTitle,
                onValueChange = { v -> viewModel.update { it.copy(originalTitle = v) } },
                label = { Text("Originaltitel") },
                supportingText = { Text("Wird beim TMDB-Abgleich automatisch gesetzt") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = form.genre,
                onValueChange = { v -> viewModel.update { it.copy(genre = v) } },
                label = { Text("Genre") },
                supportingText = { Text("Kommt beim TMDB-Abgleich von TMDB, z. B. „Drama, Krimi“") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            if (viewModel.seasonsFromTmdb) {
                Text(
                    "Staffeln und Folgen kommen von TMDB.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                SeasonFields(viewModel)
            }

            Column {
                Text(
                    "Bewertung: " + (form.rating?.let { "$it/10" } ?: "keine"),
                    style = MaterialTheme.typography.titleSmall,
                )
                Slider(
                    value = (form.rating ?: 0).toFloat(),
                    onValueChange = { v ->
                        val r = v.toInt()
                        viewModel.update { it.copy(rating = if (r == 0) null else r) }
                    },
                    valueRange = 0f..10f,
                    steps = 9,
                )
            }

            OutlinedTextField(
                value = form.notes,
                onValueChange = { v -> viewModel.update { it.copy(notes = v) } },
                label = { Text("Notizen") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun SeasonFields(viewModel: EditSeriesViewModel) {
    val form = viewModel.form
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Staffeln & Folgen", style = MaterialTheme.typography.titleSmall)
        form.seasons.forEachIndexed { index, value ->
            OutlinedTextField(
                value = value,
                onValueChange = { viewModel.setSeasonEpisodes(index, it) },
                label = { Text("Staffel ${index + 1}: Anzahl Folgen") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = viewModel::addSeason) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text(" Staffel")
            }
            if (form.seasons.isNotEmpty()) {
                OutlinedButton(onClick = viewModel::removeLastSeason) {
                    Icon(Icons.Filled.Delete, contentDescription = null)
                    Text(" Letzte Staffel")
                }
            }
        }
    }
}

@Composable
private fun TmdbSection(viewModel: EditSeriesViewModel) {
    val pick = viewModel.form.tmdbPick
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Von TMDB übernehmen", style = MaterialTheme.typography.titleSmall)
            if (pick != null) {
                CandidateRow(pick) {}
                TextButton(onClick = { viewModel.pickTmdb(null) }) { Text("Andere wählen") }
            } else {
                OutlinedTextField(
                    value = viewModel.tmdbQuery,
                    onValueChange = viewModel::onTmdbQueryChange,
                    label = { Text("Serie suchen") },
                    singleLine = true,
                    trailingIcon = {
                        IconButton(onClick = viewModel::searchTmdb) {
                            Icon(Icons.Filled.Search, contentDescription = "Suchen")
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { viewModel.searchTmdb() }),
                    modifier = Modifier.fillMaxWidth(),
                )
                viewModel.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                viewModel.tmdbResults.take(5).forEach { r -> CandidateRow(r) { viewModel.pickTmdb(r) } }
            }
        }
    }
}
