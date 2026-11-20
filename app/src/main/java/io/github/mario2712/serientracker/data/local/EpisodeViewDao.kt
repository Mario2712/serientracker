package io.github.mario2712.serientracker.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.MapColumn
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Further viewings of episodes (rewatches). The first viewing lives in the episode itself. */
@Dao
interface EpisodeViewDao {

    @Insert
    suspend fun insert(view: EpisodeViewEntity): Long

    @Insert
    suspend fun insertAll(views: List<EpisodeViewEntity>)

    @Query("SELECT * FROM episode_views WHERE episodeId = :episodeId ORDER BY watchedAt")
    suspend fun forEpisode(episodeId: Long): List<EpisodeViewEntity>

    @Query("SELECT * FROM episode_views WHERE episodeId = :episodeId ORDER BY watchedAt")
    fun observeForEpisode(episodeId: Long): Flow<List<EpisodeViewEntity>>

    /** Episode id → number of further viewings, for the "2×" badges of one series. */
    @Query(
        """
        SELECT v.episodeId AS episodeId, COUNT(*) AS cnt FROM episode_views v
        JOIN episodes e ON v.episodeId = e.id JOIN seasons se ON e.seasonId = se.id
        WHERE se.seriesId = :seriesId GROUP BY v.episodeId
        """,
    )
    fun observeCountsForSeries(seriesId: Long): Flow<Map<@MapColumn("episodeId") Long, @MapColumn("cnt") Int>>

    @Query("SELECT * FROM episode_views")
    suspend fun getAll(): List<EpisodeViewEntity>

    @Query("DELETE FROM episode_views WHERE id = :viewId")
    suspend fun delete(viewId: Long)

    @Query("DELETE FROM episode_views WHERE episodeId = :episodeId")
    suspend fun deleteForEpisode(episodeId: Long)

    @Query("DELETE FROM episode_views WHERE episodeId IN (SELECT id FROM episodes WHERE seasonId = :seasonId)")
    suspend fun deleteForSeason(seasonId: Long)

    @Query("UPDATE episode_views SET watchedAt = :watchedAt, imported = 0, zoneId = :zoneId WHERE id = :viewId")
    suspend fun updateTime(viewId: Long, watchedAt: Long, zoneId: String)

    /** Real further viewings, for finding catch-up days. */
    @Query("SELECT * FROM episode_views WHERE imported = 0")
    suspend fun realViews(): List<EpisodeViewEntity>

    @Query("UPDATE episode_views SET imported = 1 WHERE id IN (:ids)")
    suspend fun markImported(ids: List<Long>)
}
