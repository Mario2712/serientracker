package io.github.mario2712.serientracker.ui.settings

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import io.github.mario2712.serientracker.data.settings.BackgroundSettings
import io.github.mario2712.serientracker.work.AutoBackup
import io.github.mario2712.serientracker.work.WorkScheduler
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import androidx.compose.runtime.getValue
import io.github.mario2712.serientracker.data.export.BackupRepository
import io.github.mario2712.serientracker.data.export.ExportRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.mario2712.serientracker.data.remote.TmdbClient
import io.github.mario2712.serientracker.data.repository.TmdbSyncRepository
import io.github.mario2712.serientracker.data.seed.SeedImporter
import io.github.mario2712.serientracker.data.settings.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

class SettingsViewModel(
    private val settings: SettingsRepository,
    private val tmdbClient: TmdbClient,
    private val syncRepository: TmdbSyncRepository,
    private val seedImporter: SeedImporter,
    private val exportRepository: ExportRepository,
    private val backupRepository: BackupRepository,
    private val contentResolver: ContentResolver,
    private val workScheduler: WorkScheduler,
) : ViewModel() {

    /** Notification and auto-backup state for the settings screen. */
    val background: StateFlow<BackgroundSettings> = settings.background
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BackgroundSettings())

    val showExcel: StateFlow<Boolean> = settings.showExcelComparisons
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    fun setShowExcel(show: Boolean) {
        viewModelScope.launch { settings.setShowExcelComparisons(show) }
    }

    fun setNotifications(enabled: Boolean) {
        viewModelScope.launch {
            settings.setNotificationsEnabled(enabled)
            workScheduler.setNewEpisodeCheck(enabled)
            message = if (enabled) "Täglich gegen 8 Uhr wird auf neue Folgen geprüft." else "Benachrichtigung aus."
        }
    }

    fun checkNow() {
        workScheduler.checkNow()
        message = "Prüfung gestartet. Neue Folgen erscheinen als Benachrichtigung."
    }

    /** Folder picked with OpenDocumentTree; keeps access across restarts. */
    fun setBackupFolder(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
            settings.setBackupFolder(uri.toString())
            settings.setBackupEnabled(true)
            workScheduler.setAutoBackup(true)
            message = "Automatisches Backup aktiv: wöchentlich, die letzten ${AutoBackup.KEEP} bleiben erhalten."
        }
    }

    fun setAutoBackup(enabled: Boolean) {
        viewModelScope.launch {
            settings.setBackupEnabled(enabled)
            workScheduler.setAutoBackup(enabled && settings.currentBackground().backupFolder != null)
        }
    }

    fun backupNow() {
        workScheduler.backupNow()
        message = "Backup gestartet."
    }

    var apiKey by mutableStateOf("")
        private set
    var message by mutableStateOf<String?>(null)
        private set
    var busy by mutableStateOf(false)
        private set

    init {
        viewModelScope.launch { apiKey = settings.tmdbApiKey.first().orEmpty() }
    }

    fun onApiKeyChange(value: String) { apiKey = value }

    fun saveAndCheckKey() = runBusy {
        settings.setTmdbApiKey(apiKey)
        message = if (apiKey.isBlank()) {
            "Key gelöscht."
        } else if (tmdbClient.checkKey()) {
            "Key gespeichert und gültig ✓"
        } else {
            "TMDB lehnt diesen Key ab. Bitte „API-Schlüssel“ oder „Lesezugriffstoken“ prüfen."
        }
    }

    fun refreshAll() = runBusy {
        message = "Aktualisiere …"
        val withNew = syncRepository.refreshAll { done, total -> message = "Aktualisiere $done/$total …" }
        message = "Fertig. $withNew Serien haben neue Folgen."
    }

    fun resetToExcel() = runBusy {
        seedImporter.resetToSeed()
        message = "Excel-Stand wiederhergestellt. Jetzt erneut mit TMDB abgleichen."
    }

    fun excelFileName(): String = ExportRepository.excelFileName()
    fun backupFileName(): String = BackupRepository.fileName()

    fun exportExcel(uri: Uri) = runBusy {
        message = "Excel wird erstellt …"
        withContext(Dispatchers.IO) {
            val out = checkNotNull(contentResolver.openOutputStream(uri)) { "Datei kann nicht geschrieben werden" }
            out.use { exportRepository.exportExcel(it) }
        }
        message = "Excel exportiert ✓"
    }

    fun exportBackup(uri: Uri) = runBusy {
        message = "Archiv wird erstellt …"
        withContext(Dispatchers.IO) {
            val out = checkNotNull(contentResolver.openOutputStream(uri)) { "Datei kann nicht geschrieben werden" }
            out.use { backupRepository.export(it) }
        }
        message = "Archiv exportiert ✓"
    }

    fun restoreBackup(uri: Uri) = runBusy {
        message = "Archiv wird eingespielt …"
        val count = withContext(Dispatchers.IO) {
            val input = checkNotNull(contentResolver.openInputStream(uri)) { "Datei kann nicht gelesen werden" }
            input.use { backupRepository.restore(it) }
        }
        message = "$count Serien aus dem Archiv wiederhergestellt ✓"
    }

    private fun runBusy(block: suspend () -> Unit) {
        if (busy) return
        viewModelScope.launch {
            busy = true
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                message = "Fehler: ${e.message ?: e::class.simpleName}"
            } finally {
                busy = false
            }
        }
    }
}
