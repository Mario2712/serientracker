package io.github.mario2712.serientracker.data.repository

import androidx.room.withTransaction
import io.github.mario2712.serientracker.data.local.AppDatabase
import io.github.mario2712.serientracker.data.local.CUSTOM_EPISODE_START
import io.github.mario2712.serientracker.data.local.DataStats
import io.github.mario2712.serientracker.data.local.EpisodeEntity
import io.github.mario2712.serientracker.data.local.EpisodeViewEntity
import io.github.mario2712.serientracker.data.local.SeasonEntity
import io.github.mario2712.serientracker.data.local.SeriesEntity
import io.github.mario2712.serientracker.data.local.SeriesOverview
import io.github.mario2712.serientracker.data.local.SeriesWithSeasons
import io.github.mario2712.serientracker.data.local.zoneOf
import io.github.mario2712.serientracker.domain.EpisodeKind
import io.github.mario2712.serientracker.domain.Progress
import io.github.mario2712.serientracker.domain.TitleMatcher
import io.github.mario2712.serientracker.domain.WatchStatus
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Single entry point for the UI to read and change data.
 * Later sources (TMDB, Excel import, backup) plug in here, not in the ViewModels.
 */
class SeriesRepository(
    private val db: AppDatabase,
    /** Catch-up mode: ticks count as "watched earlier" and stay out of the daily statistics. */
    private val catchUpMode: suspend () -> Boolean = { false },
    /** Zone of new ticks (where the user is right now). */
    private val currentZone: () -> String = { ZoneId.systemDefault().id },
) {

    private val seriesDao = db.seriesDao()
    private val episodeDao = db.episodeDao()
    private val viewDao = db.episodeViewDao()

    // --- Correcting catch-up days -------------------------------------------------------------

    /** A real viewing: episode (first viewing) or further viewing, with its local date. */
    private data class RealViewing(val episodeId: Long?, val viewId: Long?, val date: LocalDate)

    private suspend fun realViewings(): List<RealViewing> =
        episodeDao.realWatchMoments().map {
            RealViewing(it.id, null, Instant.ofEpochMilli(it.watchedAt).atZone(zoneOf(it.watchedZone)).toLocalDate())
        } + viewDao.realViews().map {
            RealViewing(null, it.id, Instant.ofEpochMilli(it.watchedAt).atZone(zoneOf(it.zoneId)).toLocalDate())
        }

    /** Days (local date where it was watched) with the most real ticks, to find catch-up days. */
    suspend fun busiestDays(limit: Int = 5): List<Pair<LocalDate, Int>> =
        realViewings()
            .groupingBy { it.date }
            .eachCount()
            .entries
            .sortedWith(compareByDescending<Map.Entry<LocalDate, Int>> { it.value }.thenByDescending { it.key })
            .take(limit)
            .map { it.key to it.value }

    /** Real viewings of [date] become "watched earlier"; returns how many were changed. */
    suspend fun markDayAsCatchUp(date: LocalDate): Int = db.withTransaction {
        val ofDay = realViewings().filter { it.date == date }
        ofDay.mapNotNull { it.episodeId }.chunked(500).forEach { episodeDao.markImported(it) }
        ofDay.mapNotNull { it.viewId }.chunked(500).forEach { viewDao.markImported(it) }
        ofDay.size
    }

    // --- Reading ----------------------------------------------------------------------------

    fun observeOverview(): Flow<List<SeriesOverview>> = seriesDao.observeOverview()

    fun observeUnsyncedCount(): Flow<Int> = seriesDao.observeUnsyncedCount()

    fun observeStats(): Flow<DataStats> = seriesDao.observeStats()

    fun observeSeries(id: Long): Flow<SeriesWithSeasons?> = seriesDao.observeSeries(id)

    /** Episode id → number of further viewings (for the "2×" badges). */
    fun observeRewatchCounts(seriesId: Long): Flow<Map<Long, Int>> = viewDao.observeCountsForSeries(seriesId)

    fun observeViews(episodeId: Long): Flow<List<EpisodeViewEntity>> = viewDao.observeForEpisode(episodeId)

    suspend fun getSeries(id: Long): SeriesEntity? = seriesDao.getSeries(id)

    /** Episode count per regular season (index 0 = season 1); specials are not edited in the form. */
    suspend fun getEpisodeCounts(seriesId: Long): List<Int> {
        val byNumber = episodeDao.getSeasons(seriesId).filter { it.number > 0 }.associateBy { it.number }
        val maxSeason = byNumber.keys.maxOrNull() ?: return emptyList()
        return (1..maxSeason).map { n -> byNumber[n]?.let { episodeDao.maxEpisodeNumber(it.id) } ?: 0 }
    }

    // --- Duplicates -------------------------------------------------------------------------

    /** Series that look like the same show: same TMDB id or same (normalized) title/original title. */
    suspend fun findDuplicates(tmdbId: Int?, title: String, originalTitle: String? = null, excludeId: Long = 0): List<SeriesEntity> {
        val names = listOfNotNull(title, originalTitle).map(TitleMatcher::normalize).filter { it.isNotEmpty() }.toSet()
        return seriesDao.getAll().filter { s ->
            s.id != excludeId && (
                (tmdbId != null && s.tmdbId == tmdbId) ||
                    TitleMatcher.normalize(s.title) in names ||
                    (s.originalTitle != null && TitleMatcher.normalize(s.originalTitle) in names)
                )
        }
    }

    /** "watched/total" for the duplicate dialog (Excel numbers while not matched yet). */
    suspend fun progressText(seriesId: Long): String {
        val total = episodeDao.countEpisodes(seriesId)
        if (total > 0) return "${episodeDao.countWatched(seriesId)}/$total"
        val ip = seriesDao.getImportProgress(seriesId)
        return "${ip?.watched ?: 0}/${ip?.total ?: 0}"
    }

    /**
     * New entry for the same show (e.g. a rewatch or someone watching alone): copies title, TMDB info,
     * seasons and episodes (incl. custom specials and episode kinds), but no progress and no ratings.
     */
    suspend fun duplicateSeries(
        sourceId: Long,
        watcher: io.github.mario2712.serientracker.domain.Watcher?,
        label: String?,
        status: WatchStatus = WatchStatus.PLAN_TO_WATCH,
    ): Long = db.withTransaction {
        val source = checkNotNull(seriesDao.getSeries(sourceId)) { "Serie $sourceId nicht gefunden" }
        val now = System.currentTimeMillis()
        val id = seriesDao.insert(
            source.copy(
                id = 0,
                label = label?.trim()?.ifEmpty { null },
                watcher = watcher,
                status = status,
                rating = null,
                notes = null,
                isFavorite = false,
                excelWatched = null,
                excelTotal = null,
                createdAt = now,
                updatedAt = now,
            ),
        )
        episodeDao.getSeasons(sourceId).forEach { season ->
            val seasonId = episodeDao.insertSeason(season.copy(id = 0, seriesId = id))
            episodeDao.insertEpisodesStrict(
                episodeDao.getEpisodes(season.id).map {
                    it.copy(
                        id = 0, seasonId = seasonId, watchedAt = null, watchedAtImported = false,
                        watchedZone = null, rating = null,
                    )
                },
            )
        }
        id
    }

    // --- Saving & ticking -------------------------------------------------------------------

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
        if (series.status == WatchStatus.COMPLETED) episodeDao.markAllWatched(id, now, catchUpMode(), currentZone())
        id
    }

    suspend fun delete(seriesId: Long) = seriesDao.delete(seriesId)

    /** Tick (first viewing) or untick (removes all viewings, including rewatches). */
    suspend fun setEpisodeWatched(seriesId: Long, episodeId: Long, watched: Boolean) = db.withTransaction {
        if (watched) {
            episodeDao.setWatched(episodeId, System.currentTimeMillis(), catchUpMode(), currentZone())
        } else {
            episodeDao.setWatched(episodeId, null, false, null)
            viewDao.deleteForEpisode(episodeId)
        }
        afterProgressChange(seriesId, if (watched) Progress.Change.WATCHED else Progress.Change.UNWATCHED)
    }

    suspend fun setSeasonWatched(seriesId: Long, seasonId: Long, watched: Boolean) = db.withTransaction {
        if (watched) {
            episodeDao.markSeasonWatched(seasonId, System.currentTimeMillis(), catchUpMode(), currentZone())
        } else {
            episodeDao.clearSeasonWatched(seasonId)
            viewDao.deleteForSeason(seasonId)
        }
        afterProgressChange(seriesId, if (watched) Progress.Change.WATCHED else Progress.Change.UNWATCHED)
    }

    /** Watched once more: a further viewing, or the first tick if not watched yet. */
    suspend fun watchAgain(seriesId: Long, episodeId: Long) = db.withTransaction {
        val episode = episodeDao.getEpisode(episodeId) ?: return@withTransaction
        if (episode.watchedAt == null) {
            episodeDao.setWatched(episodeId, System.currentTimeMillis(), catchUpMode(), currentZone())
        } else {
            viewDao.insert(EpisodeViewEntity(episodeId = episodeId, watchedAt = System.currentTimeMillis(), imported = catchUpMode(), zoneId = currentZone()))
        }
        afterProgressChange(seriesId, Progress.Change.WATCHED)
    }

    /** Whole season watched once more: every counting episode gets a (further) viewing. */
    suspend fun watchSeasonAgain(seriesId: Long, seasonId: Long) = db.withTransaction {
        val now = System.currentTimeMillis()
        val catchUp = catchUpMode()
        val zone = currentZone()
        episodeDao.countingEpisodes(seasonId).forEach { ep ->
            if (ep.watchedAt == null) {
                episodeDao.setWatched(ep.id, now, catchUp, zone)
            } else {
                viewDao.insert(EpisodeViewEntity(episodeId = ep.id, watchedAt = now, imported = catchUp, zoneId = zone))
            }
        }
        afterProgressChange(seriesId, Progress.Change.WATCHED)
    }

    /**
     * Deletes one viewing. [viewId] null = the first viewing: the earliest further viewing moves up;
     * without further viewings the episode is unwatched.
     */
    suspend fun deleteViewing(seriesId: Long, episodeId: Long, viewId: Long?) = db.withTransaction {
        if (viewId != null) {
            viewDao.delete(viewId)
        } else {
            val next = viewDao.forEpisode(episodeId).firstOrNull()
            if (next != null) {
                episodeDao.setWatched(episodeId, next.watchedAt, next.imported, next.zoneId)
                viewDao.delete(next.id)
            } else {
                episodeDao.setWatched(episodeId, null, false, null)
            }
        }
        afterProgressChange(seriesId, Progress.Change.UNWATCHED)
    }

    /** Corrected date of a viewing ([viewId] null = first viewing); a chosen date is always exact. */
    suspend fun setViewingTime(seriesId: Long, episodeId: Long, viewId: Long?, watchedAt: Long, zone: String) =
        db.withTransaction {
            if (viewId == null) {
                episodeDao.setWatched(episodeId, watchedAt, false, zone)
            } else {
                viewDao.updateTime(viewId, watchedAt, zone)
            }
            afterProgressChange(seriesId, Progress.Change.WATCHED)
        }

    /** Sets a corrected "watched on" date for the first viewing (from the episode detail sheet). */
    suspend fun setEpisodeWatchedAt(seriesId: Long, episodeId: Long, watchedAt: Long, zone: String = currentZone()) =
        setViewingTime(seriesId, episodeId, null, watchedAt, zone)

    /** Own rating 1..10 (null clears it). Rating an unwatched episode also ticks it. */
    suspend fun setEpisodeRating(seriesId: Long, episodeId: Long, rating: Int?) = db.withTransaction {
        episodeDao.setRating(episodeId, rating?.coerceIn(1, 10))
        val episode = episodeDao.getEpisode(episodeId)
        if (rating != null && episode != null && episode.watchedAt == null) {
            episodeDao.setWatched(episodeId, System.currentTimeMillis(), catchUpMode(), currentZone())
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
            episodeDao.setWatched(episodeId, System.currentTimeMillis(), catchUpMode(), currentZone())
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
                    watchedAtImported = watched && catchUpMode(),
                    watchedZone = if (watched) currentZone() else null,
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
        episodeDao.setWatched(next.id, System.currentTimeMillis(), catchUpMode(), currentZone())
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
