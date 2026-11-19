package io.github.mario2712.serientracker.ui

import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.mario2712.serientracker.SerienTrackerApp
import io.github.mario2712.serientracker.ui.editseries.EditSeriesViewModel
import io.github.mario2712.serientracker.ui.seriesdetail.SeriesDetailViewModel
import io.github.mario2712.serientracker.ui.serieslist.SeriesListViewModel

/** Builds every ViewModel with its dependencies from the [io.github.mario2712.serientracker.AppContainer]. */
object AppViewModelProvider {
    val Factory = viewModelFactory {
        initializer { SeriesListViewModel(app().container.seriesRepository) }
        initializer { SeriesDetailViewModel(createSavedStateHandle(), app().container.seriesRepository) }
        initializer { EditSeriesViewModel(createSavedStateHandle(), app().container.seriesRepository) }
    }
}

private fun CreationExtras.app(): SerienTrackerApp = this[APPLICATION_KEY] as SerienTrackerApp
