package com.asta.cherryoffice.excel

import com.asta.cherryoffice.formats.OfficeArchive
import com.asta.cherryoffice.formats.OfficeArchive.elements
import org.w3c.dom.Element
import java.io.InputStream
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object XlsxFileReader {
    private const val MAX_SHEET_ROWS = 10000
    private const val MAX_SHEET_COLS = 256

    fun readSpreadsheet(inputStream: InputStream, fileName: String): SpreadsheetDocument =
        when (fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)) {
            "csv", "tsv", "txt" -> readCsv(inputStream, fileName)
            "xls" -> OfficeSpreadsheetReader.read(inputStream, fileName)
            else -> readXlsx(inputStream, fileName)
        }

    private data class Style(
        val numberFormat: String = "General", val bold: Boolean = false,
        val italic: Boolean = false, val color: String? = null,
        val fill: String? = null, val alignment: String = "", val wrap: Boolean = false
    )

    fun readXlsx(inputStream: InputStream, fileName: String): SpreadsheetDocument {
        val entries = OfficeArchive.read(inputStream) { it.startsWith("xl/") && (it.endsWith(".xml") || it.endsWith(".rels")) }
        val workbook = OfficeArchive.xml(entries["xl/workbook.xml"]
            ?: throw IllegalArgumentException("This is not a readable XLSX workbook. It may be damaged or password-protected."))
        val rels = entries["xl/_rels/workbook.xml.rels"]?.let { bytes ->
            elements(OfficeArchive.xml(bytes), "Relationship").filter { it.getAttribute("TargetMode") != "External" }
                .associate { it.getAttribute("Id") to OfficeArchive.resolve("xl", it.getAttribute("Target")) }
        }.orEmpty()
        val strings = entries["xl/sharedStrings.xml"]?.let { bytes ->
            elements(OfficeArchive.xml(bytes), "si").map { si -> elements(si, "t").joinToString("") { it.textContent } }
        }.orEmpty()
        val styles = parseStyles(entries["xl/styles.xml"])
        val date1904 = elements(workbook, "workbookPr").firstOrNull()?.getAttribute("date1904") in listOf("1", "true")
        val warnings = mutableSetOf<String>()
        val sheets = elements(workbook, "sheet").mapNotNull { sheet ->
            val id = sheet.getAttributeNS("http://schemas.openxmlformats.org/officeDocument/2006/relationships", "id")
                .ifBlank { sheet.getAttribute("r:id") }
            val path = rels[id] ?: throw IllegalArgumentException("A worksheet relationship is missing.")
            if (!path.contains("/worksheets/")) {
                warnings.add("Chart sheets are not displayed.")
                return@mapNotNull null
            }
            val bytes = entries[path] ?: throw IllegalArgumentException("A worksheet is missing from this workbook.")
            val xml = OfficeArchive.xml(bytes)
            val rows = mutableListOf<RowData>()
            var columns = 0
            val rowNodes = elements(xml, "row")
            if (rowNodes.size > MAX_SHEET_ROWS) warnings.add("Preview limited to $MAX_SHEET_ROWS rows per sheet.")
            for (row in rowNodes.take(MAX_SHEET_ROWS)) {
                val index = row.getAttribute("r").toIntOrNull() ?: (rows.size + 1)
                val cells = linkedMapOf<Int, CellData>()
                for (cell in elements(row, "c")) {
                    val col = cell.getAttribute("r").takeIf { it.isNotBlank() }?.let(::columnRefToIndex) ?: cells.size
                    if (col >= MAX_SHEET_COLS) {
                        warnings.add("Preview limited to $MAX_SHEET_COLS columns per sheet.")
                        continue
                    }
                    val style = styles.getOrElse(cell.getAttribute("s").toIntOrNull() ?: 0) { Style() }
                    val raw = elements(cell, "v").firstOrNull()?.textContent.orEmpty()
                    val formula = elements(cell, "f").firstOrNull()?.textContent
                    val type = cell.getAttribute("t")
                    val value = when (type) {
                        "s" -> strings.getOrNull(raw.toIntOrNull() ?: -1).orEmpty()
                        "inlineStr" -> elements(cell, "t").joinToString("") { it.textContent }
                        "b" -> if (raw == "1") "TRUE" else "FALSE"
                        "str", "e", "d" -> raw
                        else -> if (raw.isEmpty() && formula != null) "=$formula" else formatNumber(raw, style.numberFormat, date1904)
                    }
                    if (formula != null) warnings.add("Formulas show saved results; they are not recalculated.")
                    cells[col] = CellData(value, false, col, index, formula, style.bold, style.italic,
                        style.color, style.fill, style.alignment, style.wrap)
                }
                if (cells.isNotEmpty()) {
                    val last = cells.keys.maxOrNull() ?: 0
                    columns = maxOf(columns, last + 1)
                    rows.add(RowData(index, (0..last).map { cells[it] ?: CellData("", columnIndex = it, rowIndex = index) }))
                }
            }
            if (elements(xml, "mergeCell").isNotEmpty()) warnings.add("Merged cell values appear in their starting cells.")
            val widths = mutableMapOf<Int, Float>()
            for (col in elements(xml, "col")) {
                val start = col.getAttribute("min").toIntOrNull() ?: continue
                val end = col.getAttribute("max").toIntOrNull() ?: start
                val width = col.getAttribute("width").toFloatOrNull() ?: continue
                for (c in start..minOf(end, MAX_SHEET_COLS)) widths[c - 1] = (width * 7 + 12).coerceIn(40f, 400f)
            }
            SheetData(sheet.getAttribute("name").ifBlank { "Sheet ${rows.size + 1}" }, rows, columns, widths)
        }
        require(sheets.isNotEmpty()) { "This workbook has no readable worksheets." }
        return SpreadsheetDocument(fileName, sheets, warnings.toList())
    }

    private fun parseStyles(bytes: ByteArray?): List<Style> {
        if (bytes == null) return listOf(Style())
        val xml = OfficeArchive.xml(bytes)
        val formats = mutableMapOf(0 to "General", 1 to "0", 2 to "0.00", 3 to "#,##0", 4 to "#,##0.00",
            9 to "0%", 10 to "0.00%", 14 to "mm-dd-yy", 15 to "d-mmm-yy", 16 to "d-mmm", 17 to "mmm-yy",
            18 to "h:mm AM/PM", 19 to "h:mm:ss AM/PM", 20 to "h:mm", 21 to "h:mm:ss", 22 to "m/d/yy h:mm",
            45 to "mm:ss", 46 to "[h]:mm:ss", 47 to "mmss.0")
        elements(xml, "numFmt").forEach { el -> el.getAttribute("numFmtId").toIntOrNull()?.let { formats[it] = el.getAttribute("formatCode") } }
        val fonts = elements(xml, "fonts").firstOrNull()?.let { elements(it, "font") }.orEmpty()
        val fills = elements(xml, "fills").firstOrNull()?.let { elements(it, "fill") }.orEmpty()
        fun rgb(el: Element?, tag: String): String? = el?.let { elements(it, tag).firstOrNull()?.getAttribute("rgb") }
            ?.takeIf { it.length == 6 || it.length == 8 }?.takeLast(6)
        return elements(xml, "cellXfs").firstOrNull()?.let { xfs -> elements(xfs, "xf").map { xf ->
            val font = fonts.getOrNull(xf.getAttribute("fontId").toIntOrNull() ?: 0)
            val fill = fills.getOrNull(xf.getAttribute("fillId").toIntOrNull() ?: 0)
            val alignment = elements(xf, "alignment").firstOrNull()
            Style(formats[xf.getAttribute("numFmtId").toIntOrNull()] ?: "General",
                font != null && elements(font, "b").any { it.getAttribute("val") !in listOf("0", "false") },
                font != null && elements(font, "i").any { it.getAttribute("val") !in listOf("0", "false") },
                rgb(font, "color"), rgb(fill, "fgColor"), alignment?.getAttribute("horizontal").orEmpty(),
                alignment?.getAttribute("wrapText") in listOf("1", "true"))
        } }.orEmpty()
    }

    private fun formatNumber(raw: String, format: String, date1904: Boolean): String {
        val number = raw.toDoubleOrNull()?.takeIf { it.isFinite() } ?: return raw
        if (format == "General" || format == "@") return raw
        val clean = format.replace(Regex("\\\"[^\\\"]*\\\"|\\\\."), "").replace(Regex("\\[[^]]*]"), "")
        return try {
            if (clean.contains(Regex("[ydhs]", RegexOption.IGNORE_CASE)) || clean.contains(Regex("m{1,4}[-/]", RegexOption.IGNORE_CASE))) {
                // Excel's 1900 leap-day bug: day 60 is not a real Gregorian date.
                if (!date1904 && number >= 60 && number < 61) return "1900-02-29"
                val days = number - (if (date1904) 24107 else if (number < 60) 25568 else 25569)
                val pattern = if (clean.contains('h', true) || clean.contains('s', true)) {
                    if (clean.contains('y', true) || clean.contains('d', true)) "yyyy-MM-dd HH:mm:ss" else "HH:mm:ss"
                } else "yyyy-MM-dd"
                SimpleDateFormat(pattern, Locale.ROOT).apply { timeZone = TimeZone.getTimeZone("UTC") }
                    .format(Date((days * 86400000).toLong()))
            } else {
                val section = format.substringBefore(';').replace(Regex("\\[[^]]*]"), "")
                    .replace(Regex("_.|\\*."), "")
                DecimalFormat(section.replace("\\\\", ""), DecimalFormatSymbols(Locale.US)).format(number)
            }
        } catch (_: IllegalArgumentException) { raw }
    }

    fun columnRefToIndex(cellRef: String): Int {
        var col = 0
        for (char in cellRef.takeWhile { it.isLetter() }.uppercase(Locale.ROOT)) col = col * 26 + (char - 'A' + 1)
        return (col - 1).coerceAtLeast(0)
    }

    fun indexToColumnName(index: Int): String {
        var n = index + 1
        val result = StringBuilder()
        while (n > 0) { result.append('A' + (n - 1) % 26); n = (n - 1) / 26 }
        return result.reverse().toString()
    }

    fun readCsv(inputStream: InputStream, fileName: String): SpreadsheetDocument {
        val text = inputStream.reader(Charsets.UTF_8).use { it.readTextCapped() }.removePrefix("\uFEFF")
        val firstRecord = text.lineSequence().firstOrNull().orEmpty()
        fun delimiterCount(delimiter: Char): Int {
            var quoted = false
            return firstRecord.count { c -> if (c == '"') quoted = !quoted; c == delimiter && !quoted }
        }
        val delimiter = if (fileName.endsWith(".tsv", true)) '\t' else listOf(',', ';', '\t').maxBy { delimiterCount(it) }
        val rows = mutableListOf<RowData>()
        val cells = mutableListOf<String>()
        val value = StringBuilder()
        var quoted = false
        var i = 0
        var truncated = false
        fun finishRow() {
            cells.add(value.toString()); value.clear()
            val index = rows.size + 1
            if (cells.size > MAX_SHEET_COLS) truncated = true
            rows.add(RowData(index, cells.take(MAX_SHEET_COLS).mapIndexed { col, v -> CellData(v, index == 1, col, index) }))
            cells.clear()
        }
        while (i < text.length && rows.size < MAX_SHEET_ROWS) {
            val c = text[i]
            when {
                c == '"' && (quoted || value.isEmpty()) -> {
                    if (quoted && i + 1 < text.length && text[i + 1] == '"') { value.append('"'); i++ } else quoted = !quoted
                }
                c == delimiter && !quoted -> { cells.add(value.toString()); value.clear() }
                (c == '\r' || c == '\n') && !quoted -> {
                    finishRow()
                    if (c == '\r' && i + 1 < text.length && text[i + 1] == '\n') i++
                }
                else -> value.append(c)
            }
            i++
        }
        require(!quoted) { "The CSV contains an unclosed quoted field." }
        if (i < text.length) truncated = true
        if ((value.isNotEmpty() || cells.isNotEmpty()) && rows.size < MAX_SHEET_ROWS) finishRow()
        return SpreadsheetDocument(fileName, listOf(SheetData("Sheet 1", rows, rows.maxOfOrNull { it.cells.size } ?: 0)),
            if (truncated) listOf("Preview limited to $MAX_SHEET_ROWS rows and $MAX_SHEET_COLS columns.") else emptyList())
    }

    private fun java.io.Reader.readTextCapped(): String {
        val text = StringBuilder()
        val buffer = CharArray(8192)
        while (true) {
            val count = read(buffer)
            if (count < 0) break
            require(text.length + count <= 16 * 1024 * 1024) { "This text file is too large to preview (maximum 16 MB)." }
            text.append(buffer, 0, count)
        }
        return text.toString()
    }
}
