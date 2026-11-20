package io.github.mario2712.serientracker.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import io.github.mario2712.serientracker.SerienTrackerApp

/** Daily: fetches new episodes from TMDB and notifies about them. */
class NewEpisodesWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as SerienTrackerApp).container
        // Without a TMDB key there is nothing to check; not an error.
        if (container.settings.currentTmdbApiKey().isNullOrBlank()) return Result.success()
        val reports = container.tmdbSyncRepository.checkForNewEpisodes()
        container.settings.setLastCheck(System.currentTimeMillis())
        if (container.settings.currentBackground().notificationsEnabled || inputData.getBoolean(KEY_MANUAL, false)) {
            Notifications.showNewEpisodes(applicationContext, reports)
        }
        return Result.success()
    }

    companion object {
        const val KEY_MANUAL = "manual"
    }
}
