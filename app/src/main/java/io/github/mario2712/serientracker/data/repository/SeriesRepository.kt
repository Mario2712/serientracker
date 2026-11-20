package io.github.mario2712.serientracker.data.repository

import androidx.room.withTransaction
import io.github.mario2712.serientracker.data.local.AppDatabase
import io.github.mario2712.serientracker.data.local.CUSTOM_EPISODE_START
import io.github.mario2712.serientracker.data.local.DataStats
import io.github.mario2712.serientracker.domain.EpisodeKind
import io.github.mario2712.serientracker.data.local.EpisodeEntity
import io.github.mario2712.serientracker.data.local.SeasonEntity
import io.github.mario2712.serientracker.data.local.SeriesEntity
import io.github.mario2712.serientracker.data.local.SeriesOverview
import io.github.mario2712.serientracker.data.local.SeriesWithSeasons
import io.github.mario2712.serientracker.domain.Progress
import io.github.mario2712.serientracker.domain.WatchStatus
import kotlinx.coroutines.flow.Flow

/**
 * Single entry point for the UI to read and change data.
 * Later sources (TMDB, Excel import, backup) plug in here, not in the ViewModels.
 */
class SeriesRepository(private val db: AppDatabase) {

    private val seriesDao = db.seriesDao()
    private val episodeDao = db.episodeDao()

    fun observeOverview(): Flow<List<SeriesOverview>> = seriesDao.observeOverview()

    fun observeUnsyncedCount(): Flow<Int> = seriesDao.observeUnsyncedCount()

    fun observeStats(): Flow<DataStats> = seriesDao.observeStats()

    fun observeSeries(id: Long): Flow<SeriesWithSeasons?> = seriesDao.observeSeries(id)

    suspend fun getSeries(id: Long): SeriesEntity? = seriesDao.getSeries(id)

    /** Episode count per regular season (index 0 = season 1); specials are not edited in the form. */
    suspend fun getEpisodeCounts(seriesId: Long): List<Int> {
        val byNumber = episodeDao.getSeasons(seriesId).filter { it.number > 0 }.associateBy { it.number }
        val maxSeason = byNumber.keys.maxOrNull() ?: return emptyList()
        return (1..maxSeason).map { n -> byNumber[n]?.let { episodeDao.maxEpisodeNumber(it.id) } ?: 0 }
    }

    /**
     * Creates or updates a series. [episodesPerSeason] has one entry per season
     * (index 0 = season 1); watched flags of episodes that still exist are kept.
     */
    suspend fun save(series: SeriesEntity, episodesPerSeason: List<Int>): Long = db.withTransaction {
        val now = System.currentTimeMillis()
        val id = if (series.id == 0L) {
            seriesDao.insert(series.copy(createdAt = now, updatedAt = now))
        } else {
            seriesDao.update(series.copy(updatedAt = now))
            series.id
        }
        // Matched series get their seasons from TMDB; the form only edits the others.
        if (series.tmdbId == null) syncSeasons(id, episodesPerSeason)
        // A manually chosen status wins on save; "Fertig" means everything has been seen.
        if (series.status == WatchStatus.COMPLETED) episodeDao.markAllWatched(id, now)
        id
    }

    suspend fun delete(seriesId: Long) = seriesDao.delete(seriesId)

    suspend fun setEpisodeWatched(seriesId: Long, episodeId: Long, watched: Boolean) = db.withTransaction {
        episodeDao.setWatched(episodeId, if (watched) System.currentTimeMillis() else null)
        afterProgressChange(seriesId, if (watched) Progress.Change.WATCHED else Progress.Change.UNWATCHED)
    }

    suspend fun setSeasonWatched(seriesId: Long, seasonId: Long, watched: Boolean) = db.withTransaction {
        if (watched) {
            episodeDao.markSeasonWatched(seasonId, System.currentTimeMillis())
        } else {
            episodeDao.clearSeasonWatched(seasonId)
        }
        afterProgressChange(seriesId, if (watched) Progress.Change.WATCHED else Progress.Change.UNWATCHED)
    }

    /** Sets a corrected "watched on" date for an episode (from the episode detail sheet). */
    suspend fun setEpisodeWatchedAt(seriesId: Long, episodeId: Long, watchedAt: Long) = db.withTransaction {
        episodeDao.setWatched(episodeId, watchedAt)
        afterProgressChange(seriesId, Progress.Change.WATCHED)
    }

