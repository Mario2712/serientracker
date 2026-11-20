package io.github.mario2712.serientracker.data.stats

import io.github.mario2712.serientracker.data.local.EpisodeEntity
import io.github.mario2712.serientracker.data.local.EpisodeViewEntity
import io.github.mario2712.serientracker.data.local.SeriesWithSeasons
import io.github.mario2712.serientracker.data.local.counts
import io.github.mario2712.serientracker.data.local.isWatched
import io.github.mario2712.serientracker.data.local.zone
import io.github.mario2712.serientracker.data.local.zoneOf
import io.github.mario2712.serientracker.domain.Progress
import io.github.mario2712.serientracker.domain.WatchStatus
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** One bar of a bar chart; [seriesId] makes it clickable. */
data class Bar(val label: String, val value: Double, val detail: String = "", val seriesId: Long? = null)

data class RatedItem(
    val title: String,
    val subtitle: String,
    val rating: Double,
    val seriesId: Long,
)

data class DayRecord(val date: LocalDate, val episodes: Int, val title: String? = null, val seriesId: Long? = null)

data class Deviation(
    val seriesId: Long,
    val title: String,
    val excelWatched: Int?,
    val excelTotal: Int?,
    val watched: Int,
    val total: Int,
)

data class Stats(
    val seriesCount: Int,
    val watchedEpisodes: Int,
    val totalHours: Double,
    /** Share of watched episodes (with episode data) whose runtime is known, 0..100. */
    val runtimeCoverage: Int,
    val avgEpisodeRating: Double?,
    val ratedEpisodes: Int,
    val genresByEpisodes: List<Bar>,
    val genresByHours: List<Bar>,
    val topEpisodes: List<RatedItem>,
    val topSeasons: List<RatedItem>,
    val topSeries: List<RatedItem>,
    val hoursPerYear: List<Bar>,
    val topSeriesByHours: List<Bar>,
    val perMonth: List<Bar>,
    val mostActiveDay: DayRecord?,
    val longestBinge: DayRecord?,
    val watcherSeries: List<Bar>,
    val watcherEpisodes: List<Bar>,
    val statusCounts: List<Bar>,
    val pileOfShame: List<Bar>,
    val deviations: List<Deviation>,
    val extras: ExtraStats,
    /** Episodes watched at least twice. */
    val mostWatchedEpisodes: List<Bar> = emptyList(),
)

/** All statistics, computed from the full data set. Pure function; unit-tested. */
object StatsCalculator {

    const val MIN_RATED_EPISODES_PER_SEASON = 2
    const val MIN_RATED_EPISODES_FOR_SERIES = 2
    private const val TOP = 10

    /** Year label for viewings without an exact date (Excel import, catch-up mode). */
    const val NO_DATE = "ohne genaues Datum"

