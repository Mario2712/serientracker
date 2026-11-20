package io.github.mario2712.serientracker.data.repository

import androidx.room.withTransaction
import io.github.mario2712.serientracker.data.local.AppDatabase
import io.github.mario2712.serientracker.data.local.EpisodeEntity
import io.github.mario2712.serientracker.data.local.SeasonEntity
import io.github.mario2712.serientracker.data.local.SeriesEntity
import io.github.mario2712.serientracker.data.local.TMDB_META_VERSION
import io.github.mario2712.serientracker.data.remote.TmdbDataSource
import io.github.mario2712.serientracker.data.remote.TmdbSearchResult
import io.github.mario2712.serientracker.data.remote.TmdbShow
import io.github.mario2712.serientracker.domain.EpisodeKind
import io.github.mario2712.serientracker.domain.Progress
import io.github.mario2712.serientracker.domain.TitleMatcher
import io.github.mario2712.serientracker.domain.WatchStatus
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/** Result of storing TMDB episodes for one series, compared with the Excel numbers. */
data class SyncReport(
    val seriesId: Long,
    val title: String,
    val excelWatched: Int?,
    val excelTotal: Int?,
    val watched: Int,
    val total: Int,
    /** Episodes added by a refresh of an already linked show, e.g. "S05E09 „Titel“" (for notifications). */
    val newEpisodes: List<String> = emptyList(),
) {
    /** Excel and TMDB disagree; worth a look by the user. */
    val hasDifference: Boolean
        get() = (excelTotal != null && excelTotal != total) || (excelWatched != null && excelWatched != watched)
}

sealed interface MatchOutcome {
    data class Matched(val report: SyncReport) : MatchOutcome
    data class NeedsReview(val series: SeriesEntity, val candidates: List<TmdbSearchResult>) : MatchOutcome
}

