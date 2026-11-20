package io.github.mario2712.serientracker.ui.tmdbsync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.mario2712.serientracker.data.repository.SyncState
import io.github.mario2712.serientracker.data.repository.TmdbSyncCoordinator
import io.github.mario2712.serientracker.data.repository.TmdbSyncRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class SyncViewModel(
    private val coordinator: TmdbSyncCoordinator,
    repository: TmdbSyncRepository,
) : ViewModel() {
    val state: StateFlow<SyncState> = coordinator.state
    val unsyncedCount: StateFlow<Int> = repository.observeUnsyncedCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun start() = coordinator.start()
    fun stop() = coordinator.stop()
}
