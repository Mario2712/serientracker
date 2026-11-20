package io.github.mario2712.serientracker.data.local

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import io.github.mario2712.serientracker.domain.EpisodeKind
import io.github.mario2712.serientracker.domain.WatchStatus
import io.github.mario2712.serientracker.domain.Watcher

@Entity(tableName = "series", indices = [Index("title")])
data class SeriesEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    /** Original title from TMDB (e.g. "The Queen's Gambit" for "Damengambit"). */
    val originalTitle: String? = null,
    /** Extra name to tell duplicates apart, e.g. "Rewatch 2026" or "Mario alleine". */
    val label: String? = null,
    val status: WatchStatus = WatchStatus.PLAN_TO_WATCH,
    /** 1..10, null = not rated. */
    val rating: Int? = null,
    val streamingService: String? = null,
    val notes: String? = null,
    val isFavorite: Boolean = false,
    val watcher: Watcher? = null,
    val genre: String? = null,
    /** TMDB id once the series was matched; episodes then come from TMDB. */
    val tmdbId: Int? = null,
    val posterUrl: String? = null,
    /** TMDB: no more episodes will come (Ended/Canceled). null = unknown. */
    val isEnded: Boolean? = null,
    /** First air date of the last watched episode (epoch day), from Excel until episodes are known. */
    val lastWatchedAirDate: Long? = null,
    /** Next planned air date or latest episode (epoch day). */
    val nextAirDate: Long? = null,
    /** When the series info was last refreshed (epoch day, Excel "aktualisiert" or TMDB sync). */
    val infoUpdatedAt: Long? = null,
    /** Below [TMDB_META_VERSION], the next sync takes title, original title and genre from TMDB. */
    @ColumnInfo(defaultValue = "0") val tmdbMetaVersion: Int = 0,
    /** Excel "gesehen"/"von" at import, kept after the TMDB match to show differences. */
    val excelWatched: Int? = null,
    val excelTotal: Int? = null,
    /** Next episode announced by TMDB (date in [nextAirDate]). */
    val nextEpisodeSeason: Int? = null,
    val nextEpisodeNumber: Int? = null,
    val nextEpisodeName: String? = null,
    /** TMDB info, refreshed with every sync. */
    val overview: String? = null,
    val tmdbRating: Double? = null,
    val tmdbVoteCount: Int? = null,
    /** YouTube video key of the trailer. */
    val trailerKey: String? = null,
    /** Top cast as "Name als Rolle, …". */
    val castSummary: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "seasons",
    foreignKeys = [
        ForeignKey(
            entity = SeriesEntity::class,
            parentColumns = ["id"],
            childColumns = ["seriesId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["seriesId", "number"], unique = true)],
)
data class SeasonEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val seriesId: Long,
    val number: Int,
    val title: String? = null,
)

@Entity(
    tableName = "episodes",
    foreignKeys = [
        ForeignKey(
            entity = SeasonEntity::class,
            parentColumns = ["id"],
            childColumns = ["seasonId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["seasonId", "number"], unique = true)],
)
data class EpisodeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val seasonId: Long,
    val number: Int,
    val title: String? = null,
    /** Air date as epoch day, for later "new episode" features. */
    val airDate: Long? = null,
    /** When the episode was marked as watched; null = not watched. */
    val watchedAt: Long? = null,
    /** watchedAt is only the Excel "aktualisiert" date, not the real moment. */
    @ColumnInfo(defaultValue = "0") val watchedAtImported: Boolean = false,
    /** Own rating 1..10, null = not rated. */
    val rating: Int? = null,
    @ColumnInfo(defaultValue = "'REGULAR'") val kind: EpisodeKind = EpisodeKind.REGULAR,
    /** Special added by hand (not from TMDB); never touched by a refresh. */
    @ColumnInfo(defaultValue = "0") val isCustom: Boolean = false,
    /** Runtime in minutes, from TMDB. */
    val runtime: Int? = null,
    /** Time zone where the (first) viewing was ticked; null = [HOME_ZONE]. */
    val watchedZone: String? = null,
)

/**
 * Further viewings of an episode (the first one is stored in [EpisodeEntity.watchedAt]).
 * Number of viewings = 1 + rows here.
 */
@Entity(
    tableName = "episode_views",
    foreignKeys = [
        ForeignKey(
            entity = EpisodeEntity::class,
            parentColumns = ["id"],
            childColumns = ["episodeId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("episodeId")],
)
data class EpisodeViewEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val episodeId: Long,
    val watchedAt: Long,
    /** "Watched earlier", no exact date (catch-up mode). */
    @ColumnInfo(defaultValue = "0") val imported: Boolean = false,
    val zoneId: String,
)

/** A real first viewing (id of the episode), for correcting catch-up days. */
data class WatchMoment(val id: Long, val watchedAt: Long, val watchedZone: String?)

/** Zone for viewings stored before zones were recorded. */
const val HOME_ZONE = "Europe/Vienna"

/** Zone of the first viewing, falling back to [HOME_ZONE]. */
val EpisodeEntity.zone: java.time.ZoneId
    get() = runCatching { java.time.ZoneId.of(watchedZone ?: HOME_ZONE) }.getOrDefault(java.time.ZoneId.of(HOME_ZONE))

fun zoneOf(id: String?): java.time.ZoneId =
    runCatching { java.time.ZoneId.of(id ?: HOME_ZONE) }.getOrDefault(java.time.ZoneId.of(HOME_ZONE))

/** Series title with the extra name, e.g. "Dark · Rewatch 2026". */
val SeriesEntity.displayTitle: String get() = label?.takeIf { it.isNotBlank() }?.let { "$title · $it" } ?: title

/** Current version of the TMDB metadata rules (title, original title, genre). */
const val TMDB_META_VERSION = 1

/** Custom specials get numbers from here on, so they never clash with TMDB numbers. */
const val CUSTOM_EPISODE_START = 1000

val EpisodeEntity.isWatched: Boolean get() = watchedAt != null
val EpisodeEntity.counts: Boolean get() = kind != EpisodeKind.HIDDEN

/**
 * Excel position of a series that has not been matched with TMDB yet.
 * Deleted once the real episodes exist.
 */
@Entity(
    tableName = "import_progress",
    foreignKeys = [
        ForeignKey(
            entity = SeriesEntity::class,
            parentColumns = ["id"],
            childColumns = ["seriesId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class ImportProgressEntity(
    @PrimaryKey val seriesId: Long,
    val lastSeason: Int?,
    val lastEpisode: Int?,
    val lastSpecial: Int?,
    val maxSeason: Int?,
    val maxEpisode: Int?,
    val maxSpecial: Int?,
    val watched: Int?,
    val total: Int?,
    /** Excel "aktualisiert" as epoch ms, used as watchedAt for imported episodes. */
    val updatedAt: Long?,
    /** Excel "ungesehen" (x), kept for a lossless Excel export. */
    val unseen: Boolean? = null,
)

data class SeasonWithEpisodes(
    @Embedded val season: SeasonEntity,
    @Relation(parentColumn = "id", entityColumn = "seasonId")
    val episodes: List<EpisodeEntity>,
)

data class SeriesWithSeasons(
    @Embedded val series: SeriesEntity,
    @Relation(entity = SeasonEntity::class, parentColumn = "id", entityColumn = "seriesId")
    val seasons: List<SeasonWithEpisodes>,
    @Relation(parentColumn = "id", entityColumn = "seriesId")
    val importProgress: ImportProgressEntity?,
)

/** Totals for the "Über" screen. */
data class DataStats(val series: Int, val synced: Int, val episodes: Int, val watched: Int)

/**
 * One row of the series list, with progress computed in SQL.
 * Without episodes (not matched with TMDB yet) the numbers come from the Excel import.
 */
data class SeriesOverview(
    @Embedded val series: SeriesEntity,
    val totalEpisodes: Int,
    val watchedEpisodes: Int,
    val nextSeason: Int?,
    val nextEpisode: Int?,
    /** Last watched position from Excel; only set while [needsSync]. */
    val excelSeason: Int?,
    val excelEpisode: Int?,
    val needsSync: Boolean,
    /** Average of the rated (counting) episodes, null if none is rated. */
    val avgEpisodeRating: Double? = null,
)
