package io.github.mario2712.serientracker.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SeriesDao {

    // Episode counts and "next" ignore nothing; the next episode skips specials (season 0).
    // Without episodes, the Excel numbers from import_progress fill in.
    @Query(
        """
        SELECT s.*,
            CASE WHEN ep.total > 0 THEN ep.total ELSE COALESCE(ip.total, 0) END AS totalEpisodes,
            CASE WHEN ep.total > 0 THEN ep.watched ELSE COALESCE(ip.watched, 0) END AS watchedEpisodes,
            (SELECT se.number FROM episodes e JOIN seasons se ON e.seasonId = se.id
                WHERE se.seriesId = s.id AND se.number > 0 AND e.watchedAt IS NULL
                ORDER BY se.number, e.number LIMIT 1) AS nextSeason,
            (SELECT e.number FROM episodes e JOIN seasons se ON e.seasonId = se.id
                WHERE se.seriesId = s.id AND se.number > 0 AND e.watchedAt IS NULL
                ORDER BY se.number, e.number LIMIT 1) AS nextEpisode,
            ip.lastSeason AS excelSeason,
            ip.lastEpisode AS excelEpisode,
            (ip.seriesId IS NOT NULL) AS needsSync
        FROM series s
        LEFT JOIN import_progress ip ON ip.seriesId = s.id
        LEFT JOIN (
            SELECT se.seriesId AS seriesId,
                COUNT(*) AS total,
                SUM(CASE WHEN e.watchedAt IS NOT NULL THEN 1 ELSE 0 END) AS watched
            FROM episodes e JOIN seasons se ON e.seasonId = se.id
            GROUP BY se.seriesId
        ) ep ON ep.seriesId = s.id
        ORDER BY s.updatedAt DESC, s.title COLLATE NOCASE
        """,
    )
    fun observeOverview(): Flow<List<SeriesOverview>>

    @Transaction
    @Query("SELECT * FROM series WHERE id = :id")
    fun observeSeries(id: Long): Flow<SeriesWithSeasons?>

    @Query("SELECT * FROM series WHERE id = :id")
    suspend fun getSeries(id: Long): SeriesEntity?

    @Query("SELECT * FROM series ORDER BY title COLLATE NOCASE")
    suspend fun getAll(): List<SeriesEntity>

    @Query("SELECT COUNT(*) FROM series")
    suspend fun count(): Int

    @Insert
    suspend fun insert(series: SeriesEntity): Long

    @Update
    suspend fun update(series: SeriesEntity)

    @Query("DELETE FROM series WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM series")
    suspend fun deleteAll()

    @Query("UPDATE series SET updatedAt = :timestamp WHERE id = :id")
    suspend fun touch(id: Long, timestamp: Long = System.currentTimeMillis())

    @Insert
    suspend fun insertImportProgress(progress: ImportProgressEntity)

    @Query("SELECT * FROM import_progress WHERE seriesId = :seriesId")
    suspend fun getImportProgress(seriesId: Long): ImportProgressEntity?

    @Query("DELETE FROM import_progress WHERE seriesId = :seriesId")
    suspend fun deleteImportProgress(seriesId: Long)

    /** Series still waiting for the TMDB match, alphabetically. */
    @Query(
        """
        SELECT s.* FROM series s JOIN import_progress ip ON ip.seriesId = s.id
        ORDER BY s.title COLLATE NOCASE
        """,
    )
    suspend fun getUnsynced(): List<SeriesEntity>

    @Query("SELECT COUNT(*) FROM import_progress")
    fun observeUnsyncedCount(): Flow<Int>

    @Query("SELECT * FROM series WHERE tmdbId IS NOT NULL")
    suspend fun getSynced(): List<SeriesEntity>
}
