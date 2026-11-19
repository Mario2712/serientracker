package io.github.mario2712.serientracker.data

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.mario2712.serientracker.data.local.AppDatabase
import io.github.mario2712.serientracker.data.local.isWatched
import io.github.mario2712.serientracker.data.remote.TmdbDataSource
import io.github.mario2712.serientracker.data.remote.TmdbEpisode
import io.github.mario2712.serientracker.data.remote.TmdbSearchResult
import io.github.mario2712.serientracker.data.remote.TmdbSeason
import io.github.mario2712.serientracker.data.remote.TmdbShow
import io.github.mario2712.serientracker.data.repository.MatchOutcome
import io.github.mario2712.serientracker.data.repository.SeriesRepository
import io.github.mario2712.serientracker.data.repository.TmdbSyncRepository
import io.github.mario2712.serientracker.data.seed.SeedImporter
import io.github.mario2712.serientracker.domain.WatchStatus
import io.github.mario2712.serientracker.domain.Watcher
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
import java.io.File

/** Seed import + TMDB sync against a real (in-memory) Room database. */
@RunWith(AndroidJUnit4::class)
class SyncFlowTest {

    private lateinit var db: AppDatabase
    private var imported = false

    private val seedJson = """
        {"version":1,"series":[
        {"status":2,"finished":0,"title":"12 Monkeys","who":"M","season":1,"episode":2,"special":0,"maxSeason":4,"maxEpisode":11,"maxSpecial":0,"genre":"Mystery","updated":"2020-01-24","watched":2,"total":47},
        {"status":2,"finished":0,"title":"Black Mirror","who":"B","season":7,"episode":6,"special":2,"maxSeason":7,"maxEpisode":6,"maxSpecial":2,"genre":"Mystery","updated":"2025-07-11","watched":34,"total":34},
        {"status":0,"finished":0,"title":"24","who":null,"maxSeason":8,"maxEpisode":24}
        ]}
    """.trimIndent()

    private val fake = FakeTmdb()

    private fun importer(json: String = seedJson) = SeedImporter(
        db = db,
        readSeed = { json },
        isImported = { imported },
        markImported = { imported = true },
    )

