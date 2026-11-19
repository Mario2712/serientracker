package io.github.mario2712.serientracker.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import io.github.mario2712.serientracker.data.export.BackupRepository
import io.github.mario2712.serientracker.data.export.ExportRepository
import io.github.mario2712.serientracker.data.remote.Tmdb
import io.github.mario2712.serientracker.ui.AppViewModelProvider

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val uriHandler = LocalUriHandler.current
    var confirmReset by remember { mutableStateOf(false) }
    var restoreUri by remember { mutableStateOf<Uri?>(null) }

    val excelLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(ExportRepository.MIME_XLSX),
    ) { uri -> uri?.let(viewModel::exportExcel) }
    val backupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(BackupRepository.MIME_JSON),
    ) { uri -> uri?.let(viewModel::exportBackup) }
    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> restoreUri = uri }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Einstellungen") },
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (viewModel.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            viewModel.message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }

            Text("TMDB", style = MaterialTheme.typography.titleMedium)
            Text(
                "Für exakte Staffeln, Folgen und Poster braucht die App einen kostenlosen TMDB-Key: " +
                    "Account auf themoviedb.org anlegen → Einstellungen → API → „API-Schlüssel“ " +
                    "(oder „API-Lesezugriffstoken“) kopieren und hier einfügen.",
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(onClick = { uriHandler.openUri(Tmdb.KEY_URL) }) { Text("TMDB-API-Seite öffnen") }
            OutlinedTextField(
                value = viewModel.apiKey,
                onValueChange = viewModel::onApiKeyChange,
                label = { Text("API-Key") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(onClick = viewModel::saveAndCheckKey, enabled = !viewModel.busy) { Text("Speichern & prüfen") }
            OutlinedButton(onClick = viewModel::refreshAll, enabled = !viewModel.busy) {
                Text("Alle abgeglichenen Serien aktualisieren")
            }

            HorizontalDivider()
            Text("Export & Archiv", style = MaterialTheme.typography.titleMedium)
            Text(
                "Die Excel hat das Format deiner ursprünglichen Serienliste. Das Archiv enthält den " +
                    "kompletten Stand mit allen Folgen und Abhak-Zeitpunkten und lässt sich wieder einspielen.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(onClick = { excelLauncher.launch(viewModel.excelFileName()) }, enabled = !viewModel.busy) {
                Text("Excel exportieren")
            }
            OutlinedButton(onClick = { backupLauncher.launch(viewModel.backupFileName()) }, enabled = !viewModel.busy) {
                Text("Archiv exportieren")
            }
            OutlinedButton(
                onClick = { restoreLauncher.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) },
                enabled = !viewModel.busy,
            ) {
                Text("Aus Archiv wiederherstellen")
            }

            HorizontalDivider()
            Text("Daten", style = MaterialTheme.typography.titleMedium)
            OutlinedButton(onClick = { confirmReset = true }, enabled = !viewModel.busy) {
                Text("Auf Excel-Stand zurücksetzen")
            }
        }
    }

    restoreUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { restoreUri = null },
            title = { Text("Archiv wiederherstellen?") },
            text = { Text("Der aktuelle Stand in der App wird vollständig durch das Archiv ersetzt.") },
            confirmButton = {
                TextButton(onClick = { restoreUri = null; viewModel.restoreBackup(uri) }) { Text("Wiederherstellen") }
            },
            dismissButton = { TextButton(onClick = { restoreUri = null }) { Text("Abbrechen") } },
        )
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Auf Excel-Stand zurücksetzen?") },
            text = { Text("Alle Serien und der gesamte Fortschritt in der App werden gelöscht und aus der eingebauten Excel-Liste neu geladen.") },
            confirmButton = {
                TextButton(onClick = { confirmReset = false; viewModel.resetToExcel() }) { Text("Zurücksetzen") }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Abbrechen") } },
        )
    }
}
