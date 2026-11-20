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
import io.github.mario2712.serientracker.work.AutoBackup
import io.github.mario2712.serientracker.work.Notifications
import android.Manifest
import android.os.Build
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Switch
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private fun formatDateTime(millis: Long?): String? = millis?.let {
    Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd.MM.yyyy, HH:mm"))
}

@Composable
private fun SwitchRow(text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

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
    val folderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri -> uri?.let(viewModel::setBackupFolder) }
    // Android 13+: ask for the notification permission when switching notifications on.
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> viewModel.setNotifications(granted) }
    val context = LocalContext.current
    val background by viewModel.background.collectAsStateWithLifecycle()
    val showExcel by viewModel.showExcel.collectAsStateWithLifecycle()

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
            Text("Neue Folgen", style = MaterialTheme.typography.titleMedium)
            SwitchRow(
                text = "Bei neuen Folgen benachrichtigen",
                checked = background.notificationsEnabled,
                onChange = { on ->
                    val needsPermission = on && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        !Notifications.canNotify(context)
                    if (needsPermission) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        viewModel.setNotifications(on)
                    }
                },
            )
            Text(
                "Täglich gegen 8 Uhr prüft die App bei TMDB, ob es neue Folgen gibt: für laufende, pausierte, " +
                    "aufgeholte und fertige Serien (nicht für abgebrochene oder geplante). " +
                    "Zuletzt geprüft: ${formatDateTime(background.lastCheckAt) ?: "noch nie"}.",
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedButton(onClick = viewModel::checkNow, enabled = !viewModel.busy) { Text("Jetzt prüfen") }

            HorizontalDivider()
            Text("Automatisches Backup", style = MaterialTheme.typography.titleMedium)
            SwitchRow(
                text = "Wöchentlich sichern",
                checked = background.backupEnabled && background.backupFolder != null,
                onChange = { on ->
                    if (on && background.backupFolder == null) folderLauncher.launch(null) else viewModel.setAutoBackup(on)
                },
            )
            Text(
                listOfNotNull(
                    "Legt jede Woche ein Archiv in einem Ordner deiner Wahl ab (z. B. Downloads oder Google Drive); " +
                        "die letzten ${AutoBackup.KEEP} bleiben erhalten.",
                    background.backupFolder?.let { "Ordner: ${Uri.parse(it).lastPathSegment ?: it}" },
                    "Letztes Backup: ${formatDateTime(background.lastBackupAt) ?: "noch keines"}.",
                ).joinToString("\n"),
                style = MaterialTheme.typography.bodySmall,
            )
            background.lastBackupError?.let {
                Text("Fehler beim letzten Backup: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { folderLauncher.launch(null) }) {
                    Text(if (background.backupFolder == null) "Ordner wählen" else "Ordner ändern")
                }
                OutlinedButton(onClick = viewModel::backupNow, enabled = background.backupFolder != null) {
                    Text("Jetzt sichern")
                }
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
            SwitchRow(
                text = "Excel-Vergleiche anzeigen",
                checked = showExcel,
                onChange = viewModel::setShowExcel,
            )
            Text(
                "Zeigt Abweichungen zur ursprünglichen Excel-Liste und den Excel-Stand noch nicht abgeglichener Serien. " +
                    "Ausschalten, sobald du nur noch die App verwendest. Der Excel-Export bleibt verfügbar.",
                style = MaterialTheme.typography.bodySmall,
            )
            if (showExcel) {
                OutlinedButton(onClick = { confirmReset = true }, enabled = !viewModel.busy) {
                    Text("Auf Excel-Stand zurücksetzen")
                }
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
