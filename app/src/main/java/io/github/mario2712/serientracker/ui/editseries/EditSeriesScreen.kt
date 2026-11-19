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
                        enabled = form.isValid && !viewModel.isLoading,
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

            OutlinedTextField(
                value = form.streamingService,
                onValueChange = { v -> viewModel.update { it.copy(streamingService = v) } },
                label = { Text("Wo? (z. B. Netflix)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

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
