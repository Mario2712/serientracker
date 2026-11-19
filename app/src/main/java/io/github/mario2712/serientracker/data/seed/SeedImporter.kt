package io.github.mario2712.serientracker.data.seed

import androidx.room.withTransaction
import io.github.mario2712.serientracker.data.local.AppDatabase
import io.github.mario2712.serientracker.data.local.ImportProgressEntity
import io.github.mario2712.serientracker.data.local.SeriesEntity
import io.github.mario2712.serientracker.domain.ExcelMapping
import io.github.mario2712.serientracker.domain.ExcelRow
import io.github.mario2712.serientracker.domain.ExcelSeed
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.ZoneId

/**
 * Loads the Excel list (assets/seed/serien.json, made by tools/convert_excel.py) into the database.
 * Runs once on first start; can be repeated from the settings ("Auf Excel-Stand zurücksetzen").
 */
class SeedImporter(
    private val db: AppDatabase,
    private val readSeed: () -> String,
    private val isImported: suspend () -> Boolean,
    private val markImported: suspend () -> Unit,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun importIfNeeded() {
        if (isImported()) return
        if (db.seriesDao().count() == 0) importAll()
        markImported()
    }

    /** Deletes everything and loads the Excel list again. */
    suspend fun resetToSeed() = db.withTransaction {
        db.seriesDao().deleteAll()
        importAll()
    }

    fun parse(): ExcelSeed = json.decodeFromString(readSeed())

    private suspend fun importAll(): Int = db.withTransaction {
        val rows = parse().series
        rows.forEach { insert(it) }
        rows.size
    }

    private suspend fun insert(row: ExcelRow) {
        val dao = db.seriesDao()
        val updatedMs = ExcelMapping.epochDay(row.updated)
            ?.let { LocalDate.ofEpochDay(it).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() }
        val warnings = ExcelMapping.warnings(row)
        val id = dao.insert(
            SeriesEntity(
                title = row.title.trim(),
                status = ExcelMapping.status(row),
                watcher = ExcelMapping.watcher(row),
                genre = row.genre?.trim()?.takeIf { it.isNotEmpty() },
                notes = warnings.takeIf { it.isNotEmpty() }?.joinToString("\n", prefix = "Excel-Import: "),
                lastWatchedAirDate = ExcelMapping.epochDay(row.lastAired),
                nextAirDate = ExcelMapping.epochDay(row.nextAired),
                infoUpdatedAt = ExcelMapping.epochDay(row.updated),
                createdAt = updatedMs ?: System.currentTimeMillis(),
                // Sorting by updatedAt shows recently touched series first, like in the Excel.
                updatedAt = updatedMs ?: 0L,
            ),
        )
        dao.insertImportProgress(
            ImportProgressEntity(
                seriesId = id,
                lastSeason = row.season,
                lastEpisode = row.episode,
                lastSpecial = row.special,
                maxSeason = row.maxSeason,
                maxEpisode = row.maxEpisode,
                maxSpecial = row.maxSpecial,
                watched = row.watched,
                total = row.total,
                updatedAt = updatedMs,
            ),
        )
    }
}