    /** Own rating 1..10 (null clears it). Rating an unwatched episode also ticks it. */
    suspend fun setEpisodeRating(seriesId: Long, episodeId: Long, rating: Int?) = db.withTransaction {
        episodeDao.setRating(episodeId, rating?.coerceIn(1, 10))
        val episode = episodeDao.getEpisode(episodeId)
        if (rating != null && episode != null && episode.watchedAt == null) {
            episodeDao.setWatched(episodeId, System.currentTimeMillis())
            afterProgressChange(seriesId, Progress.Change.WATCHED)
        }
    }

    /** Regular episode, special or hidden (does not count). */
    suspend fun setEpisodeKind(seriesId: Long, episodeId: Long, kind: EpisodeKind) = db.withTransaction {
        episodeDao.setKind(episodeId, kind)
        afterProgressChange(seriesId, Progress.Change.NEW_EPISODES)
    }

    /** Tapping a hidden episode: it counts again (special in season 0, else regular) and is ticked. */
    suspend fun countAndWatch(seriesId: Long, episodeId: Long, seasonNumber: Int) = db.withTransaction {
        episodeDao.setKind(episodeId, if (seasonNumber == 0) EpisodeKind.SPECIAL else EpisodeKind.REGULAR)
        if (episodeDao.getEpisode(episodeId)?.watchedAt == null) {
            episodeDao.setWatched(episodeId, System.currentTimeMillis())
        }
        afterProgressChange(seriesId, Progress.Change.WATCHED)
    }

    /** Adds a special TMDB does not know (e.g. a film the user counts as special). */
    suspend fun addCustomSpecial(seriesId: Long, title: String, airDate: Long?, watched: Boolean): Long =
        db.withTransaction {
            val seasonId = episodeDao.getSeasons(seriesId).firstOrNull { it.number == 0 }?.id
                ?: episodeDao.insertSeason(SeasonEntity(seriesId = seriesId, number = 0, title = "Specials"))
            val number = maxOf(episodeDao.maxEpisodeNumber(seasonId), CUSTOM_EPISODE_START - 1) + 1
            val id = episodeDao.insertEpisode(
                EpisodeEntity(
                    seasonId = seasonId,
                    number = number,
                    title = title.trim().ifEmpty { "Special" },
                    airDate = airDate,
                    watchedAt = if (watched) System.currentTimeMillis() else null,
                    kind = EpisodeKind.SPECIAL,
                    isCustom = true,
                ),
            )
            afterProgressChange(seriesId, if (watched) Progress.Change.WATCHED else Progress.Change.NEW_EPISODES)
            id
        }

    suspend fun deleteCustomEpisode(seriesId: Long, episodeId: Long) = db.withTransaction {
        episodeDao.deleteCustomEpisode(episodeId)
        afterProgressChange(seriesId, Progress.Change.NEW_EPISODES)
    }

    /** Marks the first unwatched episode as watched. Returns false if there is none. */
    suspend fun watchNextEpisode(seriesId: Long): Boolean = db.withTransaction {
        val next = episodeDao.nextUnwatched(seriesId) ?: return@withTransaction false
        episodeDao.setWatched(next.id, System.currentTimeMillis())
        afterProgressChange(seriesId, Progress.Change.WATCHED)
        true
    }

    private suspend fun afterProgressChange(seriesId: Long, change: Progress.Change) {
        seriesDao.touch(seriesId)
        val series = seriesDao.getSeries(seriesId) ?: return
        val newStatus = Progress.autoStatus(
            current = series.status,
            watched = episodeDao.countWatched(seriesId),
            total = episodeDao.countEpisodes(seriesId),
            change = change,
            isEnded = series.isEnded,
        )
        if (newStatus != series.status) seriesDao.update(series.copy(status = newStatus))
    }

    private suspend fun syncSeasons(seriesId: Long, episodesPerSeason: List<Int>) {
        val existing = episodeDao.getSeasons(seriesId).associateBy { it.number }
        episodesPerSeason.forEachIndexed { index, count ->
            val number = index + 1
            val seasonId = existing[number]?.id
                ?: episodeDao.insertSeason(SeasonEntity(seriesId = seriesId, number = number))
            val target = count.coerceAtLeast(0)
            episodeDao.deleteEpisodesAbove(seasonId, target)
            // IGNORE conflict strategy keeps existing episodes (and their watched state).
            episodeDao.insertEpisodes((1..target).map { EpisodeEntity(seasonId = seasonId, number = it) })
        }
        episodeDao.deleteSeasonsAbove(seriesId, episodesPerSeason.size)
    }
}
