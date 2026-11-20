package io.github.mario2712.serientracker.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.mario2712.serientracker.data.export.BackupFormatException
import io.github.mario2712.serientracker.data.export.BackupRepository
import io.github.mario2712.serientracker.data.export.ExportRepository
import io.github.mario2712.serientracker.data.local.AppDatabase
import io.github.mario2712.serientracker.data.remote.TmdbDataSource
import io.github.mario2712.serientracker.data.remote.TmdbEpisode
import io.github.mario2712.serientracker.data.remote.TmdbSearchResult
import io.github.mario2712.serientracker.data.remote.TmdbSeason
import io.github.mario2712.serientracker.data.remote.TmdbShow
import io.github.mario2712.serientracker.data.repository.TmdbSyncRepository
import io.github.mario2712.serientracker.data.seed.SeedImporter
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalDate
import java.util.zip.ZipInputStream

@RunWith(AndroidJUnit4::class)
class ExportFlowTest {

    private lateinit var db: AppDatabase
    private val seedFile = File("src/main/assets/seed/serien.json")

    @Before
    fun setUp() {
        db = AppDatabase.inMemory(ApplicationProvider.getApplicationContext())
    }

    @After
    fun tearDown() = db.close()

    private suspend fun importSeed(json: String = seedFile.readText()) {
        var imported = false
        SeedImporter(db, { json }, { imported }, { imported = true }).importIfNeeded()
    }

    /**
     * Writes the Excel export of the untouched seed. CI reads it back with tools/convert_excel.py
     * and compares it with serien.json (tools/check_roundtrip.py): it must be identical.
     */
    @Test
    fun `excel export of the seed is written for the round trip check`() = runTest {
        importSeed()
        val out = File("build/test-output/serienliste_roundtrip.xlsx").apply { parentFile?.mkdirs() }
        out.outputStream().use { ExportRepository(db, "test").exportExcel(it, LocalDate.of(2026, 11, 19)) }

        val entries = ZipInputStream(out.inputStream()).use { zip ->
            generateSequence { zip.nextEntry }.associate { it.name to zip.readBytes().toString(Charsets.UTF_8) }
        }
        assertTrue("[Content_Types].xml" in entries)
        val serien = entries.getValue("xl/worksheets/sheet1.xml")
        assertTrue(serien.contains("<f>Q2/R2</f>"))
        assertTrue(serien.contains("Originaltitel"))
        assertTrue(serien.contains("sqref=\"K1:K1048576\""))
        assertTrue(serien.contains("12 Monkeys"))
        assertEquals(362, Regex("<row ").findAll(serien).count()) // header + 361 series
        val info = entries.getValue("xl/worksheets/sheet2.xml")
        assertTrue(info.contains("DI Mario Siller"))
        assertTrue(entries.getValue("xl/workbook.xml").contains("name=\"Info\""))
    }

    @Test
    fun `excel file name contains the date`() {
        assertEquals("serienliste_20261119.xlsx", ExportRepository.excelFileName(LocalDate.of(2026, 11, 19)))
        assertEquals("serientracker_backup_20261119.json", BackupRepository.fileName(LocalDate.of(2026, 11, 19)))
    }

    @Test
    fun `archive restores the complete state`() = runTest {
        importSeed()
        val sync = TmdbSyncRepository(db, OneShowTmdb())
        val monkeys = db.seriesDao().getAll().first { it.title == "12 Monkeys" }
        sync.link(monkeys.id, 1)
        val before = db.seriesDao().getAllWithSeasons()

        val bytes = ByteArrayOutputStream().also { BackupRepository(db, "test").export(it) }.toByteArray()
        val restoredDb = AppDatabase.inMemory(ApplicationProvider.getApplicationContext())
        try {
            val count = BackupRepository(restoredDb, "test").restore(ByteArrayInputStream(bytes))
            assertEquals(361, count)
            val after = restoredDb.seriesDao().getAllWithSeasons()
            assertEquals(before.size, after.size)
            assertEquals(
                before.sumOf { s -> s.seasons.sumOf { it.episodes.size } },
                after.sumOf { s -> s.seasons.sumOf { it.episodes.size } },
            )
            val m = after.first { it.series.title == "12 Monkeys" }
            assertEquals(47, m.seasons.sumOf { it.episodes.size })
            assertEquals(2, m.seasons.sumOf { s -> s.episodes.count { it.watchedAt != null } })
            assertTrue(m.seasons.flatMap { it.episodes }.filter { it.watchedAt != null }.all { it.watchedAtImported })
            assertEquals(360, after.count { it.importProgress != null })
        } finally {
            restoredDb.close()
        }
    }

    @Test
    fun `broken archive changes nothing`() = runTest {
        importSeed()
        try {
            BackupRepository(db, "test").restore(ByteArrayInputStream("{kaputt".toByteArray()))
            fail("expected BackupFormatException")
        } catch (e: BackupFormatException) {
            // expected
        }
        assertEquals(361, db.seriesDao().count())
    }