/** Brings real seasons and episodes from TMDB into the database. */
class TmdbSyncRepository(
    private val db: AppDatabase,
    private val tmdb: TmdbDataSource,
) {
    private val seriesDao = db.seriesDao()
    private val episodeDao = db.episodeDao()

    fun observeUnsyncedCount(): Flow<Int> = seriesDao.observeUnsyncedCount()

    suspend fun unsynced(): List<SeriesEntity> = seriesDao.getUnsynced()

    suspend fun search(query: String): List<TmdbSearchResult> = tmdb.search(query).take(10)

    /**
     * Searches TMDB for the series. A result is taken automatically only when the title and
     * the Excel season count match; otherwise the user picks from the candidates.
     */
    suspend fun autoMatch(series: SeriesEntity): MatchOutcome {
        val candidates = tmdb.search(series.title).take(5)
        val maxSeason = seriesDao.getImportProgress(series.id)?.maxSeason
        val titleHits = candidates.filter {
            TitleMatcher.sameTitle(series.title, it.name) || TitleMatcher.sameTitle(series.title, it.originalName)
        }
        for (hit in titleHits.take(2)) {
            val show = tmdb.show(hit.id)
            val confident = TitleMatcher.isConfidentMatch(
                excelTitle = series.title,
                excelMaxSeason = maxSeason,
                candidateNames = listOf(show.name, show.originalName, hit.name, hit.originalName),
                regularSeasons = show.airedRegularSeasons,
            )
            if (confident) return MatchOutcome.Matched(apply(series.id, show))
        }
        return MatchOutcome.NeedsReview(series, candidates)
    }

    /** Links the series with a TMDB show chosen by the user. */
    suspend fun link(seriesId: Long, tmdbId: Int): SyncReport = apply(seriesId, tmdb.show(tmdbId))

    /** Fetches new episodes for a matched series. */
    suspend fun refresh(seriesId: Long): SyncReport? {
        val tmdbId = seriesDao.getSeries(seriesId)?.tmdbId ?: return null
        return apply(seriesId, tmdb.show(tmdbId))
    }

    /** Refreshes all matched series; returns how many got new episodes. */
    suspend fun refreshAll(onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }): Int {
        val synced = seriesDao.getSynced()
        var withNew = 0
        synced.forEachIndexed { i, s ->
            runCatching { refresh(s.id) }.getOrNull()?.let { if (it.newEpisodes.isNotEmpty()) withNew++ }
            onProgress(i + 1, synced.size)
        }
        return withNew
    }

    /**
     * Daily background check: refreshes the matched series that can get new episodes
     * ([WatchStatus.ALERT_STATUSES]) and returns the reports that contain new episodes.
     */
    suspend fun checkForNewEpisodes(): List<SyncReport> =
        seriesDao.getSynced()
            .filter { it.status in WatchStatus.ALERT_STATUSES }
            .mapNotNull { s -> runCatching { refresh(s.id) }.getOrNull() }
            .filter { it.newEpisodes.isNotEmpty() }

    /**
     * Stores the TMDB seasons and episodes. Existing episodes keep their watched state.
     * On the first sync the Excel position decides which episodes are watched.
     */
    suspend fun apply(seriesId: Long, show: TmdbShow): SyncReport = db.withTransaction {
        val series = checkNotNull(seriesDao.getSeries(seriesId)) { "Serie $seriesId nicht gefunden" }
        val progress = seriesDao.getImportProgress(seriesId)
        val existingSeasons = episodeDao.getSeasons(seriesId).associateBy { it.number }
        val watchedAt = progress?.updatedAt ?: System.currentTimeMillis()
        val countBefore = episodeDao.countEpisodes(seriesId)

        // Specials (season 0) only for series that track them: TMDB often lists lots of extras.
        val tracksSpecials = (progress?.maxSpecial ?: 0) > 0 || existingSeasons.containsKey(0)
        val added = mutableListOf<String>()

        for (season in show.seasons) {
            if (season.number == 0 && !tracksSpecials) continue
            val seasonId = existingSeasons[season.number]?.id ?: episodeDao.insertSeason(
                SeasonEntity(seriesId = seriesId, number = season.number, title = if (season.number == 0) "Specials" else null),
            )
            val existing = episodeDao.getEpisodes(seasonId).associateBy { it.number }
            // All TMDB specials count; extras that are no specials for the user are hidden by hand.
            // The Excel numbers specials 1, 2, 3 … in order of first air date.
            val chronologicalSpecials = if (season.number == 0) {
                season.episodes
                    .sortedWith(compareBy({ it.airDate.toEpochDay() ?: Long.MAX_VALUE }, { it.number }))
                    .map { it.number }
            } else {
                emptyList()
            }
            val newEpisodes = season.episodes.filter { it.number !in existing }.map { ep ->
                val kind = if (season.number > 0) EpisodeKind.REGULAR else EpisodeKind.SPECIAL
                val watched = progress != null && Progress.isWatchedAtPosition(
                    season = season.number,
                    episode = if (season.number == 0) chronologicalSpecials.indexOf(ep.number) + 1 else ep.number,
                    lastSeason = progress.lastSeason,
                    lastEpisode = progress.lastEpisode,
                    lastSpecial = progress.lastSpecial,
                )
                EpisodeEntity(
                    seasonId = seasonId,
                    number = ep.number,
                    title = ep.title,
                    airDate = ep.airDate.toEpochDay(),
                    watchedAt = if (watched) watchedAt else null,
                    watchedAtImported = watched,
                    kind = kind,
                    runtime = ep.runtime,
                )
            }
            episodeDao.insertEpisodes(newEpisodes)
            // Only a refresh of an already linked show reports "new" episodes (not the first match).
            if (series.tmdbId == show.id && progress == null) {
                newEpisodes.forEach { ep ->
                    added += Progress.episodeCode(season.number, ep.number) + (ep.title?.let { " „$it“" } ?: "")
                }
            }
            season.episodes.forEach { ep ->
                existing[ep.number]?.let {
                    episodeDao.setEpisodeInfo(it.id, ep.title, ep.airDate.toEpochDay(), ep.runtime)
                }
            }
        }
        if (progress != null) seriesDao.deleteImportProgress(seriesId)

        val watched = episodeDao.countWatched(seriesId)
        val total = episodeDao.countEpisodes(seriesId)
        val status = if (progress != null || total > countBefore) {
            val auto = Progress.autoStatus(series.status, watched, total, Progress.Change.NEW_EPISODES, show.isEnded)
            // First sync: if only specials are open (all regular episodes seen), the Excel verdict
            // ("fertig"/"aufgeholt") stays; extra TMDB specials alone should not pause the series.
            val onlySpecialsOpen = episodeDao.nextUnwatched(seriesId) == null
            if (progress != null && onlySpecialsOpen && auto == WatchStatus.PAUSED) series.status else auto
        } else {
            series.status
        }
        // German title, original title and genre come from TMDB when the show is linked, or once
        // after the rules changed (tmdbMetaVersion). Later refreshes keep hand-made edits.
        val fillMeta = series.tmdbId != show.id || series.tmdbMetaVersion < TMDB_META_VERSION
        val tmdbGenre = show.genres.joinToString(", ").ifBlank { null }
        val newTitle = if (fillMeta) show.name.trim().ifBlank { series.title } else series.title
        seriesDao.update(
            series.copy(
                tmdbId = show.id,
                title = newTitle,
                originalTitle = if (fillMeta || series.originalTitle == null) {
                    show.originalName.trim().ifBlank { null } ?: series.originalTitle
                } else {
                    series.originalTitle
                },
                genre = if (fillMeta) tmdbGenre ?: series.genre else series.genre,
                tmdbMetaVersion = TMDB_META_VERSION,
                excelWatched = series.excelWatched ?: progress?.watched,
                excelTotal = series.excelTotal ?: progress?.total,
                posterUrl = show.posterUrl ?: series.posterUrl,
                isEnded = show.isEnded,
                // TMDB knows the next announced episode; without one the old (Excel) date is obsolete.
                nextAirDate = show.nextAirDate.toEpochDay(),
                nextEpisodeSeason = show.nextEpisodeSeason,
                nextEpisodeNumber = show.nextEpisodeNumber,
                nextEpisodeName = show.nextEpisodeName,
                overview = show.overview ?: series.overview,
                tmdbRating = show.voteAverage ?: series.tmdbRating,
                tmdbVoteCount = show.voteCount ?: series.tmdbVoteCount,
                trailerKey = show.trailerKey ?: series.trailerKey,
                castSummary = show.castSummary ?: series.castSummary,
                infoUpdatedAt = LocalDate.now().toEpochDay(),
                status = status,
            ),
        )
        SyncReport(seriesId, newTitle, progress?.watched, progress?.total, watched, total, added)
    }

    private fun String?.toEpochDay(): Long? =
        this?.let { runCatching { LocalDate.parse(it).toEpochDay() }.getOrNull() }
}
