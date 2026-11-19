package io.github.mario2712.serientracker.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ProgressTest {

    @Test
    fun `all episodes watched completes the series`() {
        assertEquals(WatchStatus.COMPLETED, Progress.autoStatus(WatchStatus.WATCHING, 10, 10))
        assertEquals(WatchStatus.COMPLETED, Progress.autoStatus(WatchStatus.PAUSED, 10, 10))
    }

    @Test
    fun `first watched episode starts a planned series`() {
        assertEquals(WatchStatus.WATCHING, Progress.autoStatus(WatchStatus.PLAN_TO_WATCH, 1, 10))
    }

    @Test
    fun `unwatching reopens a completed series`() {
        assertEquals(WatchStatus.WATCHING, Progress.autoStatus(WatchStatus.COMPLETED, 9, 10))
    }

    @Test
    fun `paused and dropped are kept while unfinished`() {
        assertEquals(WatchStatus.PAUSED, Progress.autoStatus(WatchStatus.PAUSED, 3, 10))
        assertEquals(WatchStatus.DROPPED, Progress.autoStatus(WatchStatus.DROPPED, 3, 10))
    }

    @Test
    fun `series without episodes keeps its status`() {
        assertEquals(WatchStatus.PLAN_TO_WATCH, Progress.autoStatus(WatchStatus.PLAN_TO_WATCH, 0, 0))
        assertEquals(WatchStatus.COMPLETED, Progress.autoStatus(WatchStatus.COMPLETED, 0, 0))
    }

    @Test
    fun `episode code is zero padded`() {
        assertEquals("S01E05", Progress.episodeCode(1, 5))
        assertEquals("S12E103", Progress.episodeCode(12, 103))
    }
}
