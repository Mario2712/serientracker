package io.github.mario2712.serientracker.ui.tmdbsync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.mario2712.serientracker.data.repository.SyncState
import io.github.mario2712.serientracker.data.repository.TmdbSyncCoordinator
import io.github.mario2712.serientracker.data.repository.TmdbSyncRepository
import io.github.mario2712.serientracker.data.settings.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class SyncViewModel(
    private val coordinator: TmdbSyncCoordinator,
    repository: TmdbSyncRepository,
    settings: SettingsRepository,
) : ViewModel() {
    val state: StateFlow<SyncState> = coordinator.state
    val unsyncedCount: StateFlow<Int> = repository.observeUnsyncedCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** The list "Abweichungen zur Excel" can be switched off in the settings. */
    val showExcel: StateFlow<Boolean> = settings.showExcelComparisons
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    fun start() = coordinator.start()
    fun stop() = coordinator.stop()
}
