package io.github.mario2712.serientracker.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Raw TMDB v3 JSON. Only the fields the app uses; unknown keys are ignored.

@Serializable
data class TmdbSearchResponse(val results: List<TmdbSearchResult> = emptyList())

@Serializable
data class TmdbSearchResult(
    val id: Int,
    val name: String = "",
    @SerialName("original_name") val originalName: String = "",
    @SerialName("first_air_date") val firstAirDate: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    val overview: String? = null,
) {
    val year: String? get() = firstAirDate?.take(4)?.takeIf { it.length == 4 }
    val posterUrl: String? get() = posterPath?.let { Tmdb.posterUrl(it) }
}

@Serializable
data class TmdbShowJson(
    val id: Int,
    val name: String = "",
    @SerialName("original_name") val originalName: String = "",
    val status: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    val seasons: List<TmdbSeasonSummaryJson> = emptyList(),
    @SerialName("next_episode_to_air") val nextEpisodeToAir: TmdbEpisodeJson? = null,
    val genres: List<TmdbGenreJson> = emptyList(),
)

@Serializable
data class TmdbGenreJson(val id: Int = 0, val name: String = "")

@Serializable
data class TmdbSeasonSummaryJson(
    @SerialName("season_number") val seasonNumber: Int,
    @SerialName("episode_count") val episodeCount: Int = 0,
    val name: String? = null,
    @SerialName("air_date") val airDate: String? = null,
)

@Serializable
data class TmdbSeasonJson(
    @SerialName("season_number") val seasonNumber: Int,
    val name: String? = null,
    val episodes: List<TmdbEpisodeJson> = emptyList(),
)

@Serializable
data class TmdbEpisodeJson(
    @SerialName("episode_number") val episodeNumber: Int,
    @SerialName("season_number") val seasonNumber: Int = 0,
    val name: String? = null,
    @SerialName("air_date") val airDate: String? = null,
)

/** A show with all its seasons and episodes, ready to be stored. */
data class TmdbShow(
    val id: Int,
    val name: String,
    val originalName: String,
    /** "Ended" / "Canceled" mean no more episodes. */
    val status: String?,
    val posterUrl: String?,
    val nextAirDate: String?,
    val seasons: List<TmdbSeason>,
    /** German genre names (language=de-DE), e.g. "Drama", "Krimi", "Sci-Fi & Fantasy". */
    val genres: List<String> = emptyList(),
) {
    val isEnded: Boolean? get() = status?.let { it == "Ended" || it == "Canceled" }

    /** Regular seasons that already have aired episodes, comparable to Excel "maxStaffel". */
    val airedRegularSeasons: Int get() = seasons.count { it.number > 0 && it.episodes.isNotEmpty() }
}

/** A season with only its already aired episodes (as Excel counts "available" episodes). */
data class TmdbSeason(val number: Int, val name: String?, val episodes: List<TmdbEpisode>)

data class TmdbEpisode(val number: Int, val title: String?, val airDate: String?)

object Tmdb {
    const val BASE_URL = "https://api.themoviedb.org/3/"
    const val KEY_URL = "https://www.themoviedb.org/settings/api"
    fun posterUrl(path: String) = "https://image.tmdb.org/t/p/w342$path"
}