    @Test
    fun `migration 2 to 5 keeps data, flags imported watches and marks specials`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-test.db"
        context.deleteDatabase(name)
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name).apply { parentFile?.mkdirs() }, null).use { old ->
            V2_SCHEMA.forEach(old::execSQL)
            old.execSQL("INSERT INTO series (id, title, status, isFavorite, createdAt, updatedAt) VALUES (1, 'X', 'WATCHING', 0, 0, 0)")
            old.execSQL("INSERT INTO seasons (id, seriesId, number) VALUES (1, 1, 1)")
            old.execSQL("INSERT INTO episodes (id, seasonId, number, watchedAt) VALUES (1, 1, 1, 1579820400000)") // midnight → imported
            old.execSQL("INSERT INTO episodes (id, seasonId, number, watchedAt) VALUES (2, 1, 2, 1700000123456)") // real tap
            old.execSQL("INSERT INTO episodes (id, seasonId, number) VALUES (3, 1, 3)")
            old.execSQL("INSERT INTO seasons (id, seriesId, number) VALUES (2, 1, 0)")
            old.execSQL("INSERT INTO episodes (id, seasonId, number) VALUES (4, 2, 1)")
            old.execSQL("INSERT INTO series (id, title, status, isFavorite, createdAt, updatedAt) VALUES (2, 'Y', 'PAUSED', 0, 0, 0)")
            old.execSQL("INSERT INTO import_progress (seriesId, watched, total) VALUES (2, 3, 10)")
            old.version = 2
        }

        val migrated = AppDatabase.build(context, name)
        try {
            val sql = migrated.openHelper.readableDatabase
            val episodes = sql.query("SELECT id, watchedAtImported, kind FROM episodes ORDER BY id").use { c ->
                buildList { while (c.moveToNext()) add(Triple(c.getLong(0), c.getInt(1), c.getString(2))) }
            }
            assertEquals(
                listOf(Triple(1L, 1, "REGULAR"), Triple(2L, 0, "REGULAR"), Triple(3L, 0, "REGULAR"), Triple(4L, 0, "SPECIAL")),
                episodes,
            )
            assertEquals(2, sql.query("SELECT COUNT(*) FROM series").use { it.moveToFirst(); it.getInt(0) })
            // Excel counts of not yet matched series are copied from import_progress.
            sql.query("SELECT excelWatched, excelTotal FROM series WHERE id = 2").use {
                it.moveToFirst()
                assertEquals(3, it.getInt(0))
                assertEquals(10, it.getInt(1))
            }
        } finally {
            migrated.close()
            context.deleteDatabase(name)
        }
    }

    /** 12 Monkeys with 13+13+10+11 episodes. */
    private class OneShowTmdb : TmdbDataSource {
        override suspend fun search(query: String) = listOf(TmdbSearchResult(1, "12 Monkeys", "12 Monkeys"))
        override suspend fun show(id: Int) = TmdbShow(
            id = 1, name = "12 Monkeys", originalName = "12 Monkeys", status = "Ended", posterUrl = null, nextAirDate = null,
            seasons = listOf(13, 13, 10, 11).mapIndexed { i, n ->
                TmdbSeason(i + 1, null, (1..n).map { TmdbEpisode(it, "Folge $it", "2015-01-01") })
            },
        )
    }

    private companion object {
        /** Schema of database version 2, as Room created it. */
        val V2_SCHEMA = listOf(
            """CREATE TABLE IF NOT EXISTS `series` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL,
                `status` TEXT NOT NULL, `rating` INTEGER, `streamingService` TEXT, `notes` TEXT, `isFavorite` INTEGER NOT NULL,
                `watcher` TEXT, `genre` TEXT, `tmdbId` INTEGER, `posterUrl` TEXT, `isEnded` INTEGER, `lastWatchedAirDate` INTEGER,
                `nextAirDate` INTEGER, `infoUpdatedAt` INTEGER, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL)""",
            "CREATE INDEX IF NOT EXISTS `index_series_title` ON `series` (`title`)",
            """CREATE TABLE IF NOT EXISTS `seasons` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `seriesId` INTEGER NOT NULL,
                `number` INTEGER NOT NULL, `title` TEXT,
                FOREIGN KEY(`seriesId`) REFERENCES `series`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )""",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_seasons_seriesId_number` ON `seasons` (`seriesId`, `number`)",
            """CREATE TABLE IF NOT EXISTS `episodes` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `seasonId` INTEGER NOT NULL,
                `number` INTEGER NOT NULL, `title` TEXT, `airDate` INTEGER, `watchedAt` INTEGER,
                FOREIGN KEY(`seasonId`) REFERENCES `seasons`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )""",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_episodes_seasonId_number` ON `episodes` (`seasonId`, `number`)",
            """CREATE TABLE IF NOT EXISTS `import_progress` (`seriesId` INTEGER NOT NULL, `lastSeason` INTEGER,
                `lastEpisode` INTEGER, `lastSpecial` INTEGER, `maxSeason` INTEGER, `maxEpisode` INTEGER, `maxSpecial` INTEGER,
                `watched` INTEGER, `total` INTEGER, `updatedAt` INTEGER, PRIMARY KEY(`seriesId`),
                FOREIGN KEY(`seriesId`) REFERENCES `series`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )""",
        )
    }
}
