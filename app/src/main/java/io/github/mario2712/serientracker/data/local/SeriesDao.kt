package io.github.mario2712.serientracker.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SeriesDao {

    @Query(
        """
        SELECT s.*,
            (SELECT COUNT(*) FROM episodes e JOIN seasons se ON e.seasonId = se.id
                WHERE se.seriesId = s.id) AS totalEpisodes,
            (SELECT COUNT(*) FROM episodes e JOIN seasons se ON e.seasonId = se.id
                WHERE se.seriesId = s.id AND e.watchedAt IS NOT NULL) AS watchedEpisodes,
            (SELECT se.number FROM episodes e JOIN seasons se ON e.seasonId = se.id
                WHERE se.seriesId = s.id AND e.watchedAt IS NULL
                ORDER BY se.number, e.number LIMIT 1) AS nextSeason,
            (SELECT e.number FROM episodes e JOIN seasons se ON e.seasonId = se.id
                WHERE se.seriesId = s.id AND e.watchedAt IS NULL
                ORDER BY se.number, e.number LIMIT 1) AS nextEpisode
        FROM series s
        ORDER BY s.updatedAt DESC
        """,
    )
    fun observeOverview(): Flow<List<SeriesOverview>>

    @Transaction
    @Query("SELECT * FROM series WHERE id = :id")
    fun observeSeries(id: Long): Flow<SeriesWithSeasons?>

    @Query("SELECT * FROM series WHERE id = :id")
    suspend fun getSeries(id: Long): SeriesEntity?

    @Insert
    suspend fun insert(series: SeriesEntity): Long

    @Update
    suspend fun update(series: SeriesEntity)

    @Query("DELETE FROM series WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE series SET updatedAt = :timestamp WHERE id = :id")
    suspend fun touch(id: Long, timestamp: Long = System.currentTimeMillis())
}
