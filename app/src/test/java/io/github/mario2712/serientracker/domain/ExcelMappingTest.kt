package io.github.mario2712.serientracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExcelMappingTest {

    private fun row(status: Int?, finished: Int?, watched: Int? = null, total: Int? = null) =
        ExcelRow(status = status, finished = finished, title = "X", watched = watched, total = total)

    @Test
    fun `status mapping follows the excel columns`() {
        assertEquals(WatchStatus.COMPLETED, ExcelMapping.status(row(0, 1, 8, 8)))
        assertEquals(WatchStatus.WATCHING, ExcelMapping.status(row(1, 0, 3, 10)))
        assertEquals(WatchStatus.CAUGHT_UP, ExcelMapping.status(row(2, 0, 24, 24)))
        assertEquals(WatchStatus.PAUSED, ExcelMapping.status(row(2, 0, 2, 47)))
        assertEquals(WatchStatus.PAUSED, ExcelMapping.status(row(2, 0, 4, null)))
        assertEquals(WatchStatus.PLAN_TO_WATCH, ExcelMapping.status(row(0, 0)))
        assertEquals(WatchStatus.PLAN_TO_WATCH, ExcelMapping.status(row(null, null)))
    }

    @Test
    fun `finished wins over status`() {
        assertEquals(WatchStatus.COMPLETED, ExcelMapping.status(row(2, 1, 5, 10)))
    }

    @Test
    fun `watcher codes`() {
        assertEquals(Watcher.ME, Watcher.fromExcel("M"))
        assertEquals(Watcher.JOHANNA, Watcher.fromExcel("j"))
        assertEquals(Watcher.BOTH, Watcher.fromExcel(" B "))
        assertNull(Watcher.fromExcel(null))
        assertNull(Watcher.fromExcel("?"))
    }

    @Test
    fun `watcher filter includes series watched together`() {
        assertTrue(WatcherFilter.MINE.accepts(Watcher.BOTH))
        assertTrue(WatcherFilter.JOHANNA.accepts(Watcher.JOHANNA))
        assertEquals(false, WatcherFilter.TOGETHER.accepts(Watcher.ME))
        assertEquals(false, WatcherFilter.MINE.accepts(null))
    }

    @Test
    fun `dates become epoch days`() {
        assertEquals(java.time.LocalDate.of(2015, 1, 23).toEpochDay(), ExcelMapping.epochDay("2015-01-23"))
        assertNull(ExcelMapping.epochDay(null))
        assertNull(ExcelMapping.epochDay("kaputt"))
    }

    @Test
    fun `warnings for inconsistent rows`() {
        val skullIsland = ExcelRow(title = "Skull Island", season = 1, episode = 8, special = 0, watched = 7, total = 8)
        assertEquals(1, ExcelMapping.warnings(skullIsland).size)
        val peaky = ExcelRow(title = "Peaky Blinders", season = 1, episode = 4, watched = 4, total = null)
        assertEquals(listOf("„von“ fehlt"), ExcelMapping.warnings(peaky))
        assertTrue(ExcelMapping.warnings(ExcelRow(title = "ok", season = 1, episode = 2, special = 0, watched = 2, total = 8)).isEmpty())
    }
}
