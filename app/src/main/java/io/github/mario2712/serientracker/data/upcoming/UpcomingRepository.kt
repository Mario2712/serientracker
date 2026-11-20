package io.github.mario2712.serientracker.data.upcoming

import io.github.mario2712.serientracker.data.local.AppDatabase
import io.github.mario2712.serientracker.data.local.SeriesWithSeasons
import io.github.mario2712.serientracker.data.local.counts
import io.github.mario2712.serientracker.data.local.displayTitle
import io.github.mario2712.serientracker.data.local.isWatched
import io.github.mario2712.serientracker.domain.EpisodeKind
import io.github.mario2712.serientracker.domain.Progress
import io.github.mario2712.serientracker.domain.WatchStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/** An announced next episode. */
data class UpcomingItem(
    val seriesId: Long,
    val title: String,
    val posterUrl: String?,
    val date: LocalDate,
    /** e.g. "S05E09", null if TMDB only knows the date. */
    val code: String?,
    val episodeName: String?,
    val daysAway: Long,
)

/** Recently aired episodes not watched yet, per series. */
data class NewlyAiredItem(
    val seriesId: Long,
    val title: String,
    val posterUrl: String?,
    val unwatched: Int,
    val latestAirDate: LocalDate,
    /** Next regular episode to watch, e.g. "S05E08". */
    val nextCode: String?,
)

data class UpcomingData(
    val newlyAired: List<NewlyAiredItem>,
    /** Section title ("Heute", "Diese Woche", …) → items, in date order. */
    val upcoming: List<Pair<String, List<UpcomingItem>>>,
)

/** "Demnächst": only for series that can get new episodes (not dropped, not "Geplant"). */
object Upcoming {
    const val NEW_DAYS = 14L

    fun compute(all: List<SeriesWithSeasons>, today: LocalDate): UpcomingData {
        val relevant = all.filter { it.series.tmdbId != null && it.series.status in WatchStatus.ALERT_STATUSES }

        val newlyAired = relevant.mapNotNull { data ->
            val episodes = data.seasons.flatMap { s -> s.episodes.filter { it.counts }.map { s.season.number to it } }
            val recent = episodes.filter { (_, ep) ->
                !ep.isWatched && ep.airDate != null &&
                    LocalDate.ofEpochDay(ep.airDate).let { !it.isAfter(today) && !it.isBefore(today.minusDays(NEW_DAYS)) }
            }
            if (recent.isEmpty()) return@mapNotNull null
            val next = episodes
                .filter { (_, ep) -> ep.kind == EpisodeKind.REGULAR && !ep.isWatched }
                .minWithOrNull(compareBy({ it.first }, { it.second.number }))
            NewlyAiredItem(
                seriesId = data.series.id,
                // Each entry has its own progress, so duplicates stay separate (with their extra name).
                title = data.series.displayTitle,
                posterUrl = data.series.posterUrl,
                unwatched = recent.size,
                latestAirDate = LocalDate.ofEpochDay(recent.maxOf { it.second.airDate!! }),
                nextCode = next?.let { Progress.episodeCode(it.first, it.second.number) },
            )
        }.sortedByDescending { it.latestAirDate }

        // Announced episodes are the same for duplicates of a show: list each show once.
        val items = relevant.groupBy { it.series.tmdbId }.values.mapNotNull { group ->
            val data = group.first()
            val s = data.series
            val date = s.nextAirDate?.let(LocalDate::ofEpochDay)?.takeIf { !it.isBefore(today) } ?: return@mapNotNull null
            UpcomingItem(
                seriesId = s.id,
                title = if (group.size > 1) "${s.title} (${group.size} Einträge)" else s.title,
                posterUrl = s.posterUrl,
                date = date,
                code = if (s.nextEpisodeSeason != null && s.nextEpisodeNumber != null) {
                    Progress.episodeCode(s.nextEpisodeSeason, s.nextEpisodeNumber)
                } else {
                    null
                },
                episodeName = s.nextEpisodeName,
                daysAway = ChronoUnit.DAYS.between(today, date),
            )
        }.sortedWith(compareBy({ it.date }, { it.title.lowercase() }))

        val endOfWeek = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
        val groups = items.groupBy { item ->
            when {
                item.date == today -> "Heute"
                !item.date.isAfter(endOfWeek) -> "Diese Woche"
                item.date.month == today.month && item.date.year == today.year -> "Diesen Monat"
                else -> "Später"
            }
        }
        val order = listOf("Heute", "Diese Woche", "Diesen Monat", "Später")
        return UpcomingData(newlyAired, order.mapNotNull { key -> groups[key]?.let { key to it } })
    }
}

class UpcomingRepository(private val db: AppDatabase) {
    suspend fun load(today: LocalDate = LocalDate.now()): UpcomingData {
        val all = db.seriesDao().getAllWithSeasons()
        return withContext(Dispatchers.Default) { Upcoming.compute(all, today) }
    }
}
