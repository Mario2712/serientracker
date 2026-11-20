package io.github.mario2712.serientracker.data.stats

import io.github.mario2712.serientracker.data.local.EpisodeEntity
import io.github.mario2712.serientracker.data.local.EpisodeViewEntity
import io.github.mario2712.serientracker.data.local.zone
import io.github.mario2712.serientracker.data.local.zoneOf
import io.github.mario2712.serientracker.data.local.SeriesEntity
import io.github.mario2712.serientracker.data.local.SeriesWithSeasons
import io.github.mario2712.serientracker.data.local.counts
import io.github.mario2712.serientracker.data.local.isWatched
import io.github.mario2712.serientracker.domain.Progress
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

data class YearReview(
    val year: Int,
    val episodes: Int,
    val hours: Double,
    val topSeries: List<Bar>,
    val topGenre: String?,
    val bestEpisode: RatedItem?,
    val binge: DayRecord?,
)

data class RatingComparison(
    /** Own minus TMDB, averaged; negative = stricter than TMDB. */
    val avgDifference: Double?,
    val compared: Int,
    val stricter: List<RatedItem>,
    val moreGenerous: List<RatedItem>,
)

data class DelayStats(
    val avgDays: Double?,
    val medianDays: Long?,
    val sameDay: Int,
    val counted: Int,
    val fastest: List<Bar>,
    val slowest: List<Bar>,
)

data class Heatmap(
    /** [weekday 0 = Monday][block 0..5 = 0–4, 4–8, … 20–24 h] → watched episodes. */
    val cells: List<List<Int>>,
    val bestWeekday: String?,
    val bestBlock: String?,
) {
    val max: Int get() = cells.maxOf { row -> row.maxOrNull() ?: 0 }

    companion object {
        val BLOCKS = listOf("0–4", "4–8", "8–12", "12–16", "16–20", "20–24")
        val WEEKDAYS = DayOfWeek.entries.map { it.getDisplayName(TextStyle.SHORT, Locale.GERMAN) }
    }
}

data class ExtraStats(
    /** Newest first; only years with real watch dates. */
    val years: List<YearReview>,
    val ratingComparison: RatingComparison,
    val delay: DelayStats,
    val heatmap: Heatmap,
)

/** Year review, own vs. TMDB rating, delay between airing and watching, weekday/time heatmap. */
object StatsExtrasCalculator {

    private const val MIN_EPISODES_FOR_DELAY = 3

    /** One real viewing in the local time where it was ticked; [first] = first viewing of the episode. */
    private data class Watch(
        val series: SeriesEntity,
        val episode: EpisodeEntity,
        val season: Int,
        val at: LocalDateTime,
        val first: Boolean,
    )

    fun compute(all: List<SeriesWithSeasons>, extraViews: Map<Long, List<EpisodeViewEntity>> = emptyMap()): ExtraStats {
        // Only real watch moments (no Excel/catch-up dates), each in the zone where it was ticked.
        val watches = all.flatMap { data ->
            data.seasons.flatMap { season ->
                season.episodes.filter { it.counts && it.isWatched }.flatMap { ep ->
                    val first = if (ep.watchedAtImported) {
                        emptyList()
                    } else {
                        listOf(Watch(data.series, ep, season.season.number, local(ep.watchedAt!!, ep.zone), first = true))
                    }
                    first + extraViews[ep.id].orEmpty().filter { !it.imported }.map { v ->
                        Watch(data.series, ep, season.season.number, local(v.watchedAt, zoneOf(v.zoneId)), first = false)
                    }
                }
            }
        }
        val medianRuntime = all.associate { data ->
            data.series.id to data.seasons.flatMap { it.episodes }.mapNotNull { it.runtime }.sorted()
                .let { if (it.isEmpty()) null else it[it.size / 2] }
        }
        return ExtraStats(
            years = yearReviews(watches, medianRuntime),
            ratingComparison = ratingComparison(all),
            delay = delay(watches),
            heatmap = heatmap(watches),
        )
    }

