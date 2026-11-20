package io.github.mario2712.serientracker

import android.app.Application
import android.content.ContentResolver
import android.util.Log
import io.github.mario2712.serientracker.data.export.BackupRepository
import io.github.mario2712.serientracker.data.export.ExportRepository
import io.github.mario2712.serientracker.data.stats.StatsRepository
import io.github.mario2712.serientracker.data.local.AppDatabase
import io.github.mario2712.serientracker.data.remote.TmdbClient
import io.github.mario2712.serientracker.data.repository.SeriesRepository
import io.github.mario2712.serientracker.data.repository.TmdbSyncCoordinator
import io.github.mario2712.serientracker.data.repository.TmdbSyncRepository
import io.github.mario2712.serientracker.data.seed.SeedImporter
import io.github.mario2712.serientracker.data.settings.SettingsRepository
import io.github.mario2712.serientracker.data.upcoming.UpcomingRepository
import io.github.mario2712.serientracker.work.Notifications
import io.github.mario2712.serientracker.work.WorkScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SerienTrackerApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Notifications.createChannels(this)
        container.appScope.launch {
            runCatching { container.seedImporter.importIfNeeded() }
                .onFailure { Log.e("SerienTracker", "Excel-Startdaten konnten nicht geladen werden", it) }
            runCatching { container.seedImporter.backfillExcelCounts() }
                .onFailure { Log.e("SerienTracker", "Excel-Vergleichswerte konnten nicht nachgetragen werden", it) }
            // Re-register the background jobs (KEEP: does nothing if they are already scheduled).
            runCatching {
                val bg = container.settings.currentBackground()
                container.workScheduler.setNewEpisodeCheck(bg.notificationsEnabled)
                container.workScheduler.setAutoBackup(bg.backupEnabled && bg.backupFolder != null)
            }.onFailure { Log.w("SerienTracker", "Hintergrund-Jobs nicht geplant", it) }
        }
    }
}

/**
 * Manual dependency injection: one place that builds the long-lived objects.
 * Small enough for now; switch to Hilt if the graph keeps growing.
 */
class AppContainer(app: Application) {
    /** For work that must outlive a screen (seed import, TMDB sync). */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val appVersion: String = "${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})"
    val contentResolver: ContentResolver = app.contentResolver

    private val database: AppDatabase by lazy { AppDatabase.build(app) }
    val exportRepository: ExportRepository by lazy { ExportRepository(database, appVersion) }
    val backupRepository: BackupRepository by lazy { BackupRepository(database, appVersion) }
    val statsRepository: StatsRepository by lazy { StatsRepository(database) }
    val upcomingRepository: UpcomingRepository by lazy { UpcomingRepository(database) }
    val workScheduler: WorkScheduler by lazy { WorkScheduler(app) }
    val settings: SettingsRepository by lazy { SettingsRepository(app) }
    val seriesRepository: SeriesRepository by lazy { SeriesRepository(database) }
    val tmdbClient: TmdbClient by lazy { TmdbClient { settings.currentTmdbApiKey() } }
    val tmdbSyncRepository: TmdbSyncRepository by lazy { TmdbSyncRepository(database, tmdbClient) }
    val tmdbSyncCoordinator: TmdbSyncCoordinator by lazy { TmdbSyncCoordinator(tmdbSyncRepository, appScope) }
    val seedImporter: SeedImporter by lazy {
        SeedImporter(
            db = database,
            readSeed = { app.assets.open("seed/serien.json").bufferedReader().use { it.readText() } },
            isImported = { settings.isSeedImported() },
            markImported = { settings.setSeedImported() },
        )
    }
}