    @Before
    fun setUp() {
        db = AppDatabase.inMemory(ApplicationProvider.getApplicationContext())
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `seed import creates series with excel progress`() = runTest {
        importer().importIfNeeded()
        val overview = SeriesRepository(db).observeOverview().first().associateBy { it.series.title }

        assertEquals(3, overview.size)
        val monkeys = overview.getValue("12 Monkeys")
        assertEquals(WatchStatus.PAUSED, monkeys.series.status)
        assertEquals(Watcher.ME, monkeys.series.watcher)
        assertEquals(2, monkeys.watchedEpisodes)
        assertEquals(47, monkeys.totalEpisodes)
        assertTrue(monkeys.needsSync)
        assertEquals(WatchStatus.CAUGHT_UP, overview.getValue("Black Mirror").series.status)

        // Second start does not import again.
        importer().importIfNeeded()
        assertEquals(3, db.seriesDao().count())
    }

    @Test
    fun `whole bundled seed imports`() = runTest {
        importer(File("src/main/assets/seed/serien.json").readText()).importIfNeeded()
        assertEquals(361, db.seriesDao().count())
        assertEquals(361, db.seriesDao().getUnsynced().size)
    }

    @Test
    fun `tmdb sync ticks episodes up to the excel position`() = runTest {
        importer().importIfNeeded()
        val sync = TmdbSyncRepository(db, fake)
        val monkeys = db.seriesDao().getAll().first { it.title == "12 Monkeys" }

        val outcome = sync.autoMatch(monkeys)

        assertTrue(outcome is MatchOutcome.Matched)
        val report = (outcome as MatchOutcome.Matched).report
        assertEquals(2, report.watched)
        assertEquals(47, report.total)
        assertFalse(report.hasDifference)

        val stored = db.seriesDao().observeSeries(monkeys.id).first()!!
        assertNull(stored.importProgress)
        assertEquals(100, stored.series.tmdbId)
        assertEquals(WatchStatus.PAUSED, stored.series.status)
        // Genre and original title come from TMDB (Excel said "Mystery").
        assertEquals("Sci-Fi & Fantasy, Drama", stored.series.genre)
        assertEquals("12 Monkeys", stored.series.originalTitle)
        val s1 = stored.seasons.first { it.season.number == 1 }.episodes.sortedBy { it.number }
        assertEquals(listOf(true, true, false), s1.take(3).map { it.isWatched })
        assertEquals("Folge 1", s1.first().title)
    }

    @Test
    fun `specials are imported for series that track them and finished shows complete`() = runTest {
        importer().importIfNeeded()
        val sync = TmdbSyncRepository(db, fake)
        val bm = db.seriesDao().getAll().first { it.title == "Black Mirror" }

        val report = sync.link(bm.id, 200)

        assertEquals(34, report.total)
        assertEquals(34, report.watched)
        val stored = db.seriesDao().observeSeries(bm.id).first()!!
        assertEquals(2, stored.seasons.first { it.season.number == 0 }.episodes.size)
        assertEquals(WatchStatus.CAUGHT_UP, stored.series.status)
    }

    @Test
    fun `refresh adds new episodes and keeps watched state`() = runTest {
        importer().importIfNeeded()
        val sync = TmdbSyncRepository(db, fake)
        val monkeys = db.seriesDao().getAll().first { it.title == "12 Monkeys" }
        sync.link(monkeys.id, 100)

        // A genre edited by hand survives later refreshes.
        val linked = db.seriesDao().getSeries(monkeys.id)!!
        db.seriesDao().update(linked.copy(genre = "Zeitreise"))

        fake.extraEpisodes = 3
        val report = sync.refresh(monkeys.id)!!

        assertEquals(50, report.total)
        assertEquals(2, report.watched)
        assertEquals("Zeitreise", db.seriesDao().getSeries(monkeys.id)!!.genre)
    }

    @Test
    fun `wrong season count needs review`() = runTest {
        importer().importIfNeeded()
        val sync = TmdbSyncRepository(db, fake)
        val show24 = db.seriesDao().getAll().first { it.title == "24" }
        fake.seasonsFor24 = 9

        val outcome = sync.autoMatch(show24)

        assertTrue(outcome is MatchOutcome.NeedsReview)
        assertTrue(db.seriesDao().getImportProgress(show24.id) != null)
    }

    /** TMDB stand-in: 12 Monkeys (id 100, 13+13+10+11), Black Mirror (id 200), 24 (id 300). */
    private class FakeTmdb : TmdbDataSource {
        var extraEpisodes = 0
        var seasonsFor24 = 8

        override suspend fun search(query: String): List<TmdbSearchResult> = when (query) {
            "12 Monkeys" -> listOf(TmdbSearchResult(100, "12 Monkeys", "12 Monkeys"))
            "Black Mirror" -> listOf(TmdbSearchResult(200, "Black Mirror", "Black Mirror"))
            "24" -> listOf(TmdbSearchResult(300, "24", "24"))
            else -> emptyList()
        }

        override suspend fun show(id: Int): TmdbShow = when (id) {
            100 -> show(id, "12 Monkeys", listOf(13, 13, 10, 11 + extraEpisodes), specials = 1, status = "Ended")
            // 32 regular episodes over 7 seasons + 2 specials = 34, as in the Excel.
            200 -> show(id, "Black Mirror", listOf(3, 4, 6, 6, 3, 5, 5), specials = 2, status = "Returning Series")
            else -> show(id, "24", List(seasonsFor24) { 24 }, specials = 0, status = "Ended")
        }

        private fun show(id: Int, name: String, perSeason: List<Int>, specials: Int, status: String) = TmdbShow(
            id = id,
            name = name,
            originalName = name,
            status = status,
            posterUrl = null,
            nextAirDate = null,
            genres = listOf("Sci-Fi & Fantasy", "Drama"),
            seasons = buildList {
                if (specials > 0) add(TmdbSeason(0, "Specials", episodes(specials)))
                perSeason.forEachIndexed { i, n -> add(TmdbSeason(i + 1, null, episodes(n))) }
            },
        )

        private fun episodes(n: Int) = (1..n).map { TmdbEpisode(it, "Folge $it", "2015-01-01") }
    }
}
