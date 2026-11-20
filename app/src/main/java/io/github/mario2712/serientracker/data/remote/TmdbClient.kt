package io.github.mario2712.serientracker.data.remote

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.time.LocalDate

/** What the sync logic needs from TMDB; faked in tests. */
interface TmdbDataSource {
    suspend fun search(query: String): List<TmdbSearchResult>
    suspend fun show(id: Int): TmdbShow
}

class TmdbKeyMissingException : IllegalStateException("Kein TMDB-API-Key hinterlegt")

class TmdbClient(private val apiKey: suspend () -> String?) : TmdbDataSource {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    private val api: TmdbApi by lazy {
        val http = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val key = currentKey ?: throw TmdbKeyMissingException()
                val request = chain.request()
                // v4 "read access tokens" are long JWTs sent as Bearer; v3 keys go into the query.
                val authed = if (key.length > 40) {
                    request.newBuilder().header("Authorization", "Bearer $key").build()
                } else {
                    request.newBuilder().url(request.url.newBuilder().addQueryParameter("api_key", key).build()).build()
                }
                chain.proceed(authed)
            }
            .build()
        Retrofit.Builder()
            .baseUrl(Tmdb.BASE_URL)
            .client(http)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(TmdbApi::class.java)
    }

    @Volatile private var currentKey: String? = null

    private suspend fun <T> withKey(block: suspend TmdbApi.() -> T): T {
        currentKey = apiKey()?.trim()?.takeIf { it.isNotEmpty() } ?: throw TmdbKeyMissingException()
        return api.block()
    }

    /** true = key works, false = TMDB rejected it. Network errors are thrown. */
    suspend fun checkKey(): Boolean = try {
        withKey { authentication() }
        true
    } catch (e: HttpException) {
        if (e.code() == 401) false else throw e
    }

    override suspend fun search(query: String): List<TmdbSearchResult> =
        withKey { searchTv(query).results }

    override suspend fun show(id: Int): TmdbShow = withKey {
        val first = tvDetails(id, append = "aggregate_credits,videos", videoLanguages = "de,en")
        val info = json.decodeFromJsonElement<TmdbShowJson>(first)
        val seasonNumbers = info.seasons.filter { it.episodeCount > 0 }.map { it.seasonNumber }
        val seasonJson = mutableMapOf<Int, TmdbSeasonJson>()
        seasonNumbers.chunked(20).forEach { chunk ->
            val obj: JsonObject = tvDetails(id, append = chunk.joinToString(",") { "season/$it" })
            chunk.forEach { n ->
                obj["season/$n"]?.let { seasonJson[n] = json.decodeFromJsonElement(it) }
            }
        }
        toShow(info, seasonJson, LocalDate.now())
    }

    companion object {
        /** YouTube trailer, German before English, official before others; a teaser if no trailer. */
        fun trailerKey(videos: TmdbVideosJson?): String? = videos?.results
            ?.filter { it.site == "YouTube" && it.key.isNotBlank() && (it.type == "Trailer" || it.type == "Teaser") }
            ?.sortedWith(
                compareBy(
                    { if (it.type == "Trailer") 0 else 1 },
                    { if (it.language == "de") 0 else 1 },
                    { if (it.official) 0 else 1 },
                ),
            )
            ?.firstOrNull()?.key

        /** Top 8 cast as "Name als Rolle, …". */
        fun castSummary(credits: TmdbCreditsJson?): String? = credits?.cast
            ?.sortedBy { it.order }
            ?.take(8)
            ?.filter { it.name.isNotBlank() }
            ?.joinToString(", ") { c ->
                val role = c.roles.firstOrNull()?.character?.takeIf(String::isNotBlank)
                if (role != null) "${c.name} als $role" else c.name
            }
            ?.takeIf(String::isNotEmpty)

        /** Keeps only episodes that have already aired, like the Excel "von" column. */
        fun toShow(info: TmdbShowJson, seasons: Map<Int, TmdbSeasonJson>, today: LocalDate): TmdbShow {
            fun aired(date: String?): Boolean =
                date != null && runCatching { !LocalDate.parse(date).isAfter(today) }.getOrDefault(false)
            return TmdbShow(
                id = info.id,
                name = info.name,
                originalName = info.originalName,
                status = info.status,
                posterUrl = info.posterPath?.let { Tmdb.posterUrl(it) },
                nextAirDate = info.nextEpisodeToAir?.airDate,
                nextEpisodeSeason = info.nextEpisodeToAir?.seasonNumber,
                nextEpisodeNumber = info.nextEpisodeToAir?.episodeNumber,
                nextEpisodeName = info.nextEpisodeToAir?.name?.takeIf(String::isNotBlank),
                genres = info.genres.map { it.name.trim() }.filter { it.isNotEmpty() },
                overview = info.overview?.trim()?.takeIf(String::isNotEmpty),
                voteAverage = info.voteAverage?.takeIf { (info.voteCount ?: 0) > 0 },
                voteCount = info.voteCount,
                trailerKey = trailerKey(info.videos),
                castSummary = castSummary(info.credits),
                seasons = seasons.values
                    .sortedBy { it.seasonNumber }
                    .map { s ->
                        TmdbSeason(
                            number = s.seasonNumber,
                            name = s.name,
                            episodes = s.episodes
                                .filter { aired(it.airDate) }
                                .map {
                                    TmdbEpisode(
                                        number = it.episodeNumber,
                                        title = it.name?.takeIf(String::isNotBlank),
                                        airDate = it.airDate,
                                        runtime = it.runtime?.takeIf { min -> min > 0 },
                                    )
                                },
                        )
                    }
                    .filter { it.episodes.isNotEmpty() },
            )
        }
    }
}
