package io.github.mario2712.serientracker.data

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.mario2712.serientracker.data.local.AppDatabase
import io.github.mario2712.serientracker.data.local.EpisodeEntity
import io.github.mario2712.serientracker.data.local.SeasonEntity
import io.github.mario2712.serientracker.data.local.SeriesEntity
import io.github.mario2712.serientracker.data.repository.SeriesRepository
import io.github.mario2712.serientracker.data.stats.StatsCalculator
import io.github.mario2712.serientracker.domain.WatchStatus
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.ZoneOffset

/** Catch-up mode and correcting a catch-up day, against a real (in-memory) Room database. */
@RunWith(AndroidJUnit4::class)
class CatchUpTest {

    private lateinit var db: AppDatabase
    private var catchUp = false
    private lateinit var repo: SeriesRepository
    private var seriesId = 0L
    private var seasonId = 0L

    @Before
    fun setUp() = runTest {
        db = AppDatabase.inMemory(ApplicationProvider.getApplicationContext())
        repo = SeriesRepository(db, catchUpMode = { catchUp })
        seriesId = db.seriesDao().insert(SeriesEntity(title = "Test", status = WatchStatus.WATCHING))
        seasonId = db.episodeDao().insertSeason(SeasonEntity(seriesId = seriesId, number = 1))
        db.episodeDao().insertEpisodes((1..6).map { EpisodeEntity(seasonId = seasonId, number = it, runtime = 30) })
    }

    @After
    fun tearDown() = db.close()

    private suspend fun episode(n: Int) = db.episodeDao().getEpisodes(seasonId).first { it.number == n }

    @Test
    fun `without catch-up mode ticks are real`() = runTest {
        repo.setEpisodeWatched(seriesId, episode(1).id, true)
        assertFalse(episode(1).watchedAtImported)
    }

    @Test
    fun `catch-up mode marks every way of ticking as watched earlier`() = runTest {
        catchUp = true
        repo.setEpisodeWatched(seriesId, episode(1).id, true)
        repo.watchNextEpisode(seriesId) // episode 2
        repo.setEpisodeRating(seriesId, episode(3).id, 8)
        assertTrue(episode(1).watchedAtImported)
        assertTrue(episode(2).watchedAtImported)
        assertTrue(episode(3).watchedAtImported)

        repo.setSeasonWatched(seriesId, seasonId, true)
        assertTrue(db.episodeDao().getEpisodes(seasonId).all { it.watchedAtImported && it.watchedAt != null })

        // A date chosen on purpose is a real watch moment, even in catch-up mode.
        repo.setEpisodeWatchedAt(seriesId, episode(4).id, 1_700_000_000_000)
        assertFalse(episode(4).watchedAtImported)

        // Catch-up ticks stay out of the daily statistics but count for the totals.
        val all = db.seriesDao().getAllWithSeasons()
        val stats = StatsCalculator.compute(all, LocalDate.now(), ZoneOffset.UTC)
        assertEquals(6, stats.watchedEpisodes)
        assertEquals(3.0, stats.totalHours, 0.001)
        assertEquals(1, stats.extras.heatmap.cells.sumOf { it.sum() }) // only episode 4
    }

    @Test
    fun `a day can be corrected afterwards`() = runTest {
        val zone = ZoneOffset.UTC
        val day = LocalDate.of(2026, 11, 20)
        fun at(d: LocalDate, hour: Int) = d.atTime(hour, 0).toInstant(zone).toEpochMilli()
        // 4 ticks on the catch-up day, 1 the day before.
        listOf(1, 2, 3, 4).forEach { db.episodeDao().setWatched(episode(it).id, at(day, 10 + it)) }
        db.episodeDao().setWatched(episode(5).id, at(day.minusDays(1), 21))

        // Stored with the home zone (Europe/Vienna): 11–14 h UTC is still the same day there.
        assertEquals(listOf(day to 4, day.minusDays(1) to 1), repo.busiestDays())

        val changed = repo.markDayAsCatchUp(day)

        assertEquals(4, changed)
        assertTrue((1..4).all { n -> db.episodeDao().getEpisodes(seasonId).first { it.number == n }.watchedAtImported })
        assertFalse(episode(5).watchedAtImported)
        assertEquals(listOf(day.minusDays(1) to 1), repo.busiestDays())

        val stats = StatsCalculator.compute(db.seriesDao().getAllWithSeasons(), day, zone)
        assertEquals(1, stats.longestBinge!!.episodes)
        assertEquals(day.minusDays(1), stats.mostActiveDay!!.date)
        assertEquals(5, stats.watchedEpisodes)
        assertNull(stats.extras.years.firstOrNull { it.episodes > 1 })
    }
}
