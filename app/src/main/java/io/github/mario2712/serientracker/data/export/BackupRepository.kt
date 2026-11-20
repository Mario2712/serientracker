package io.github.mario2712.serientracker.data.export

import androidx.room.withTransaction
import io.github.mario2712.serientracker.data.local.AppDatabase
import io.github.mario2712.serientracker.data.local.EpisodeEntity
import io.github.mario2712.serientracker.data.local.EpisodeViewEntity
import io.github.mario2712.serientracker.data.local.HOME_ZONE
import io.github.mario2712.serientracker.data.local.ImportProgressEntity
import io.github.mario2712.serientracker.data.local.SeasonEntity
import io.github.mario2712.serientracker.data.local.SeriesEntity
import io.github.mario2712.serientracker.data.local.SeriesWithSeasons
import io.github.mario2712.serientracker.domain.EpisodeKind
import io.github.mario2712.serientracker.domain.WatchStatus
import io.github.mario2712.serientracker.domain.Watcher
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

// Archive format. Keep fields optional with defaults so older archives stay readable.

@Serializable
data class Backup(
    val formatVersion: Int,
    val appVersion: String = "",
    val exportedAt: String = "",
    val series: List<BackupSeries>,
)

@Serializable
data class BackupSeries(
    val title: String,
    val originalTitle: String? = null,
    val label: String? = null,
    val status: WatchStatus = WatchStatus.PLAN_TO_WATCH,
    val rating: Int? = null,
    val streamingService: String? = null,
    val notes: String? = null,
    val isFavorite: Boolean = false,
    val watcher: Watcher? = null,
    val genre: String? = null,
    val tmdbId: Int? = null,
    val posterUrl: String? = null,
    val isEnded: Boolean? = null,
    val lastWatchedAirDate: Long? = null,
    val nextAirDate: Long? = null,
    val infoUpdatedAt: Long? = null,
    val tmdbMetaVersion: Int = 0,
    val excelWatched: Int? = null,
    val excelTotal: Int? = null,
    val nextEpisodeSeason: Int? = null,
    val nextEpisodeNumber: Int? = null,
    val nextEpisodeName: String? = null,
    val overview: String? = null,
    val tmdbRating: Double? = null,
    val tmdbVoteCount: Int? = null,
    val trailerKey: String? = null,
    val castSummary: String? = null,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val importProgress: BackupImportProgress? = null,
    val seasons: List<BackupSeason> = emptyList(),
)

@Serializable
data class BackupImportProgress(
    val lastSeason: Int? = null,
    val lastEpisode: Int? = null,
    val lastSpecial: Int? = null,
    val maxSeason: Int? = null,
    val maxEpisode: Int? = null,
    val maxSpecial: Int? = null,
    val watched: Int? = null,
    val total: Int? = null,
    val updatedAt: Long? = null,
    val unseen: Boolean? = null,
)

@Serializable
data class BackupSeason(val number: Int, val title: String? = null, val episodes: List<BackupEpisode> = emptyList())

@Serializable
data class BackupEpisode(
    val number: Int,
    val title: String? = null,
    val airDate: Long? = null,
    val watchedAt: Long? = null,
    val watchedAtImported: Boolean = false,
    val rating: Int? = null,
    val kind: EpisodeKind? = null,
    val isCustom: Boolean = false,
    val runtime: Int? = null,
    /** Zone of the first viewing; missing in older archives (= home zone). */
    val watchedZone: String? = null,
    /** Further viewings (rewatches). */
    val views: List<BackupView> = emptyList(),
)

@Serializable
data class BackupView(val watchedAt: Long, val imported: Boolean = false, val zoneId: String = HOME_ZONE)

class BackupFormatException(message: String) : Exception(message)

