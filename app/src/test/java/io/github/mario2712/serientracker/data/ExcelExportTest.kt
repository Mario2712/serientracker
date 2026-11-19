package io.github.mario2712.serientracker.data

import io.github.mario2712.serientracker.data.export.ExcelExport
import io.github.mario2712.serientracker.data.local.EpisodeEntity
import io.github.mario2712.serientracker.data.local.ImportProgressEntity
import io.github.mario2712.serientracker.data.local.SeasonEntity
import io.github.mario2712.serientracker.data.local.SeasonWithEpisodes
import io.github.mario2712.serientracker.data.local.SeriesEntity
import io.github.mario2712.serientracker.data.local.SeriesWithSeasons
import io.github.mario2712.serientracker.domain.ExcelRow
import io.github.mario2712.serientracker.domain.WatchStatus
import io.github.mario2712.serientracker.domain.Watcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class ExcelExportTest {

    private fun day(iso: String) = LocalDate.parse(iso).toEpochDay()

    /** Seasons as (number, episodes, watched count); air dates count up from 2020-01-01. */
    private fun series(
        status: WatchStatus,
        seasons: List<Triple<Int, Int, Int>>,
        nextAirDate: Long? = null,
    ): SeriesWithSeasons {
        var date = day("2020-01-01")
        return SeriesWithSeasons(
            series = SeriesEntity(
                id = 1, title = "Test", status = status, watcher = Watcher.BOTH, genre = "Drama",
                infoUpdatedAt = day("2026-06-16"), nextAirDate = nextAirDate,
            ),
            seasons = seasons.mapIndexed { i, (number, count, watched) ->
                SeasonWithEpisodes(
                    SeasonEntity(id = i + 1L, seriesId = 1, number = number),
                    (1..count).map { n ->
                        EpisodeEntity(
                            seasonId = i + 1L, number = n, airDate = date++,
                            watchedAt = if (n <= watched) 1L else null,
                        )
                    },
                )
            },
            importProgress = null,
        )
    }

    @Test
    fun `status maps back to the excel columns`() {
        fun statusOf(s: WatchStatus) = ExcelExport.toRow(series(s, emptyList())).let { it.status to it.finished }
        assertEquals(0 to 1, statusOf(WatchStatus.COMPLETED))
        assertEquals(1 to 0, statusOf(WatchStatus.WATCHING))
        assertEquals(2 to 0, statusOf(WatchStatus.CAUGHT_UP))
        assertEquals(2 to 0, statusOf(WatchStatus.PAUSED))
        assertEquals(2 to 0, statusOf(WatchStatus.DROPPED))
        assertEquals(0 to 0, statusOf(WatchStatus.PLAN_TO_WATCH))
    }

    @Test
    fun `position and counts come from the episodes`() {
        // S1: 13 watched, S2: 2 of 13 watched, specials: 1 of 2 watched.
        val row = ExcelExport.toRow(
            series(WatchStatus.PAUSED, listOf(Triple(0, 2, 1), Triple(1, 13, 13), Triple(2, 13, 2))),
        )
        assertEquals(2, row.season)
        assertEquals(2, row.episode)
        assertEquals(1, row.special)
        assertEquals(2, row.maxSeason)
        assertEquals(13, row.maxEpisode)
        assertEquals(2, row.maxSpecial)
        assertEquals(16, row.watched)
        assertEquals(28, row.total)
        assertEquals(true, row.unseen)
        assertEquals("B", row.who)
        assertEquals("2026-06-16", row.updated)
        // S2E2 is episode no. 2 + 13 + 2 = 17th by air date → 2020-01-01 + 16 days.
        assertEquals("2020-01-17", row.lastAired)
        // Running show without known next date: latest available episode.
        assertEquals(LocalDate.parse("2020-01-01").plusDays(27).toString(), row.nextAired)
    }

    @Test
    fun `everything watched leaves ungesehen and naechste empty`() {
        val row = ExcelExport.toRow(series(WatchStatus.COMPLETED, listOf(Triple(1, 8, 8))))
        assertNull(row.unseen)
        assertNull(row.nextAired)
        assertEquals(0, row.special)
        assertEquals(0, row.maxSpecial)
    }

    @Test
    fun `planned next air date wins`() {
        val row = ExcelExport.toRow(series(WatchStatus.CAUGHT_UP, listOf(Triple(1, 8, 8)), nextAirDate = day("2027-03-01")))
        assertEquals("2027-03-01", row.nextAired)
    }

    @Test
    fun `not started keeps position and counts empty`() {
        val row = ExcelExport.toRow(series(WatchStatus.PLAN_TO_WATCH, listOf(Triple(1, 24, 0), Triple(2, 22, 0))))
        assertNull(row.season)
        assertNull(row.episode)
        assertNull(row.special)
        assertNull(row.watched)
        assertNull(row.total)
        assertNull(row.maxSpecial)
        assertEquals(2, row.maxSeason)
        assertEquals(22, row.maxEpisode)
        assertEquals(true, row.unseen)
    }

    @Test
    fun `unsynced series export the excel values unchanged`() {
        val data = SeriesWithSeasons(
            series = SeriesEntity(id = 1, title = "12 Monkeys", status = WatchStatus.PAUSED, lastWatchedAirDate = day("2015-01-23")),
            seasons = emptyList(),
            importProgress = ImportProgressEntity(1, 1, 2, 0, 4, 11, 0, 2, 47, null, unseen = true),
        )
        val row = ExcelExport.toRow(data)
        assertEquals(listOf(1, 2, 0, 4, 11, 0, 2, 47), listOf(row.season, row.episode, row.special, row.maxSeason, row.maxEpisode, row.maxSpecial, row.watched, row.total))
        assertEquals("2015-01-23", row.lastAired)
        assertEquals(true, row.unseen)
    }

    @Test
    fun `rows are sorted like the original sheet`() {
        fun r(title: String, status: Int, finished: Int) = ExcelRow(title = title, status = status, finished = finished)
        val sorted = ExcelExport.sort(listOf(r("b", 0, 1), r("z", 0, 0), r("y", 1, 0), r("a", 2, 0), r("A2", 2, 0)))
        assertEquals(listOf("a", "A2", "y", "z", "b"), sorted.map { it.title })
    }
}
