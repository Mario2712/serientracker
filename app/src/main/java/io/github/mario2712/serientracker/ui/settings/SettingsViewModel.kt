package io.github.mario2712.serientracker.ui.settings

import androidx.compose.runtime.getValue
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
) : ViewModel() {

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
