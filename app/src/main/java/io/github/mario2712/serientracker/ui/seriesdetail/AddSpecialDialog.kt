package io.github.mario2712.serientracker.ui.seriesdetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val inputDate = DateTimeFormatter.ofPattern("d.M.yyyy")

/** Adds a special TMDB does not list (e.g. "Altered Carbon: Resleeved", a film on TMDB). */
@Composable
fun AddSpecialDialog(onDismiss: () -> Unit, onAdd: (title: String, airDate: Long?, watched: Boolean) -> Unit) {
    var title by rememberSaveable { mutableStateOf("") }
    var date by rememberSaveable { mutableStateOf("") }
    var watched by rememberSaveable { mutableStateOf(true) }
    val parsed = date.trim().takeIf { it.isNotEmpty() }?.let { runCatching { LocalDate.parse(it, inputDate) }.getOrNull() }
    val dateInvalid = date.isNotBlank() && parsed == null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Eigenes Special") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Für Specials, die TMDB nicht in der Serie führt. Zählt wie ein Special mit.",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Titel") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = date,
                    onValueChange = { date = it },
                    label = { Text("Erstausstrahlung (TT.MM.JJJJ, optional)") },
                    isError = dateInvalid,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = watched, onCheckedChange = { watched = it })
                    Text("schon gesehen")
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank() && !dateInvalid,
                onClick = { onAdd(title, parsed?.toEpochDay(), watched) },
            ) { Text("Hinzufügen") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}
