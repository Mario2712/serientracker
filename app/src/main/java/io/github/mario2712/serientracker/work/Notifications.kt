package io.github.mario2712.serientracker.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import io.github.mario2712.serientracker.MainActivity
import io.github.mario2712.serientracker.R
import io.github.mario2712.serientracker.data.repository.SyncReport

object Notifications {
    const val CHANNEL_NEW_EPISODES = "new_episodes"
    const val EXTRA_SERIES_ID = "seriesId"
    private const val ID_NEW_EPISODES = 1001

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_NEW_EPISODES,
            "Neue Folgen",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = "Neue Folgen deiner laufenden, aufgeholten und fertigen Serien" }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** Title and lines of the summary notification; pure, so it can be tested. */
    fun content(reports: List<SyncReport>): Pair<String, List<String>> {
        val shows = merge(reports)
        val count = shows.sumOf { it.newEpisodes.size }
        val title = if (count == 1) "1 neue Folge" else "$count neue Folgen"
        val lines = shows.map { r ->
            if (r.newEpisodes.size == 1) "${r.title}: ${r.newEpisodes.first()}" else "${r.title}: ${r.newEpisodes.size} neue Folgen"
        }
        return title to lines
    }

    /** Several entries of the same show (duplicates, e.g. a rewatch) appear once. */
    fun merge(reports: List<SyncReport>): List<SyncReport> =
        reports.groupBy { it.tmdbId ?: -it.seriesId.toInt() }.values.map { group ->
            val first = group.first()
            if (group.size == 1) first else first.copy(title = "${first.title} (${group.size} Einträge)")
        }

    fun showNewEpisodes(context: Context, reports: List<SyncReport>) {
        if (reports.isEmpty() || !canNotify(context)) return
        val (title, lines) = content(reports)
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            // One series: open it directly.
            reports.takeIf { it.size == 1 }?.single()?.let { putExtra(EXTRA_SERIES_ID, it.seriesId) }
        }
        val pending = PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val style = NotificationCompat.InboxStyle().also { s -> lines.take(6).forEach(s::addLine) }
        val notification = NotificationCompat.Builder(context, CHANNEL_NEW_EPISODES)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(lines.joinToString(" · "))
            .setStyle(style)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(ID_NEW_EPISODES, notification)
        } catch (e: SecurityException) {
            // Permission revoked in the meantime; nothing to do.
        }
    }
}
