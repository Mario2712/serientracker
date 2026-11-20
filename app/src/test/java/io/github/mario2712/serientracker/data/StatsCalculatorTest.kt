package io.github.mario2712.serientracker.data

import io.github.mario2712.serientracker.data.local.EpisodeEntity
import io.github.mario2712.serientracker.data.local.ImportProgressEntity
import io.github.mario2712.serientracker.data.local.SeasonEntity
import io.github.mario2712.serientracker.data.local.SeasonWithEpisodes
import io.github.mario2712.serientracker.data.local.SeriesEntity
import io.github.mario2712.serientracker.data.local.SeriesWithSeasons
import io.github.mario2712.serientracker.data.stats.StatsCalculator
import io.github.mario2712.serientracker.domain.EpisodeKind
import io.github.mario2712.serientracker.domain.WatchStatus
import io.github.mario2712.serientracker.domain.Watcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class StatsCalculatorTest {

    private val zone = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 11, 20)
    private fun at(date: String, hour: Int = 20) =
        LocalDate.parse(date).atTime(hour, 0).toInstant(ZoneOffset.UTC).toEpochMilli()

    private fun ep(n: Int, watchedAt: Long? = null, rating: Int? = null, runtime: Int? = 30, imported: Boolean = false, kind: EpisodeKind = EpisodeKind.REGULAR) =
        EpisodeEntity(seasonId = 0, number = n, watchedAt = watchedAt, rating = rating, runtime = runtime, watchedAtImported = imported, kind = kind)

    private fun series(id: Long, title: String, genre: String?, watcher: Watcher?, status: WatchStatus, rating: Int? = null, vararg seasons: Pair<Int, List<EpisodeEntity>>) =
        SeriesWithSeasons(
            series = SeriesEntity(id = id, title = title, genre = genre, watcher = watcher, status = status, rating = rating, excelWatched = null, excelTotal = null),
            seasons = seasons.map { (n, eps) -> SeasonWithEpisodes(SeasonEntity(id = id * 10 + n, seriesId = id, number = n), eps) },
            importProgress = null,
        )

    private val data = listOf(
        // Drama/Krimi, 3 of 4 watched on one day (binge), ratings 9, 7 and 8 → season Ø 8.
        series(
            1, "Dark", "Drama, Krimi", Watcher.BOTH, WatchStatus.WATCHING, null,
            1 to listOf(
                ep(1, at("2026-11-01"), 9), ep(2, at("2026-11-01", 21), 7), ep(3, at("2026-11-01", 22), 8), ep(4),
                ep(5, kind = EpisodeKind.HIDDEN),
            ),
        ),
        // Comedy, own series rating 6; watched long ago from Excel (imported); runtime unknown for one → median.
        series(
            2, "Community", "Comedy", Watcher.ME, WatchStatus.PAUSED, 6,
            1 to listOf(ep(1, at("2020-01-24"), imported = true), ep(2, at("2020-01-24"), imported = true, runtime = null), ep(3), ep(4)),
        ),
        // Not matched yet: Excel numbers only.
        SeriesWithSeasons(
            series = SeriesEntity(id = 3, title = "24", genre = "Action", watcher = Watcher.ME, status = WatchStatus.PLAN_TO_WATCH),
            seasons = emptyList(),
            importProgress = ImportProgressEntity(3, null, null, null, 8, 24, null, 0, 192, null),
        ),
    )

    private val stats = StatsCalculator.compute(data, today, zone)

    @Test
    fun `overview counts hidden episodes nowhere`() {
        assertEquals(3, stats.seriesCount)
        assertEquals(5, stats.watchedEpisodes)
        // 5 watched × 30 min (the one without runtime uses the median 30) = 2.5 h
        assertEquals(2.5, stats.totalHours, 0.001)
        assertEquals(80, stats.runtimeCoverage)
        assertEquals(8.0, stats.avgEpisodeRating!!, 0.001)
    }

    @Test
    fun `genres are split and ranked by watched episodes`() {
        assertEquals(listOf("Drama", "Krimi", "Comedy", "Action"), stats.genresByEpisodes.map { it.label })
        assertEquals(3.0, stats.genresByEpisodes.first().value, 0.0)
        assertEquals(listOf("Drama", "Krimi", "Comedy"), stats.genresByHours.map { it.label })
    }

    @Test
    fun `top ratings use own series rating or episode average`() {
        assertEquals(9.0, stats.topEpisodes.first().rating, 0.0)
        assertTrue(stats.topEpisodes.first().title.contains("S01E01"))
        assertEquals(1, stats.topSeasons.size)
        assertEquals(8.0, stats.topSeasons.first().rating, 0.001)
        assertEquals(listOf("Dark", "Community"), stats.topSeries.map { it.title })
        assertEquals("eigene Bewertung", stats.topSeries[1].subtitle)
    }

    @Test
    fun `timeline uses only real watch dates`() {
        assertEquals(12, stats.perMonth.size)
        assertEquals("11/26", stats.perMonth.last().label)
        assertEquals(3.0, stats.perMonth.last().value, 0.0)
        assertEquals(LocalDate.of(2026, 11, 1), stats.mostActiveDay!!.date)
        assertEquals(3, stats.longestBinge!!.episodes)
        assertEquals("Dark", stats.longestBinge!!.title)
        assertEquals(listOf("2026", "vor dem Abgleich"), stats.hoursPerYear.map { it.label })
    }

    @Test
    fun `watchers, status and pile of shame`() {
        assertEquals(2.0, stats.watcherSeries.first { it.label == "Ich" }.value, 0.0)
        assertEquals(3.0, stats.watcherEpisodes.first { it.label == "Beide" }.value, 0.0)
        assertEquals("24", stats.pileOfShame.first().label)
        assertEquals(192.0, stats.pileOfShame.first().value, 0.0)
        assertEquals(3, stats.statusCounts.size)
    }

    @Test
    fun `no deviations without excel numbers`() {
        assertTrue(stats.deviations.isEmpty())
        val withExcel = data[0].copy(series = data[0].series.copy(excelWatched = 3, excelTotal = 5))
        val dev = StatsCalculator.compute(listOf(withExcel), today, zone).deviations.single()
        assertEquals(5, dev.excelTotal)
        assertEquals(4, dev.total)
    }

    @Test
    fun `empty data gives empty stats`() {
        val empty = StatsCalculator.compute(emptyList(), today, zone)
        assertEquals(0, empty.watchedEpisodes)
        assertNull(empty.avgEpisodeRating)
        assertNull(empty.longestBinge)
        assertEquals(12, empty.perMonth.size)
    }

    @Test
    fun `genre text is split on comma and slash`() {
        assertEquals(listOf("Sci-Fi & Fantasy", "Drama"), StatsCalculator.genres("Sci-Fi & Fantasy, Drama"))
        assertEquals(listOf("Action", "Mystery"), StatsCalculator.genres("Action/ Mystery"))
        assertTrue(StatsCalculator.genres(null).isEmpty())
    }
}
