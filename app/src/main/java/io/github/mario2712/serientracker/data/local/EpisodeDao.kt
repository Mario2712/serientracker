package io.github.mario2712.serientracker.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.github.mario2712.serientracker.domain.EpisodeKind

@Dao
interface EpisodeDao {

    @Query("SELECT * FROM seasons WHERE seriesId = :seriesId ORDER BY number")
    suspend fun getSeasons(seriesId: Long): List<SeasonEntity>

    @Insert
    suspend fun insertSeason(season: SeasonEntity): Long

    @Query("UPDATE seasons SET title = :title WHERE id = :seasonId")
    suspend fun setSeasonTitle(seasonId: Long, title: String?)

    @Query("SELECT * FROM episodes WHERE seasonId = :seasonId ORDER BY number")
    suspend fun getEpisodes(seasonId: Long): List<EpisodeEntity>

    @Query("SELECT * FROM episodes WHERE id = :episodeId")
    suspend fun getEpisode(episodeId: Long): EpisodeEntity?

    /** TMDB info for an episode from TMDB; custom specials are never touched. */
    @Query(
        """
        UPDATE episodes SET title = :title, airDate = :airDate, runtime = COALESCE(:runtime, runtime)
        WHERE id = :episodeId AND isCustom = 0
        """,
    )
    suspend fun setEpisodeInfo(episodeId: Long, title: String?, airDate: Long?, runtime: Int?)

    @Query("DELETE FROM seasons WHERE seriesId = :seriesId AND number > :maxNumber")
    suspend fun deleteSeasonsAbove(seriesId: Long, maxNumber: Int)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEpisodes(episodes: List<EpisodeEntity>)

    @Insert
    suspend fun insertEpisodesStrict(episodes: List<EpisodeEntity>): List<Long>

    @Insert
    suspend fun insertEpisode(episode: EpisodeEntity): Long

    @Query("DELETE FROM episodes WHERE id = :episodeId AND isCustom = 1")
    suspend fun deleteCustomEpisode(episodeId: Long)

    @Query("SELECT COALESCE(MAX(number), 0) FROM episodes WHERE seasonId = :seasonId")
    suspend fun maxEpisodeNumber(seasonId: Long): Int

    @Query("DELETE FROM episodes WHERE seasonId = :seasonId AND number > :maxNumber")
    suspend fun deleteEpisodesAbove(seasonId: Long, maxNumber: Int)

    /**
     * Ticking by hand. [imported] = true means "watched earlier, no exact date" (catch-up mode);
     * such episodes are left out of the daily statistics.
     */
    @Query(
        """
        UPDATE episodes SET watchedAt = :watchedAt, watchedAtImported = :imported,
            watchedZone = CASE WHEN :watchedAt IS NULL THEN NULL ELSE :zone END
        WHERE id = :episodeId
        """,
    )
    suspend fun setWatched(episodeId: Long, watchedAt: Long?, imported: Boolean = false, zone: String? = HOME_ZONE)

    /** All real (exact) first viewings, for finding catch-up days. */
    @Query("SELECT id, watchedAt, watchedZone FROM episodes WHERE watchedAt IS NOT NULL AND watchedAtImported = 0")
    suspend fun realWatchMoments(): List<WatchMoment>

    @Query("UPDATE episodes SET watchedAtImported = 1 WHERE id IN (:ids)")
    suspend fun markImported(ids: List<Long>)

    @Query("UPDATE episodes SET rating = :rating WHERE id = :episodeId")
    suspend fun setRating(episodeId: Long, rating: Int?)

    @Query("UPDATE episodes SET kind = :kind WHERE id = :episodeId")
    suspend fun setKind(episodeId: Long, kind: EpisodeKind)

    /** Ticks the unwatched, counting episodes of a season; already watched ones keep their date. */
    @Query(
        """
        UPDATE episodes SET watchedAt = :watchedAt, watchedAtImported = :imported, watchedZone = :zone
        WHERE seasonId = :seasonId AND watchedAt IS NULL AND kind != 'HIDDEN'
        """,
    )
    suspend fun markSeasonWatched(seasonId: Long, watchedAt: Long, imported: Boolean = false, zone: String = HOME_ZONE)

    /** Counting, already watched episodes of a season (for "Staffel nochmal gesehen"). */
    @Query("SELECT * FROM episodes WHERE seasonId = :seasonId AND kind != 'HIDDEN' ORDER BY number")
    suspend fun countingEpisodes(seasonId: Long): List<EpisodeEntity>

    @Query("UPDATE episodes SET watchedAt = NULL, watchedAtImported = 0, watchedZone = NULL WHERE seasonId = :seasonId")
    suspend fun clearSeasonWatched(seasonId: Long)

    @Query(
        """
        UPDATE episodes SET watchedAt = :watchedAt, watchedAtImported = :imported, watchedZone = :zone
        WHERE watchedAt IS NULL AND kind != 'HIDDEN'
            AND seasonId IN (SELECT id FROM seasons WHERE seriesId = :seriesId)
        """,
    )
    suspend fun markAllWatched(seriesId: Long, watchedAt: Long, imported: Boolean = false, zone: String = HOME_ZONE)

    @Query(
        """
        SELECT e.* FROM episodes e JOIN seasons se ON e.seasonId = se.id
        WHERE se.seriesId = :seriesId AND e.kind = 'REGULAR' AND e.watchedAt IS NULL
        ORDER BY se.number, e.number LIMIT 1
        """,
    )
    suspend fun nextUnwatched(seriesId: Long): EpisodeEntity?

    @Query(
        """
        SELECT COUNT(*) FROM episodes e JOIN seasons se ON e.seasonId = se.id
        WHERE se.seriesId = :seriesId AND e.kind != 'HIDDEN'
        """,
    )
    suspend fun countEpisodes(seriesId: Long): Int

    @Query(
        """
        SELECT COUNT(*) FROM episodes e JOIN seasons se ON e.seasonId = se.id
        WHERE se.seriesId = :seriesId AND e.kind != 'HIDDEN' AND e.watchedAt IS NOT NULL
        """,
    )
    suspend fun countWatched(seriesId: Long): Int
}
