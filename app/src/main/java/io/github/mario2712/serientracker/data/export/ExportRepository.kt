package io.github.mario2712.serientracker.data.export

import io.github.mario2712.serientracker.data.local.AppDatabase
import io.github.mario2712.serientracker.data.local.SeriesWithSeasons
import io.github.mario2712.serientracker.data.local.counts
import io.github.mario2712.serientracker.data.export.XlsxWriter.Cell
import io.github.mario2712.serientracker.data.export.XlsxWriter.Style
import io.github.mario2712.serientracker.domain.ExcelRow
import java.io.OutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Writes the app state as Excel file in the layout of the original "Serienliste.xlsx". */
class ExportRepository(
    private val db: AppDatabase,
    private val appVersion: String,
) {

    suspend fun exportExcel(out: OutputStream, today: LocalDate = LocalDate.now()) {
        val all = db.seriesDao().getAllWithSeasons()
        XlsxWriter().write(listOf(serienSheet(ExcelExport.rows(all)), infoSheet(all, today)), out)
    }

    fun serienSheet(rows: List<ExcelRow>): XlsxWriter.Sheet {
        val header = HEADERS.map { Cell.Text(it, Style.BOLD) }
        val body = rows.mapIndexed { i, r -> rowCells(r, rowNumber = i + 2) }
        return XlsxWriter.Sheet(
            name = "Serien",
            columnWidths = WIDTHS,
            rows = listOf(header) + body,
            freezeHeader = true,
            conditionalFormats = listOf(
                // "nächste" (K) turns orange when the date has passed or there are unseen episodes (J).
                XlsxWriter.ConditionalFormat("K1:K1048576", listOf("IF(K1<>\"\",K1<=TODAY(),0)", "J1=\"x\"")),
            ),
        )
    }

    private fun rowCells(r: ExcelRow, rowNumber: Int): List<Cell> = listOf(
        num(r.status),
        num(r.finished),
        // Pure numbers (e.g. "24", "1899") are numbers in the original sheet too.
        if (r.title.matches(Regex("[1-9][0-9]{0,8}"))) Cell.Num(r.title.toDouble(), Style.LEFT) else Cell.Text(r.title, Style.LEFT),
        r.originalTitle?.let { Cell.Text(it, Style.LEFT) } ?: Cell.Empty,
        r.who?.let { Cell.Text(it, Style.CENTER) } ?: Cell.Empty,
        num(r.season),
        num(r.episode),
        num(r.special),
        date(r.lastAired),
        if (r.unseen == true) Cell.Text("x", Style.CENTER) else Cell.Empty,
        date(r.nextAired),
        num(r.maxSeason, Style.INTEGER),
        num(r.maxEpisode, Style.INTEGER),
        num(r.maxSpecial, Style.INTEGER),
        r.genre?.let { Cell.Text(it) } ?: Cell.Empty,
        date(r.updated),
        num(r.watched),
        num(r.total),
        if (r.watched != null && r.total != null && r.total > 0) {
            Cell.Formula("Q$rowNumber/R$rowNumber", r.watched.toDouble() / r.total, Style.PERCENT)
        } else {
            Cell.Empty
        },
    )

    fun infoSheet(all: List<SeriesWithSeasons>, today: LocalDate): XlsxWriter.Sheet {
        fun row(key: String, value: Cell) = listOf(Cell.Text(key, Style.BOLD), value)
        val episodes = all.sumOf { s -> s.seasons.sumOf { season -> season.episodes.count { it.counts } } }
        return XlsxWriter.Sheet(
            name = "Info",
            columnWidths = listOf(28.0, 40.0),
            rows = listOf(
                row("App", Cell.Text("Serientracker")),
                row("Version", Cell.Text(appVersion)),
                row("Entwickler", Cell.Text(DEVELOPER)),
                row("Exportiert am", Cell.Date(today)),
                row("Serien", Cell.Num(all.size.toDouble())),
                row("davon mit TMDB abgeglichen", Cell.Num(all.count { it.series.tmdbId != null }.toDouble())),
                row("Folgen in der App", Cell.Num(episodes.toDouble())),
                row("Folgendaten", Cell.Text("TMDB (themoviedb.org)")),
            ),
        )
    }

    private fun num(v: Int?, style: Style = Style.DEFAULT): Cell = v?.let { Cell.Num(it.toDouble(), style) } ?: Cell.Empty

    private fun date(iso: String?): Cell =
        iso?.let { runCatching { Cell.Date(LocalDate.parse(it)) }.getOrNull() } ?: Cell.Empty

    companion object {
        const val DEVELOPER = "DI Mario Siller"
        const val MIME_XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

        /** Original columns plus "Originaltitel" right after "Serie". */
        val HEADERS = listOf(
            "Status", "fertig", "Serie", "Originaltitel", "Wer", "Staffel", "Episode", "Special",
            "Letzte Ausstrahlung", "ungesehen", "nächste", "maxStaffel", "maxEpisode", "maxSpecial", "Genre",
            "aktualisiert", "gesehen", "von", "Anteil",
        )

        /** Column widths of the original sheet (plus 40 for "Originaltitel"). */
        val WIDTHS = listOf(
            3.71, 6.0, 54.57, 40.0, 4.86, 6.85, 8.0, 7.42, 18.57, 11.43,
            11.43, 10.57, 11.71, 11.43, 14.86, 11.43, 8.57, 5.57, 8.57,
        )

        fun excelFileName(date: LocalDate = LocalDate.now()): String =
            "serienliste_${date.format(DateTimeFormatter.BASIC_ISO_DATE)}.xlsx"
    }
}
