package io.github.mario2712.serientracker.data.export

import java.io.OutputStream
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Minimal XLSX writer (Office Open XML = ZIP of XML files), enough for the Serien export.
 * No library: Apache POI is far too heavy for Android.
 */
class XlsxWriter {

    sealed interface Cell {
        data object Empty : Cell
        data class Num(val value: Double, val style: Style = Style.DEFAULT) : Cell
        data class Text(val value: String, val style: Style = Style.DEFAULT) : Cell
        data class Date(val value: LocalDate) : Cell
        data class Formula(val formula: String, val cached: Double?, val style: Style = Style.DEFAULT) : Cell
    }

    /** Index into cellXfs in styles.xml. */
    enum class Style(val index: Int) { DEFAULT(0), BOLD(1), DATE(2), INTEGER(3), PERCENT(4), CENTER(5), LEFT(6) }

    /** Highlights cells with the orange fill used in the original sheet when [formula] is true. */
    data class ConditionalFormat(val range: String, val formulas: List<String>)

    data class Sheet(
        val name: String,
        val columnWidths: List<Double>,
        val rows: List<List<Cell>>,
        val freezeHeader: Boolean = false,
        val conditionalFormats: List<ConditionalFormat> = emptyList(),
    )

    fun write(sheets: List<Sheet>, out: OutputStream) {
        ZipOutputStream(out).use { zip ->
            fun entry(path: String, content: String) {
                zip.putNextEntry(ZipEntry(path))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            entry("[Content_Types].xml", contentTypes(sheets.size))
            entry("_rels/.rels", ROOT_RELS)
            entry("xl/workbook.xml", workbook(sheets))
            entry("xl/_rels/workbook.xml.rels", workbookRels(sheets.size))
            entry("xl/styles.xml", STYLES)
            sheets.forEachIndexed { i, sheet -> entry("xl/worksheets/sheet${i + 1}.xml", sheetXml(sheet)) }
        }
    }

    private fun contentTypes(count: Int) = buildString {
        append(XML_HEADER)
        append("""<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""")
        append("""<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""")
        append("""<Default Extension="xml" ContentType="application/xml"/>""")
        append("""<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>""")
        append("""<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>""")
        for (i in 1..count) {
            append("""<Override PartName="/xl/worksheets/sheet$i.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>""")
        }
        append("</Types>")
    }

    private fun workbook(sheets: List<Sheet>) = buildString {
        append(XML_HEADER)
        append("""<workbook xmlns="$NS_MAIN" xmlns:r="$NS_REL"><sheets>""")
        sheets.forEachIndexed { i, s ->
            append("""<sheet name="${escape(s.name)}" sheetId="${i + 1}" r:id="rId${i + 1}"/>""")
        }
        append("</sheets></workbook>")
    }

    private fun workbookRels(count: Int) = buildString {
        append(XML_HEADER)
        append("""<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
        for (i in 1..count) {
            append("""<Relationship Id="rId$i" Type="$NS_REL/worksheet" Target="worksheets/sheet$i.xml"/>""")
        }
        append("""<Relationship Id="rIdStyles" Type="$NS_REL/styles" Target="styles.xml"/>""")
        append("</Relationships>")
    }

    private fun sheetXml(sheet: Sheet) = buildString {
        append(XML_HEADER)
        append("""<worksheet xmlns="$NS_MAIN" xmlns:r="$NS_REL">""")
        append("<sheetViews><sheetView workbookViewId=\"0\">")
        if (sheet.freezeHeader) {
            append("""<pane ySplit="1" topLeftCell="A2" activePane="bottomLeft" state="frozen"/>""")
        }
        append("</sheetView></sheetViews>")
        append("""<sheetFormatPr defaultRowHeight="15"/>""")
        if (sheet.columnWidths.isNotEmpty()) {
            append("<cols>")
            sheet.columnWidths.forEachIndexed { i, w ->
                append("""<col min="${i + 1}" max="${i + 1}" width="$w" customWidth="1"/>""")
            }
            append("</cols>")
        }
        append("<sheetData>")
        sheet.rows.forEachIndexed { r, row ->
            val rowNumber = r + 1
            append("""<row r="$rowNumber">""")
            row.forEachIndexed { c, cell -> appendCell("${column(c)}$rowNumber", cell) }
            append("</row>")
        }
        append("</sheetData>")
        var priority = 1
        sheet.conditionalFormats.forEach { cf ->
            append("""<conditionalFormatting sqref="${cf.range}">""")
            cf.formulas.forEach { f ->
                append("""<cfRule type="expression" dxfId="0" priority="${priority++}"><formula>${escape(f)}</formula></cfRule>""")
            }
            append("</conditionalFormatting>")
        }
        append("</worksheet>")
    }

    private fun StringBuilder.appendCell(ref: String, cell: Cell) {
        when (cell) {
            Cell.Empty -> Unit
            is Cell.Num -> append("""<c r="$ref" s="${cell.style.index}"><v>${number(cell.value)}</v></c>""")
            is Cell.Text -> append(
                """<c r="$ref" s="${cell.style.index}" t="inlineStr"><is><t xml:space="preserve">${escape(cell.value)}</t></is></c>""",
            )
            is Cell.Date -> append("""<c r="$ref" s="${Style.DATE.index}"><v>${excelSerial(cell.value)}</v></c>""")
            is Cell.Formula -> {
                append("""<c r="$ref" s="${cell.style.index}"><f>${escape(cell.formula)}</f>""")
                cell.cached?.let { append("<v>${number(it)}</v>") }
                append("</c>")
            }
        }
    }

    companion object {
        private const val XML_HEADER = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>"""
        private const val NS_MAIN = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
        private const val NS_REL = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"

        private const val ROOT_RELS = XML_HEADER +
            """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""" +
            """<Relationship Id="rId1" Type="$NS_REL/officeDocument" Target="xl/workbook.xml"/>""" +
            "</Relationships>"

        // cellXfs order must match Style: default, bold, date, integer, percent, center, left.
        private const val STYLES = XML_HEADER +
            """<styleSheet xmlns="$NS_MAIN">""" +
            """<numFmts count="2"><numFmt numFmtId="164" formatCode="dd/mm/yyyy"/>""" +
            """<numFmt numFmtId="165" formatCode="0.00\ %"/></numFmts>""" +
            """<fonts count="2"><font><sz val="11"/><name val="Calibri"/></font>""" +
            """<font><b/><sz val="11"/><name val="Calibri"/></font></fonts>""" +
            """<fills count="2"><fill><patternFill patternType="none"/></fill>""" +
            """<fill><patternFill patternType="gray125"/></fill></fills>""" +
            """<borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders>""" +
            """<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>""" +
            """<cellXfs count="7">""" +
            """<xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>""" +
            """<xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1"/>""" +
            """<xf numFmtId="164" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>""" +
            """<xf numFmtId="1" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>""" +
            """<xf numFmtId="165" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>""" +
            """<xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0" applyAlignment="1"><alignment horizontal="center"/></xf>""" +
            """<xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0" applyAlignment="1"><alignment horizontal="left"/></xf>""" +
            "</cellXfs>" +
            """<cellStyles count="1"><cellStyle name="Standard" xfId="0" builtinId="0"/></cellStyles>""" +
            """<dxfs count="1"><dxf><fill><patternFill><bgColor rgb="FFFFC000"/></patternFill></fill></dxf></dxfs>""" +
            "</styleSheet>"

        /** 0 → A, 25 → Z, 26 → AA. */
        fun column(index: Int): String {
            var n = index + 1
            val sb = StringBuilder()
            while (n > 0) {
                val rem = (n - 1) % 26
                sb.insert(0, 'A' + rem)
                n = (n - 1) / 26
            }
            return sb.toString()
        }

        /** Excel's day number (1900 date system): 1899-12-30 is day 0. */
        fun excelSerial(date: LocalDate): Long = date.toEpochDay() + 25569

        private fun number(v: Double): String =
            if (v == Math.floor(v) && !v.isInfinite() && kotlin.math.abs(v) < 1e15) v.toLong().toString() else v.toString()

        fun escape(s: String): String = buildString(s.length) {
            for (ch in s) {
                when (ch) {
                    '&' -> append("&amp;")
                    '<' -> append("&lt;")
                    '>' -> append("&gt;")
                    '"' -> append("&quot;")
                    '\t', '\n', '\r' -> append(ch)
                    else -> if (ch < ' ') Unit else append(ch)
                }
            }
        }
    }
}
