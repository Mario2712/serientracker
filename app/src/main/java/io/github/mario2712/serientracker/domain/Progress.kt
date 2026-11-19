package io.github.mario2712.serientracker.domain

/** Pure progress rules, kept free of Android types so they are unit-testable. */
object Progress {

    /** Why the episodes of a series changed. */
    enum class Change { WATCHED, UNWATCHED, NEW_EPISODES }

    /**
     * Status the series should get after its episodes changed.
     *
     * @param isEnded TMDB says no more episodes will come (null = unknown).
     */
    fun autoStatus(
        current: WatchStatus,
        watched: Int,
        total: Int,
        change: Change,
        isEnded: Boolean? = null,
    ): WatchStatus {
        if (total <= 0) return current
        if (watched >= total) {
            return when {
                current == WatchStatus.COMPLETED || isEnded == true -> WatchStatus.COMPLETED
                current == WatchStatus.DROPPED && change != Change.WATCHED -> WatchStatus.DROPPED
                current == WatchStatus.PLAN_TO_WATCH && change == Change.NEW_EPISODES -> WatchStatus.PLAN_TO_WATCH
                else -> WatchStatus.CAUGHT_UP
            }
        }
        return when (change) {
            Change.WATCHED ->
                if (current == WatchStatus.DROPPED || current == WatchStatus.WATCHING) current else WatchStatus.WATCHING
            Change.UNWATCHED ->
                if (current == WatchStatus.COMPLETED || current == WatchStatus.CAUGHT_UP) WatchStatus.WATCHING else current
            // Sync found episodes the user has not seen: not caught up any more, but not actively watching either.
            Change.NEW_EPISODES ->
                if (current == WatchStatus.COMPLETED || current == WatchStatus.CAUGHT_UP) WatchStatus.PAUSED else current
        }
    }

    fun fraction(watched: Int, total: Int): Float =
        if (total <= 0) 0f else (watched.toFloat() / total).coerceIn(0f, 1f)

    fun episodeCode(season: Int, episode: Int): String =
        if (season == 0) "Special %d".format(episode) else "S%02dE%02d".format(season, episode)

    /**
     * Whether an episode counts as watched for an Excel-style position
     * ("last watched: season [lastSeason], episode [lastEpisode], special [lastSpecial]").
     * Season 0 holds the specials.
     */
    fun isWatchedAtPosition(
        season: Int,
        episode: Int,
        lastSeason: Int?,
        lastEpisode: Int?,
        lastSpecial: Int?,
    ): Boolean = when {
        season == 0 -> episode <= (lastSpecial ?: 0)
        lastSeason == null || lastSeason <= 0 -> false
        season < lastSeason -> true
        season == lastSeason -> episode <= (lastEpisode ?: 0)
        else -> false
    }
}