/** Full archive of the app data as JSON, and restoring it. */
class BackupRepository(
    private val db: AppDatabase,
    private val appVersion: String,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    suspend fun export(out: OutputStream) {
        val backup = Backup(
            formatVersion = FORMAT_VERSION,
            appVersion = appVersion,
            exportedAt = OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
            series = db.episodeViewDao().getAll().groupBy { it.episodeId }.let { views ->
                db.seriesDao().getAllWithSeasons().map { toBackup(it, views) }
            },
        )
        out.bufferedWriter(Charsets.UTF_8).use { it.write(json.encodeToString(Backup.serializer(), backup)) }
    }

    /**
     * Replaces all data with the archive. The file is fully parsed and checked first;
     * the replacement runs in one transaction, so a broken file changes nothing.
     * Returns the number of restored series.
     */
    suspend fun restore(input: InputStream): Int {
        val text = input.bufferedReader(Charsets.UTF_8).use { it.readText() }
        val backup = try {
            json.decodeFromString(Backup.serializer(), text)
        } catch (e: Exception) {
            throw BackupFormatException("Keine gültige Serientracker-Archivdatei.")
        }
        if (backup.formatVersion > FORMAT_VERSION) {
            throw BackupFormatException("Archiv stammt aus einer neueren App-Version. Bitte zuerst die App aktualisieren.")
        }
        db.withTransaction {
            db.seriesDao().deleteAll()
            backup.series.forEach { insert(it) }
        }
        return backup.series.size
    }

    private suspend fun insert(b: BackupSeries) {
        val seriesDao = db.seriesDao()
        val episodeDao = db.episodeDao()
        val id = seriesDao.insert(
            SeriesEntity(
                title = b.title,
                originalTitle = b.originalTitle,
                label = b.label,
                status = b.status,
                rating = b.rating,
                streamingService = b.streamingService,
                notes = b.notes,
                isFavorite = b.isFavorite,
                watcher = b.watcher,
                genre = b.genre,
                tmdbId = b.tmdbId,
                posterUrl = b.posterUrl,
                isEnded = b.isEnded,
                lastWatchedAirDate = b.lastWatchedAirDate,
                nextAirDate = b.nextAirDate,
                infoUpdatedAt = b.infoUpdatedAt,
                tmdbMetaVersion = b.tmdbMetaVersion,
                excelWatched = b.excelWatched,
                excelTotal = b.excelTotal,
                nextEpisodeSeason = b.nextEpisodeSeason,
                nextEpisodeNumber = b.nextEpisodeNumber,
                nextEpisodeName = b.nextEpisodeName,
                overview = b.overview,
                tmdbRating = b.tmdbRating,
                tmdbVoteCount = b.tmdbVoteCount,
                trailerKey = b.trailerKey,
                castSummary = b.castSummary,
                createdAt = b.createdAt,
                updatedAt = b.updatedAt,
            ),
        )
        b.importProgress?.let { p ->
            seriesDao.insertImportProgress(
                ImportProgressEntity(
                    seriesId = id,
                    lastSeason = p.lastSeason,
                    lastEpisode = p.lastEpisode,
                    lastSpecial = p.lastSpecial,
                    maxSeason = p.maxSeason,
                    maxEpisode = p.maxEpisode,
                    maxSpecial = p.maxSpecial,
                    watched = p.watched,
                    total = p.total,
                    updatedAt = p.updatedAt,
                    unseen = p.unseen,
                ),
            )
        }
        b.seasons.forEach { season ->
            val seasonId = episodeDao.insertSeason(SeasonEntity(seriesId = id, number = season.number, title = season.title))
            val episodeIds = episodeDao.insertEpisodesStrict(
                season.episodes.map {
                    EpisodeEntity(
                        seasonId = seasonId,
                        number = it.number,
                        title = it.title,
                        airDate = it.airDate,
                        watchedAt = it.watchedAt,
                        watchedAtImported = it.watchedAtImported,
                        rating = it.rating,
                        // Archives from before v0.3 have no kind: season 0 held the specials.
                        kind = it.kind ?: if (season.number == 0) EpisodeKind.SPECIAL else EpisodeKind.REGULAR,
                        isCustom = it.isCustom,
                        runtime = it.runtime,
                        // Older archives have no zone: their viewings were in the home zone.
                        watchedZone = if (it.watchedAt != null) it.watchedZone ?: HOME_ZONE else null,
                    )
                },
            )
            db.episodeViewDao().insertAll(
                season.episodes.zip(episodeIds).flatMap { (ep, episodeId) ->
                    ep.views.map { EpisodeViewEntity(episodeId = episodeId, watchedAt = it.watchedAt, imported = it.imported, zoneId = it.zoneId) }
                },
            )
        }
    }

    private fun toBackup(d: SeriesWithSeasons, views: Map<Long, List<EpisodeViewEntity>>): BackupSeries {
        val s = d.series
        return BackupSeries(
            title = s.title,
            originalTitle = s.originalTitle,
            label = s.label,
            status = s.status,
            rating = s.rating,
            streamingService = s.streamingService,
            notes = s.notes,
            isFavorite = s.isFavorite,
            watcher = s.watcher,
            genre = s.genre,
            tmdbId = s.tmdbId,
            posterUrl = s.posterUrl,
            isEnded = s.isEnded,
            lastWatchedAirDate = s.lastWatchedAirDate,
            nextAirDate = s.nextAirDate,
            infoUpdatedAt = s.infoUpdatedAt,
            tmdbMetaVersion = s.tmdbMetaVersion,
            excelWatched = s.excelWatched,
            excelTotal = s.excelTotal,
            nextEpisodeSeason = s.nextEpisodeSeason,
            nextEpisodeNumber = s.nextEpisodeNumber,
            nextEpisodeName = s.nextEpisodeName,
            overview = s.overview,
            tmdbRating = s.tmdbRating,
            tmdbVoteCount = s.tmdbVoteCount,
            trailerKey = s.trailerKey,
            castSummary = s.castSummary,
            createdAt = s.createdAt,
            updatedAt = s.updatedAt,
            importProgress = d.importProgress?.let { p ->
                BackupImportProgress(
                    p.lastSeason, p.lastEpisode, p.lastSpecial, p.maxSeason, p.maxEpisode, p.maxSpecial,
                    p.watched, p.total, p.updatedAt, p.unseen,
                )
            },
            seasons = d.seasons.sortedBy { it.season.number }.map { season ->
                BackupSeason(
                    number = season.season.number,
                    title = season.season.title,
                    episodes = season.episodes.sortedBy { it.number }.map {
                        BackupEpisode(
                            number = it.number,
                            title = it.title,
                            airDate = it.airDate,
                            watchedAt = it.watchedAt,
                            watchedAtImported = it.watchedAtImported,
                            rating = it.rating,
                            kind = it.kind,
                            isCustom = it.isCustom,
                            runtime = it.runtime,
                            watchedZone = it.watchedZone,
                            views = views[it.id].orEmpty().map { v -> BackupView(v.watchedAt, v.imported, v.zoneId) },
                        )
                    },
                )
            },
        )
    }

    companion object {
        const val FORMAT_VERSION = 1
        const val MIME_JSON = "application/json"

        fun fileName(date: LocalDate = LocalDate.now()): String =
            "serientracker_backup_${date.format(DateTimeFormatter.BASIC_ISO_DATE)}.json"
    }
}
