package io.github.mario2712.serientracker.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/** Registers the background jobs (new-episode check, auto backup). */
class WorkScheduler(context: Context) {

    private val workManager = WorkManager.getInstance(context)
    private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    fun setNewEpisodeCheck(enabled: Boolean) {
        if (!enabled) {
            workManager.cancelUniqueWork(NEW_EPISODES)
            return
        }
        val request = PeriodicWorkRequestBuilder<NewEpisodesWorker>(1, TimeUnit.DAYS)
            .setConstraints(network)
            .setInitialDelay(delayUntil(LocalTime.of(8, 0)).toMinutes(), TimeUnit.MINUTES)
            .build()
        workManager.enqueueUniquePeriodicWork(NEW_EPISODES, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    fun checkNow() {
        val request = OneTimeWorkRequestBuilder<NewEpisodesWorker>()
            .setConstraints(network)
            .setInputData(workDataOf(NewEpisodesWorker.KEY_MANUAL to true))
            .build()
        workManager.enqueueUniqueWork("$NEW_EPISODES-now", ExistingWorkPolicy.REPLACE, request)
    }

    fun setAutoBackup(enabled: Boolean) {
        if (!enabled) {
            workManager.cancelUniqueWork(BACKUP)
            return
        }
        val request = PeriodicWorkRequestBuilder<BackupWorker>(7, TimeUnit.DAYS)
            .setInitialDelay(delayUntil(LocalTime.of(3, 0)).toMinutes(), TimeUnit.MINUTES)
            .build()
        workManager.enqueueUniquePeriodicWork(BACKUP, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    fun backupNow() {
        workManager.enqueueUniqueWork("$BACKUP-now", ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<BackupWorker>().build())
    }

    companion object {
        private const val NEW_EPISODES = "new-episodes"
        private const val BACKUP = "auto-backup"

        /** Time until the next [time] of day (tomorrow if it has passed today). */
        fun delayUntil(time: LocalTime, now: LocalDateTime = LocalDateTime.now()): Duration {
            var next = now.toLocalDate().atTime(time)
            if (!next.isAfter(now)) next = next.plusDays(1)
            return Duration.between(now, next)
        }
    }
}
