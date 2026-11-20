package io.github.mario2712.serientracker.data

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.mario2712.serientracker.data.export.BackupRepository
import io.github.mario2712.serientracker.data.local.AppDatabase
import io.github.mario2712.serientracker.data.local.EpisodeEntity
import io.github.mario2712.serientracker.data.local.EpisodeViewEntity
import io.github.mario2712.serientracker.data.local.SeasonEntity
import io.github.mario2712.serientracker.data.local.SeriesEntity
import io.github.mario2712.serientracker.data.repository.SeriesRepository
import io.github.mario2712.serientracker.data.repository.SyncReport
import io.github.mario2712.serientracker.data.stats.StatsCalculator
import io.github.mario2712.serientracker.domain.EpisodeKind
import io.github.mario2712.serientracker.domain.WatchStatus
import io.github.mario2712.serientracker.domain.Watcher
import io.github.mario2712.serientracker.work.Notifications
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
class RewatchAndDuplicateTest {

    private lateinit var db: AppDatabase
    private var zone = "Europe/Vienna"
    private lateinit var repo: SeriesRepository
    private var seriesId = 0L
    private var seasonId = 0L

    @Before
    fun setUp() = runTest {
        db = AppDatabase.inMemory(ApplicationProvider.getApplicationContext())
        repo = SeriesRepository(db, currentZone = { zone })
        seriesId = db.seriesDao().insert(
            SeriesEntity(title = "Dark", originalTitle = "Dark", status = WatchStatus.WATCHING, tmdbId = 70523, watcher = Watcher.BOTH, genre = "Drama"),
        )
        seasonId = db.episodeDao().insertSeason(SeasonEntity(seriesId = seriesId, number = 1))
        db.episodeDao().insertEpisodes((1..4).map { EpisodeEntity(seasonId = seasonId, number = it, runtime = 60, title = "Folge $it") })
    }

    @After
    fun tearDown() = db.close()

    private suspend fun episode(n: Int) = db.episodeDao().getEpisodes(seasonId).first { it.number == n }

    @Test
    fun `rewatch lifecycle`() = runTest {
        val e1 = episode(1).id
        repo.watchAgain(seriesId, e1) // not watched yet → first viewing
        assertTrue(episode(1).watchedAt != null)
        assertTrue(db.episodeViewDao().forEpisode(e1).isEmpty())

        repo.watchAgain(seriesId, e1)
        repo.watchAgain(seriesId, e1)
        assertEquals(2, db.episodeViewDao().forEpisode(e1).size)
        assertEquals(mapOf(e1 to 2), repo.observeRewatchCounts(seriesId).first())
        // Progress counts the episode once.
        assertEquals(1, db.episodeDao().countWatched(seriesId))

        // Deleting the first viewing moves the earliest further one up.
        val earliest = db.episodeViewDao().forEpisode(e1).first()
        repo.deleteViewing(seriesId, e1, null)
        assertEquals(earliest.watchedAt, episode(1).watchedAt)
        assertEquals(1, db.episodeViewDao().forEpisode(e1).size)

        // Unticking removes all viewings.
        repo.setEpisodeWatched(seriesId, e1, false)
        assertNull(episode(1).watchedAt)
        assertTrue(db.episodeViewDao().forEpisode(e1).isEmpty())
    }

    @Test
    fun `season watched again adds a viewing to every episode`() = runTest {
        repo.setSeasonWatched(seriesId, seasonId, true)
        repo.watchSeasonAgain(seriesId, seasonId)
        assertEquals(4, repo.observeRewatchCounts(seriesId).first().size)
        assertEquals(4, db.episodeDao().countWatched(seriesId))
    }

    @Test
    fun `statistics count every viewing for time, each episode once for progress`() = runTest {
        repo.setSeasonWatched(seriesId, seasonId, true)
        repo.watchAgain(seriesId, episode(1).id)
        val views = db.episodeViewDao().getAll().groupBy { it.episodeId }
        val stats = StatsCalculator.compute(db.seriesDao().getAllWithSeasons(), LocalDate.now(), extraViews = views)
        assertEquals(4, stats.watchedEpisodes)
        assertEquals(5.0, stats.totalHours, 0.001) // 5 viewings × 60 min
        assertEquals(2.0, stats.mostWatchedEpisodes.single().value, 0.0)
    }

