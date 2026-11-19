package io.github.mario2712.serientracker.data.export

import io.github.mario2712.serientracker.data.local.SeriesWithSeasons
import io.github.mario2712.serientracker.data.local.isWatched
import io.github.mario2712.serientracker.domain.ExcelRow
import io.github.mario2712.serientracker.domain.WatchStatus
import java.time.LocalDate

/**
 * Turns the app state back into rows of the original "Serien" sheet, following the
 * Excel logic (last watched position, max available, gesehen/von, ungesehen, nächste).
 */
object ExcelExport {

    fun rows(all: List<SeriesWithSeasons>): List<ExcelRow> = sort(all.map(::toRow))

    /** Original order: Status 2 → 1 → not started → finished, then by title. */
    fun sort(rows: List<ExcelRow>): List<ExcelRow> = rows.sortedWith(
        compareBy<ExcelRow> {
            when {
                it.finished == 1 -> 3
                it.status == 2 -> 0
                it.status == 1 -> 1
                else -> 2
            }
        }.thenBy { it.title.lowercase() },
    )

    fun toRow(data: SeriesWithSeasons): ExcelRow {
        val s = data.series
        val (status, finished) = when (s.status) {
            WatchStatus.COMPLETED -> 0 to 1
            WatchStatus.WATCHING -> 1 to 0
            WatchStatus.CAUGHT_UP, WatchStatus.PAUSED, WatchStatus.DROPPED -> 2 to 0
            WatchStatus.PLAN_TO_WATCH -> 0 to 0
        }
        val base = ExcelRow(
            status = status,
            finished = finished,
            title = s.title,
            who = s.watcher?.excelCode,
            genre = s.genre,
            updated = s.infoUpdatedAt.iso(),
            nextAired = s.nextAirDate.iso(),
        )
        val ip = data.importProgress
        val hasEpisodes = data.seasons.any { it.episodes.isNotEmpty() }
        if (ip != null && !hasEpisodes) {
            // Not matched with TMDB yet: the Excel values are still the truth.
            return base.copy(
                season = ip.lastSeason,
                episode = ip.lastEpisode,
                special = ip.lastSpecial,
                lastAired = s.lastWatchedAirDate.iso(),
                unseen = when (ip.unseen) {
                    true -> true
                    false -> null
                    null -> unseenFallback(ip.watched, ip.total)
                },
                maxSeason = ip.maxSeason,
                maxEpisode = ip.maxEpisode,
                maxSpecial = ip.maxSpecial,
                watched = ip.watched,
                total = ip.total,
            )
        }
        return fromEpisodes(data, base)
    }

    private fun fromEpisodes(data: SeriesWithSeasons, base: ExcelRow): ExcelRow {
        val regular = data.seasons.filter { it.season.number > 0 }
        val specials = data.seasons.filter { it.season.number == 0 }.flatMap { it.episodes }
        val all = data.seasons.flatMap { it.episodes }
        val watchedCount = all.count { it.isWatched }
        val total = all.size

        val lastSeasonNumber = regular.filter { it.episodes.isNotEmpty() }.maxOfOrNull { it.season.number }
        val maxEpisode = regular.firstOrNull { it.season.number == lastSeasonNumber }?.episodes?.maxOfOrNull { it.number }
        val unseen = total > 0 && watchedCount < total
        val latestAired = all.mapNotNull { it.airDate }.maxOrNull()
        val next = base.nextAired ?: if (unseen) latestAired.iso() else null
        val common = base.copy(
            maxSeason = lastSeasonNumber,
            maxEpisode = maxEpisode,
            unseen = if (unseen) true else null,
            nextAired = next,
        )
        if (watchedCount == 0) return common // not started: position and counts stay empty

        val lastRegular = regular
            .flatMap { season -> season.episodes.filter { it.isWatched }.map { season.season.number to it } }
            .maxWithOrNull(compareBy({ it.first }, { it.second.number }))
        val lastSpecial = specials.filter { it.isWatched }.maxByOrNull { it.number }
        val lastAired = listOfNotNull(lastRegular?.second?.airDate, lastSpecial?.airDate).maxOrNull()
            ?: data.series.lastWatchedAirDate
        return common.copy(
            season = lastRegular?.first ?: 0,
            episode = lastRegular?.second?.number ?: 0,
            special = lastSpecial?.number ?: 0,
            lastAired = lastAired.iso(),
            maxSpecial = specials.maxOfOrNull { it.number } ?: 0,
            watched = watchedCount,
            total = total,
        )
    }

    private fun unseenFallback(watched: Int?, total: Int?): Boolean? =
        if (watched == null || total == null || watched < total) true else null

    private fun Long?.iso(): String? = this?.let { LocalDate.ofEpochDay(it).toString() }
}
