package io.github.mario2712.serientracker.ui.serieslist

import io.github.mario2712.serientracker.data.local.SeriesOverview
import io.github.mario2712.serientracker.data.stats.StatsCalculator
import io.github.mario2712.serientracker.domain.Progress

enum class SeriesSort(val label: String) {
    RECENT("Zuletzt geschaut"),
    TITLE("Titel A–Z"),
    PROGRESS("Fortschritt"),
    RATING("Bewertung"),
    NEXT_AIR("Nächste Ausstrahlung"),
    ;

    companion object {
        fun from(name: String?): SeriesSort = entries.firstOrNull { it.name == name } ?: RECENT
    }
}

object SeriesListLogic {

    /** All genres that occur, alphabetically ("Drama, Krimi" counts for both). */
    fun genres(items: List<SeriesOverview>): List<String> =
        items.flatMap { StatsCalculator.genres(it.series.genre) }.distinct().sortedBy { it.lowercase() }

    fun matchesGenre(item: SeriesOverview, genre: String?): Boolean =
        genre == null || StatsCalculator.genres(item.series.genre).any { it.equals(genre, ignoreCase = true) }

    /** Own series rating, else the average of the rated episodes. */
    fun rating(item: SeriesOverview): Double? = item.series.rating?.toDouble() ?: item.avgEpisodeRating

    fun sort(items: List<SeriesOverview>, sort: SeriesSort): List<SeriesOverview> = when (sort) {
        // The database already delivers "recently watched" order.
        SeriesSort.RECENT -> items
        SeriesSort.TITLE -> items.sortedBy { it.series.title.lowercase() }
        SeriesSort.PROGRESS -> items.sortedWith(
            compareByDescending<SeriesOverview> { Progress.fraction(it.watchedEpisodes, it.totalEpisodes) }
                .thenBy { it.series.title.lowercase() },
        )
        SeriesSort.RATING -> items.sortedWith(
            compareBy<SeriesOverview> { rating(it) == null }
                .thenByDescending { rating(it) ?: 0.0 }
                .thenBy { it.series.title.lowercase() },
        )
        SeriesSort.NEXT_AIR -> items.sortedWith(
            compareBy<SeriesOverview> { it.series.nextAirDate == null }
                .thenBy { it.series.nextAirDate ?: Long.MAX_VALUE }
                .thenBy { it.series.title.lowercase() },
        )
    }
}