    /**
     * @param extraViews further viewings per episode id (rewatches); they count for watch time and the
     *   time based statistics, while "watched episodes" and progress count each episode once.
     * Each viewing is evaluated in the time zone where it was ticked; [zone] is only the fallback for "today".
     */
    fun compute(
        all: List<SeriesWithSeasons>,
        today: LocalDate,
        @Suppress("UNUSED_PARAMETER") zone: ZoneId = ZoneId.systemDefault(),
        extraViews: Map<Long, List<EpisodeViewEntity>> = emptyMap(),
    ): Stats {
        val genreEpisodes = mutableMapOf<String, Int>()
        val genreMinutes = mutableMapOf<String, Double>()
        val watcherSeries = mutableMapOf<String, Int>()
        val watcherEpisodes = mutableMapOf<String, Int>()
        val yearMinutes = sortedMapOf<String, Double>()
        val seriesMinutes = mutableListOf<Bar>()
        val pile = mutableListOf<Bar>()
        val deviations = mutableListOf<Deviation>()
        val ratedEpisodes = mutableListOf<RatedItem>()
        val ratedSeasons = mutableListOf<RatedItem>()
        val ratedSeries = mutableListOf<RatedItem>()
        val realWatches = mutableListOf<Pair<SeriesWithSeasons, LocalDate>>()
        var watchedTotal = 0
        var minutesTotal = 0.0
        var withEpisodeData = 0
        var withRuntime = 0

        for (data in all) {
            val s = data.series
            val episodes = data.seasons.flatMap { it.episodes }.filter { it.counts }
            val unsynced = data.importProgress != null && episodes.isEmpty()
            val watched = if (unsynced) data.importProgress?.watched ?: 0 else episodes.count { it.isWatched }
            val total = if (unsynced) data.importProgress?.total ?: 0 else episodes.size
            watchedTotal += watched

            val median = episodes.mapNotNull { it.runtime }.sorted().let { if (it.isEmpty()) null else it[it.size / 2] }
            var minutes = 0.0
            for (ep in episodes.filter { it.isWatched }) {
                withEpisodeData++
                if (ep.runtime != null) withRuntime++
                val extras = extraViews[ep.id].orEmpty()
                // Every viewing in its own zone; null date = no exact date.
                val viewings = listOf(if (ep.watchedAtImported) null else localDate(ep.watchedAt!!, ep.zone)) +
                    extras.map { v -> if (v.imported) null else localDate(v.watchedAt, zoneOf(v.zoneId)) }
                viewings.filterNotNull().forEach { realWatches += data to it }
                val m = (ep.runtime ?: median)?.toDouble() ?: continue
                viewings.forEach { day ->
                    minutes += m
                    yearMinutes.merge(day?.year?.toString() ?: NO_DATE, m, Double::plus)
                }
            }
            minutesTotal += minutes
            if (minutes > 0) seriesMinutes += Bar(s.title, minutes / 60, seriesId = s.id)

            genres(s.genre).forEach { g ->
                genreEpisodes.merge(g, watched, Int::plus)
                if (minutes > 0) genreMinutes.merge(g, minutes, Double::plus)
            }
            val who = s.watcher?.label ?: "ohne Angabe"
            watcherSeries.merge(who, 1, Int::plus)
            watcherEpisodes.merge(who, watched, Int::plus)

            if (s.status != WatchStatus.DROPPED && s.status != WatchStatus.COMPLETED && total > watched) {
                pile += Bar(s.title, (total - watched).toDouble(), "$watched/$total gesehen", s.id)
            }
            if (!unsynced && episodes.isNotEmpty() && (s.excelTotal != null || s.excelWatched != null) &&
                (s.excelTotal != total || s.excelWatched != watched)
            ) {
                deviations += Deviation(s.id, s.title, s.excelWatched, s.excelTotal, watched, total)
            }

            // Ratings
            data.seasons.forEach { season ->
                val rated = season.episodes.filter { it.counts && it.rating != null }
                rated.forEach { ep ->
                    ratedEpisodes += RatedItem(
                        title = "${s.title} · ${Progress.episodeCode(season.season.number, ep.number)}",
                        subtitle = ep.title.orEmpty(),
                        rating = ep.rating!!.toDouble(),
                        seriesId = s.id,
                    )
                }
                if (rated.size >= MIN_RATED_EPISODES_PER_SEASON) {
                    val name = season.season.title ?: if (season.season.number == 0) "Specials" else "Staffel ${season.season.number}"
                    ratedSeasons += RatedItem(s.title, "$name · ${rated.size} bewertete Folgen", rated.average(), s.id)
                }
            }
            val ratedOfSeries = episodes.filter { it.rating != null }
            when {
                s.rating != null -> ratedSeries += RatedItem(s.title, "eigene Bewertung", s.rating.toDouble(), s.id)
                ratedOfSeries.size >= MIN_RATED_EPISODES_FOR_SERIES -> ratedSeries += RatedItem(
                    s.title, "Ø aus ${ratedOfSeries.size} bewerteten Folgen", ratedOfSeries.average(), s.id,
                )
            }
        }

        val days = realWatches.groupingBy { it.second }.eachCount()
        val mostActive = days.maxWithOrNull(compareBy({ it.value }, { it.key }))?.let { DayRecord(it.key, it.value) }
        val binge = realWatches.groupingBy { it.first.series.id to it.second }.eachCount()
            .maxWithOrNull(compareBy({ it.value }, { it.key.second }))
            ?.let { (key, count) ->
                val series = all.first { it.series.id == key.first }.series
                DayRecord(key.second, count, series.title, series.id)
            }
        val thisMonth = YearMonth.from(today)
        val monthCounts = realWatches.groupingBy { YearMonth.from(it.second) }.eachCount()
        val perMonth = (11 downTo 0).map { back ->
            val month = thisMonth.minusMonths(back.toLong())
            Bar("%02d/%02d".format(month.monthValue, month.year % 100), (monthCounts[month] ?: 0).toDouble())
        }
        val allRatings = ratedEpisodes.map { it.rating }

        return Stats(
            seriesCount = all.size,
            watchedEpisodes = watchedTotal,
            totalHours = minutesTotal / 60,
            runtimeCoverage = if (withEpisodeData == 0) 0 else withRuntime * 100 / withEpisodeData,
            avgEpisodeRating = allRatings.takeIf { it.isNotEmpty() }?.average(),
            ratedEpisodes = allRatings.size,
            genresByEpisodes = genreEpisodes.map { Bar(it.key, it.value.toDouble()) }.sortedByDescending { it.value },
            genresByHours = genreMinutes.map { Bar(it.key, it.value / 60) }.sortedByDescending { it.value },
            topEpisodes = ratedEpisodes.sortedByDescending { it.rating }.take(TOP),
            topSeasons = ratedSeasons.sortedByDescending { it.rating }.take(TOP),
            topSeries = ratedSeries.sortedByDescending { it.rating }.take(TOP),
            hoursPerYear = yearMinutes.map { Bar(it.key, it.value / 60) },
            topSeriesByHours = seriesMinutes.sortedByDescending { it.value }.take(TOP),
            perMonth = perMonth,
            mostActiveDay = mostActive,
            longestBinge = binge,
            watcherSeries = watcherSeries.map { Bar(it.key, it.value.toDouble()) }.sortedByDescending { it.value },
            watcherEpisodes = watcherEpisodes.map { Bar(it.key, it.value.toDouble()) }.sortedByDescending { it.value },
            statusCounts = all.groupingBy { it.series.status }.eachCount()
                .map { Bar(it.key.label, it.value.toDouble()) }.sortedByDescending { it.value },
            pileOfShame = pile.sortedByDescending { it.value }.take(TOP),
            deviations = deviations.sortedBy { it.title.lowercase() },
            mostWatchedEpisodes = mostWatched(all, extraViews),
            extras = StatsExtrasCalculator.compute(all, extraViews),
        )
    }

    /** Episodes seen at least twice, most viewings first. */
    private fun mostWatched(all: List<SeriesWithSeasons>, extraViews: Map<Long, List<EpisodeViewEntity>>): List<Bar> =
        all.flatMap { data ->
            data.seasons.flatMap { season ->
                season.episodes.filter { it.isWatched && extraViews[it.id].orEmpty().isNotEmpty() }.map { ep ->
                    Bar(
                        label = "${data.series.title} · ${Progress.episodeCode(season.season.number, ep.number)}",
                        value = 1.0 + extraViews.getValue(ep.id).size,
                        detail = ep.title.orEmpty(),
                        seriesId = data.series.id,
                    )
                }
            }
        }.sortedByDescending { it.value }.take(TOP)

    /** "Drama, Krimi" → [Drama, Krimi]. */
    fun genres(genre: String?): List<String> =
        genre?.split(',', '/')?.map { it.trim() }?.filter { it.isNotEmpty() }?.distinct().orEmpty()

    private fun List<EpisodeEntity>.average(): Double = mapNotNull { it.rating }.average()

    private fun localDate(millis: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
}