    private fun yearReviews(watches: List<Watch>, medianRuntime: Map<Long, Int?>): List<YearReview> =
        watches.groupBy { it.at.year }.toSortedMap(compareByDescending { it }).map { (year, list) ->
            val minutes = list.sumOf { (it.episode.runtime ?: medianRuntime[it.series.id] ?: 0).toDouble() }
            val topSeries = list.groupBy { it.series.id }
                .map { (id, w) -> Bar(w.first().series.title, w.size.toDouble(), seriesId = id) }
                .sortedByDescending { it.value }.take(5)
            val topGenre = list.flatMap { StatsCalculator.genres(it.series.genre) }
                .groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
            val best = list.filter { it.episode.rating != null }.maxByOrNull { it.episode.rating!! }?.let {
                RatedItem(
                    title = "${it.series.title} · ${Progress.episodeCode(it.season, it.episode.number)}",
                    subtitle = it.episode.title.orEmpty(),
                    rating = it.episode.rating!!.toDouble(),
                    seriesId = it.series.id,
                )
            }
            val binge = list.groupingBy { it.series.id to it.at.toLocalDate() }.eachCount()
                .maxWithOrNull(compareBy({ it.value }, { it.key.second }))
                ?.let { (key, count) ->
                    DayRecord(key.second, count, list.first { it.series.id == key.first }.series.title, key.first)
                }
            YearReview(year, list.size, minutes / 60, topSeries, topGenre, best, binge)
        }

    private fun ratingComparison(all: List<SeriesWithSeasons>): RatingComparison {
        val pairs = all.mapNotNull { data ->
            val tmdb = data.series.tmdbRating ?: return@mapNotNull null
            val rated = data.seasons.flatMap { it.episodes }.filter { it.counts }.mapNotNull { it.rating }
            val mine = data.series.rating?.toDouble()
                ?: rated.takeIf { it.size >= StatsCalculator.MIN_RATED_EPISODES_FOR_SERIES }?.average()
                ?: return@mapNotNull null
            Triple(data.series, mine, tmdb)
        }
        fun item(t: Triple<SeriesEntity, Double, Double>) = RatedItem(
            title = t.first.title,
            subtitle = "Ich %.1f · TMDB %.1f".format(Locale.GERMANY, t.second, t.third),
            rating = t.second - t.third,
            seriesId = t.first.id,
        )
        val diffs = pairs.map { it.second - it.third }
        return RatingComparison(
            avgDifference = diffs.takeIf { it.isNotEmpty() }?.average(),
            compared = pairs.size,
            stricter = pairs.filter { it.second < it.third }.sortedBy { it.second - it.third }.take(5).map(::item),
            moreGenerous = pairs.filter { it.second > it.third }.sortedByDescending { it.second - it.third }.take(5).map(::item),
        )
    }

    private fun local(millis: Long, zone: ZoneId): LocalDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), zone)

    private fun delay(watches: List<Watch>): DelayStats {
        // Only the first viewing tells how soon after airing an episode was watched.
        val withAir = watches.filter { it.first }.mapNotNull { w ->
            val air = w.episode.airDate?.let(LocalDate::ofEpochDay) ?: return@mapNotNull null
            val days = ChronoUnit.DAYS.between(air, w.at.toLocalDate())
            if (days < 0) null else w to days
        }
        val days = withAir.map { it.second }.sorted()
        val perSeries = withAir.groupBy { it.first.series.id }
            .filter { it.value.size >= MIN_EPISODES_FOR_DELAY }
            .map { (id, list) -> Bar(list.first().first.series.title, list.map { it.second }.average(), seriesId = id) }
        return DelayStats(
            avgDays = days.takeIf { it.isNotEmpty() }?.average(),
            medianDays = days.takeIf { it.isNotEmpty() }?.get(days.size / 2),
            sameDay = days.count { it == 0L },
            counted = days.size,
            fastest = perSeries.sortedBy { it.value }.take(5),
            slowest = perSeries.sortedByDescending { it.value }.take(5),
        )
    }

    private fun heatmap(watches: List<Watch>): Heatmap {
        val cells = List(7) { MutableList(6) { 0 } }
        watches.forEach { w -> cells[w.at.dayOfWeek.value - 1][w.at.hour / 4]++ }
        val byDay = cells.map { it.sum() }
        val byBlock = (0 until 6).map { b -> cells.sumOf { it[b] } }
        return Heatmap(
            cells = cells,
            bestWeekday = byDay.indexOfMax()?.let { DayOfWeek.of(it + 1).getDisplayName(TextStyle.FULL, Locale.GERMAN) },
            bestBlock = byBlock.indexOfMax()?.let { "${Heatmap.BLOCKS[it]} Uhr" },
        )
    }

    private fun List<Int>.indexOfMax(): Int? = if (all { it == 0 }) null else indices.maxByOrNull { this[it] }
}
