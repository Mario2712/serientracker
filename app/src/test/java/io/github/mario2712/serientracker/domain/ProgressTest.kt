package io.github.mario2712.serientracker.domain

import io.github.mario2712.serientracker.domain.Progress.Change
import io.github.mario2712.serientracker.domain.WatchStatus.CAUGHT_UP
import io.github.mario2712.serientracker.domain.WatchStatus.COMPLETED
import io.github.mario2712.serientracker.domain.WatchStatus.DROPPED
import io.github.mario2712.serientracker.domain.WatchStatus.PAUSED
import io.github.mario2712.serientracker.domain.WatchStatus.PLAN_TO_WATCH
import io.github.mario2712.serientracker.domain.WatchStatus.WATCHING
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressTest {

    @Test
    fun `everything watched of a running show means caught up`() {
        assertEquals(CAUGHT_UP, Progress.autoStatus(WATCHING, 10, 10, Change.WATCHED))
        assertEquals(CAUGHT_UP, Progress.autoStatus(PAUSED, 10, 10, Change.NEW_EPISODES, isEnded = false))
    }

    @Test
    fun `everything watched of an ended show completes it`() {
        assertEquals(COMPLETED, Progress.autoStatus(WATCHING, 10, 10, Change.WATCHED, isEnded = true))
        assertEquals(COMPLETED, Progress.autoStatus(CAUGHT_UP, 10, 10, Change.NEW_EPISODES, isEnded = true))
    }

    @Test
    fun `completed stays completed when all is watched`() {
        assertEquals(COMPLETED, Progress.autoStatus(COMPLETED, 8, 8, Change.NEW_EPISODES, isEnded = false))
    }

    @Test
    fun `ticking an episode starts watching`() {
        assertEquals(WATCHING, Progress.autoStatus(PLAN_TO_WATCH, 1, 10, Change.WATCHED))
        assertEquals(WATCHING, Progress.autoStatus(PAUSED, 4, 10, Change.WATCHED))
        assertEquals(WATCHING, Progress.autoStatus(CAUGHT_UP, 9, 10, Change.WATCHED))
        assertEquals(DROPPED, Progress.autoStatus(DROPPED, 4, 10, Change.WATCHED))
    }

    @Test
    fun `new episodes pause a caught up or completed show`() {
        assertEquals(PAUSED, Progress.autoStatus(CAUGHT_UP, 10, 12, Change.NEW_EPISODES))
        assertEquals(PAUSED, Progress.autoStatus(COMPLETED, 10, 12, Change.NEW_EPISODES))
        assertEquals(PLAN_TO_WATCH, Progress.autoStatus(PLAN_TO_WATCH, 0, 12, Change.NEW_EPISODES))
        assertEquals(WATCHING, Progress.autoStatus(WATCHING, 3, 12, Change.NEW_EPISODES))
    }

    @Test
    fun `unticking reopens a finished show`() {
        assertEquals(WATCHING, Progress.autoStatus(COMPLETED, 9, 10, Change.UNWATCHED))
        assertEquals(WATCHING, Progress.autoStatus(CAUGHT_UP, 9, 10, Change.UNWATCHED))
        assertEquals(PAUSED, Progress.autoStatus(PAUSED, 2, 10, Change.UNWATCHED))
    }

    @Test
    fun `series without episodes keeps its status`() {
        assertEquals(PLAN_TO_WATCH, Progress.autoStatus(PLAN_TO_WATCH, 0, 0, Change.NEW_EPISODES))
        assertEquals(COMPLETED, Progress.autoStatus(COMPLETED, 0, 0, Change.UNWATCHED))
    }

    @Test
    fun `episode code is zero padded and names specials`() {
        assertEquals("S01E05", Progress.episodeCode(1, 5))
        assertEquals("S12E103", Progress.episodeCode(12, 103))
        assertEquals("Special 2", Progress.episodeCode(0, 2))
    }

    @Test
    fun `excel position marks earlier seasons and specials as watched`() {
        // 12 Monkeys in the Excel: last watched S01E02, no specials.
        assertTrue(Progress.isWatchedAtPosition(1, 2, 1, 2, 0))
        assertFalse(Progress.isWatchedAtPosition(1, 3, 1, 2, 0))
        assertFalse(Progress.isWatchedAtPosition(2, 1, 1, 2, 0))
        // Black Mirror: S07E06 and special 2.
        assertTrue(Progress.isWatchedAtPosition(3, 6, 7, 6, 2))
        assertTrue(Progress.isWatchedAtPosition(0, 2, 7, 6, 2))
        assertFalse(Progress.isWatchedAtPosition(0, 3, 7, 6, 2))
        // Not started.
        assertFalse(Progress.isWatchedAtPosition(1, 1, null, null, null))
    }
}
