package io.github.mario2712.serientracker.domain

/** Pure progress rules, kept free of Android types so they are unit-testable. */
object Progress {

    /**
     * Status the series should get after the watched count changed.
     * Only moves forward automatically; PAUSED and DROPPED stay unless all is watched.
     */
    fun autoStatus(current: WatchStatus, watched: Int, total: Int): WatchStatus = when {
        total > 0 && watched >= total -> WatchStatus.COMPLETED
        current == WatchStatus.COMPLETED && watched < total -> WatchStatus.WATCHING
        current == WatchStatus.PLAN_TO_WATCH && watched > 0 -> WatchStatus.WATCHING
        else -> current
    }

    fun fraction(watched: Int, total: Int): Float =
        if (total <= 0) 0f else (watched.toFloat() / total).coerceIn(0f, 1f)

    fun episodeCode(season: Int, episode: Int): String =
        "S%02dE%02d".format(season, episode)
}
