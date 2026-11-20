package io.github.mario2712.serientracker.ui.seriesdetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.mario2712.serientracker.data.local.SeriesEntity
import io.github.mario2712.serientracker.data.local.displayTitle
import io.github.mario2712.serientracker.domain.Watcher

/** "Als neuen Durchgang anlegen": same show again, without progress (e.g. a rewatch together). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DuplicateRunDialog(
    title: String,
    initialWatcher: Watcher?,
    onDismiss: () -> Unit,
    onCreate: (Watcher?, String?) -> Unit,
) {
    var watcher by rememberSaveable { mutableStateOf(initialWatcher) }
    var label by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Neuer Durchgang") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Legt „$title“ noch einmal an: mit allen Staffeln, Folgen und Infos, aber ohne Fortschritt. " +
                        "Der bisherige Eintrag bleibt unverändert.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text("Wer schaut?", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Watcher.entries.forEach { w ->
                        FilterChip(selected = watcher == w, onClick = { watcher = w }, label = { Text(w.label) })
                    }
                }
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Zusatzname (z. B. „Rewatch 2026“)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onCreate(watcher, label.trim().ifEmpty { null }) }) { Text("Anlegen") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

/** Shown when a new series looks like one that already exists. */
@Composable
fun DuplicateWarningDialog(
    existing: List<Pair<SeriesEntity, String>>,
    onOpen: (Long) -> Unit,
    onCreateAnyway: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Serie schon vorhanden") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                existing.forEach { (s, progress) ->
                    Text(
                        "„${s.displayTitle}“ (Wer: ${s.watcher?.label ?: "–"} · ${s.status.label} · $progress)",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Text(
                    "Trotzdem neu anlegen, z. B. wenn ihr sie nochmal gemeinsam schaut oder jemand allein? " +
                        "Infos und Folgen werden vom vorhandenen Eintrag übernommen, der Fortschritt startet leer.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = { TextButton(onClick = onCreateAnyway) { Text("Trotzdem neu anlegen") } },
        dismissButton = {
            Column {
                TextButton(onClick = { onOpen(existing.first().first.id) }) { Text("Vorhandene öffnen") }
                TextButton(onClick = onDismiss) { Text("Abbrechen") }
            }
        },
    )
}
