package io.github.mario2712.serientracker.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface EpisodeDao {

    @Query("SELECT * FROM seasons WHERE seriesId = :seriesId ORDER BY number")
    suspend fun getSeasons(seriesId: Long): List<SeasonEntity>

    @Insert
    suspend fun insertSeason(season: SeasonEntity): Long

    @Query("DELETE FROM seasons WHERE seriesId = :seriesId AND number > :maxNumber")
    suspend fun deleteSeasonsAbove(seriesId: Long, maxNumber: Int)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEpisodes(episodes: List<EpisodeEntity>)

    @Query("SELECT COALESCE(MAX(number), 0) FROM episodes WHERE seasonId = :seasonId")
    suspend fun maxEpisodeNumber(seasonId: Long): Int

    @Query("DELETE FROM episodes WHERE seasonId = :seasonId AND number > :maxNumber")
    suspend fun deleteEpisodesAbove(seasonId: Long, maxNumber: Int)

    @Query("UPDATE episodes SET watchedAt = :watchedAt WHERE id = :episodeId")
    suspend fun setWatched(episodeId: Long, watchedAt: Long?)

    @Query("UPDATE episodes SET watchedAt = :watchedAt WHERE seasonId = :seasonId")
    suspend fun setSeasonWatched(seasonId: Long, watchedAt: Long?)

    @Query(
        """
        UPDATE episodes SET watchedAt = :watchedAt
        WHERE watchedAt IS NULL AND seasonId IN (SELECT id FROM seasons WHERE seriesId = :seriesId)
        """,
    )
    suspend fun markAllWatched(seriesId: Long, watchedAt: Long)

    @Query(
        """
        SELECT e.* FROM episodes e JOIN seasons se ON e.seasonId = se.id
        WHERE se.seriesId = :seriesId AND e.watchedAt IS NULL
        ORDER BY se.number, e.number LIMIT 1
        """,
    )
    suspend fun nextUnwatched(seriesId: Long): EpisodeEntity?

    @Query(
        """
        SELECT COUNT(*) FROM episodes e JOIN seasons se ON e.seasonId = se.id
        WHERE se.seriesId = :seriesId
        """,
    )
    suspend fun countEpisodes(seriesId: Long): Int

    @Query(
        """
        SELECT COUNT(*) FROM episodes e JOIN seasons se ON e.seasonId = se.id
        WHERE se.seriesId = :seriesId AND e.watchedAt IS NOT NULL
        """,
    )
    suspend fun countWatched(seriesId: Long): Int
}
