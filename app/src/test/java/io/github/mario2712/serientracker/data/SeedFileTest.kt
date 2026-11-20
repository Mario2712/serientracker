package io.github.mario2712.serientracker.data

import io.github.mario2712.serientracker.domain.ExcelMapping
import io.github.mario2712.serientracker.domain.ExcelSeed
import io.github.mario2712.serientracker.domain.WatchStatus
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Checks the bundled Excel seed (app/src/main/assets/seed/serien.json). */
class SeedFileTest {

    private val seed: ExcelSeed = Json { ignoreUnknownKeys = true }
        .decodeFromString(File("src/main/assets/seed/serien.json").readText())

    @Test
    fun `seed contains the whole excel list`() {
        assertEquals(361, seed.series.size)
    }

    @Test
    fun `status counts match the excel`() {
        val counts = seed.series.groupingBy { ExcelMapping.status(it) }.eachCount()
        assertEquals(147, counts[WatchStatus.COMPLETED])
        assertEquals(6, counts[WatchStatus.WATCHING])
        assertEquals(59, counts[WatchStatus.PLAN_TO_WATCH])
        assertEquals(55, counts[WatchStatus.CAUGHT_UP])
        assertEquals(94, counts[WatchStatus.PAUSED])
    }

    @Test
    fun `numeric titles are kept as text`() {
        val titles = seed.series.map { it.title }.toSet()
        assertTrue("24" in titles && "1899" in titles)
    }
}
