package io.github.mario2712.serientracker.domain

/** One row of the "Serien" sheet, as written by tools/convert_excel.py. */
@kotlinx.serialization.Serializable
data class ExcelRow(
    val status: Int? = null,
    val finished: Int? = null,
    val title: String,
    val who: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val special: Int? = null,
    val lastAired: String? = null,
    val unseen: Boolean? = null,
    val nextAired: String? = null,
    val maxSeason: Int? = null,
    val maxEpisode: Int? = null,
    val maxSpecial: Int? = null,
    val genre: String? = null,
    val updated: String? = null,
    val watched: Int? = null,
    val total: Int? = null,
)

@kotlinx.serialization.Serializable
data class ExcelSeed(val version: Int, val series: List<ExcelRow>)

object ExcelMapping {

    /**
     * Excel: fertig=1 → done. Status 1 = watching now, 2 = not watching at the moment,
     * 0 = not started. Status 2 with everything watched means waiting for new episodes.
     */
    fun status(row: ExcelRow): WatchStatus = when {
        row.finished == 1 -> WatchStatus.COMPLETED
        row.status == 1 -> WatchStatus.WATCHING
        row.status == 2 -> {
            val caughtUp = row.watched != null && row.watched > 0 && row.watched == row.total
            if (caughtUp) WatchStatus.CAUGHT_UP else WatchStatus.PAUSED
        }
        else -> WatchStatus.PLAN_TO_WATCH
    }

    fun watcher(row: ExcelRow): Watcher? = Watcher.fromExcel(row.who)

    /** ISO date (yyyy-MM-dd) → epoch day, or null. */
    fun epochDay(iso: String?): Long? =
        iso?.let { runCatching { java.time.LocalDate.parse(it).toEpochDay() }.getOrNull() }

    /** Problems worth showing the user, e.g. "gesehen" not matching the position. */
    fun warnings(row: ExcelRow): List<String> = buildList {
        if (row.season != null && row.season > 0 && row.total == null) add("„von“ fehlt")
        if (row.season == 1 && row.watched != null && row.episode != null &&
            row.watched != row.episode + (row.special ?: 0)
        ) {
            add("gesehen (${row.watched}) passt nicht zu S1E${row.episode}")
        }
    }
}