    @Test
    fun `viewings are evaluated in the zone where they were ticked`() = runTest {
        // 22:00 in Vienna …
        val at = LocalDateTime.of(2026, 11, 20, 22, 0).atZone(ZoneId.of("Europe/Vienna")).toInstant().toEpochMilli()
        db.episodeDao().setWatched(episode(1).id, at, false, "Europe/Vienna")
        // … and a rewatch at 21:00 in New York.
        val nyAt = LocalDateTime.of(2026, 11, 21, 21, 0).atZone(ZoneId.of("America/New_York")).toInstant().toEpochMilli()
        db.episodeViewDao().insert(EpisodeViewEntity(episodeId = episode(1).id, watchedAt = nyAt, zoneId = "America/New_York"))

        val views = db.episodeViewDao().getAll().groupBy { it.episodeId }
        // Computed on a phone set to New York: the Vienna viewing still counts at 22:00 (block 20–24, Friday).
        val stats = StatsCalculator.compute(db.seriesDao().getAllWithSeasons(), LocalDate.of(2026, 11, 22), ZoneId.of("America/New_York"), views)
        val cells = stats.extras.heatmap.cells
        assertEquals(1, cells[4][5]) // Friday 22:00 Vienna
        assertEquals(1, cells[5][5]) // Saturday 21:00 New York
        assertEquals(2, cells.sumOf { it.sum() })
    }

    @Test
    fun `new ticks store the current zone`() = runTest {
        zone = "America/New_York"
        repo.setEpisodeWatched(seriesId, episode(2).id, true)
        assertEquals("America/New_York", episode(2).watchedZone)
    }

    @Test
    fun `duplicates are found and copied without progress`() = runTest {
        repo.setSeasonWatched(seriesId, seasonId, true)
        db.episodeDao().setRating(episode(1).id, 9)
        db.episodeDao().setKind(episode(4).id, EpisodeKind.SPECIAL)

        assertEquals(listOf(seriesId), repo.findDuplicates(70523, "Irgendwas").map { it.id })
        assertEquals(listOf(seriesId), repo.findDuplicates(null, "dark").map { it.id })
        assertTrue(repo.findDuplicates(null, "Dark", excludeId = seriesId).isEmpty())

        val copyId = repo.duplicateSeries(seriesId, Watcher.ME, "Rewatch 2026")
        val copy = db.seriesDao().observeSeries(copyId).first()!!
        assertEquals("Dark", copy.series.title)
        assertEquals("Rewatch 2026", copy.series.label)
        assertEquals(Watcher.ME, copy.series.watcher)
        assertEquals(70523, copy.series.tmdbId)
        assertEquals(WatchStatus.PLAN_TO_WATCH, copy.series.status)
        val episodes = copy.seasons.single().episodes
        assertEquals(4, episodes.size)
        assertTrue(episodes.all { it.watchedAt == null && it.rating == null })
        assertEquals(EpisodeKind.SPECIAL, episodes.first { it.number == 4 }.kind)
        // The original keeps its progress.
        assertEquals(4, db.episodeDao().countWatched(seriesId))
    }

    @Test
    fun `archive keeps viewings, zones and extra name`() = runTest {
        zone = "Asia/Tokyo"
        repo.setEpisodeWatched(seriesId, episode(1).id, true)
        repo.watchAgain(seriesId, episode(1).id)
        db.seriesDao().update(db.seriesDao().getSeries(seriesId)!!.copy(label = "Mario alleine"))

        val bytes = ByteArrayOutputStream().also { BackupRepository(db, "test").export(it) }.toByteArray()
        val restored = AppDatabase.inMemory(ApplicationProvider.getApplicationContext())
        try {
            BackupRepository(restored, "test").restore(ByteArrayInputStream(bytes))
            val s = restored.seriesDao().getAllWithSeasons().single()
            assertEquals("Mario alleine", s.series.label)
            val ep = s.seasons.single().episodes.first { it.number == 1 }
            assertEquals("Asia/Tokyo", ep.watchedZone)
            val views = restored.episodeViewDao().forEpisode(ep.id)
            assertEquals(1, views.size)
            assertEquals("Asia/Tokyo", views.single().zoneId)
        } finally {
            restored.close()
        }
    }

    @Test
    fun `notifications merge duplicates of the same show`() {
        val (title, lines) = Notifications.content(
            listOf(
                SyncReport(1, "Dark", null, null, 1, 2, listOf("S01E02"), tmdbId = 70523),
                SyncReport(2, "Dark", null, null, 0, 2, listOf("S01E02"), tmdbId = 70523),
            ),
        )
        assertEquals("1 neue Folge", title)
        assertEquals(listOf("Dark (2 Einträge): S01E02"), lines)
        assertFalse(lines.isEmpty())
    }
}
