package io.github.mario2712.serientracker

import android.app.Application
import io.github.mario2712.serientracker.data.local.AppDatabase
import io.github.mario2712.serientracker.data.repository.SeriesRepository

class SerienTrackerApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/**
 * Manual dependency injection: one place that builds the long-lived objects.
 * Small enough for now; switch to Hilt if the graph grows (e.g. network + workers).
 */
class AppContainer(app: Application) {
    private val database: AppDatabase by lazy { AppDatabase.build(app) }
    val seriesRepository: SeriesRepository by lazy { SeriesRepository(database) }
}
