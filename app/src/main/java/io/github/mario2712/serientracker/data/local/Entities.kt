package io.github.mario2712.serientracker.data.local

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import io.github.mario2712.serientracker.domain.WatchStatus

@Entity(tableName = "series", indices = [Index("title")])
data class SeriesEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val status: WatchStatus = WatchStatus.PLAN_TO_WATCH,
    /** 1..10, null = not rated. */
    val rating: Int? = null,
    val streamingService: String? = null,
    val notes: String? = null,
    val isFavorite: Boolean = false,
    /** Reserved for a later TMDB integration (posters, episode titles, air dates). */
    val tmdbId: Int? = null,
    val posterUrl: String? = null,
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
)

val EpisodeEntity.isWatched: Boolean get() = watchedAt != null

data class SeasonWithEpisodes(
    @Embedded val season: SeasonEntity,
    @Relation(parentColumn = "id", entityColumn = "seasonId")
    val episodes: List<EpisodeEntity>,
)

data class SeriesWithSeasons(
    @Embedded val series: SeriesEntity,
    @Relation(entity = SeasonEntity::class, parentColumn = "id", entityColumn = "seriesId")
    val seasons: List<SeasonWithEpisodes>,
)

/** One row of the series list, with progress computed in SQL. */
data class SeriesOverview(
    @Embedded val series: SeriesEntity,
    val totalEpisodes: Int,
    val watchedEpisodes: Int,
    val nextSeason: Int?,
    val nextEpisode: Int?,
)
