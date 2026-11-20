package io.github.mario2712.serientracker.data.remote

import kotlinx.serialization.json.JsonObject
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/** TMDB v3 endpoints. Authentication is added by [TmdbClient]'s interceptor. */
interface TmdbApi {

    @GET("authentication")
    suspend fun authentication(): JsonObject

    @GET("search/tv")
    suspend fun searchTv(
        @Query("query") query: String,
        @Query("language") language: String = "de-DE",
    ): TmdbSearchResponse

    /** Show details; `append_to_response=season/1,season/2` adds up to 20 full seasons. */
    @GET("tv/{id}")
    suspend fun tvDetails(
        @Path("id") id: Int,
        @Query("append_to_response") append: String? = null,
        @Query("language") language: String = "de-DE",
        /** Languages for appended videos (otherwise only German ones, which are rare). */
        @Query("include_video_language") videoLanguages: String? = null,
    ): JsonObject
}
