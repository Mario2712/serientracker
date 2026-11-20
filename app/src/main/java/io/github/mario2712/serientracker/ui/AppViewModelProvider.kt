package io.github.mario2712.serientracker.ui

import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.mario2712.serientracker.AppContainer
import io.github.mario2712.serientracker.SerienTrackerApp
import io.github.mario2712.serientracker.ui.about.AboutViewModel
import io.github.mario2712.serientracker.ui.editseries.EditSeriesViewModel
import io.github.mario2712.serientracker.ui.seriesdetail.SeriesDetailViewModel
import io.github.mario2712.serientracker.ui.serieslist.SeriesListViewModel
import io.github.mario2712.serientracker.ui.settings.SettingsViewModel
import io.github.mario2712.serientracker.ui.stats.StatsViewModel
import io.github.mario2712.serientracker.ui.tmdbsync.MatchSeriesViewModel
import io.github.mario2712.serientracker.ui.tmdbsync.SyncViewModel

/** Builds every ViewModel with its dependencies from the [AppContainer]. */
object AppViewModelProvider {
    val Factory = viewModelFactory {
        initializer { SeriesListViewModel(c().seriesRepository) }
        initializer {
            SeriesDetailViewModel(createSavedStateHandle(), c().seriesRepository, c().tmdbSyncRepository)
        }
        initializer {
            EditSeriesViewModel(createSavedStateHandle(), c().seriesRepository, c().tmdbSyncRepository)
        }
        initializer { SyncViewModel(c().tmdbSyncCoordinator, c().tmdbSyncRepository) }
        initializer {
            MatchSeriesViewModel(
                createSavedStateHandle(),
                c().seriesRepository,
                c().tmdbSyncRepository,
                c().tmdbSyncCoordinator,
            )
        }
        initializer {
            SettingsViewModel(
                settings = c().settings,
                tmdbClient = c().tmdbClient,
                syncRepository = c().tmdbSyncRepository,
                seedImporter = c().seedImporter,
                exportRepository = c().exportRepository,
                backupRepository = c().backupRepository,
                contentResolver = c().contentResolver,
            )
        }
        initializer { AboutViewModel(c().appVersion, c().seriesRepository) }
        initializer { StatsViewModel(c().statsRepository) }
    }
}

private fun CreationExtras.c(): AppContainer = (this[APPLICATION_KEY] as SerienTrackerApp).container
