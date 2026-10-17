package com.asta.cherryoffice.excel

import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.w3c.dom.Node

object XlsxFileReader {

    private const val MAX_SHEET_ROWS = 1000
    private const val MAX_SHEET_COLS = 60

    fun readSpreadsheet(inputStream: InputStream, fileName: String): SpreadsheetDocument {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return if (ext in listOf("csv", "tsv", "txt")) {
            readCsv(inputStream, fileName)
        } else {
            readXlsx(inputStream, fileName)
        }
    }

    fun readXlsx(inputStream: InputStream, fileName: String): SpreadsheetDocument {
        val entries = mutableMapOf<String, ByteArray>()
        ZipInputStream(inputStream).use { zipStream ->
            var entry: ZipEntry? = zipStream.nextEntry
            while (entry != null) {
                val name = entry.name.replace('\\', '/')
                if (name.startsWith("xl/") || name == "[Content_Types].xml") {
                    val baos = ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    var count: Int
                    while (zipStream.read(buffer).also { count = it } != -1) {
                        baos.write(buffer, 0, count)
                    }
                    entries[name] = baos.toByteArray()
                }
                zipStream.closeEntry()
                entry = zipStream.nextEntry
            }
        }

        if (entries.isEmpty()) {
            return fallbackEmptyDocument(fileName, "Empty or invalid archive")
        }

        // 1. Shared Strings Table
        val sharedStrings = parseSharedStrings(entries["xl/sharedStrings.xml"])

        // 2. Sheet definitions from xl/workbook.xml & rels
        val sheetDefinitions = parseWorkbookSheets(entries["xl/workbook.xml"], entries["xl/_rels/workbook.xml.rels"])

        // 3. Parse each sheet
        val parsedSheets = mutableListOf<SheetData>()
        for (sheetDef in sheetDefinitions) {
            val sheetBytes = entries[sheetDef.path]
                ?: entries["xl/${sheetDef.path}"]
                ?: entries.entries.firstOrNull { it.key.endsWith("/" + sheetDef.path.substringAfterLast('/')) }?.value

            if (sheetBytes != null) {
                val sheetData = parseWorksheet(sheetDef.name, sheetBytes, sharedStrings)
                parsedSheets.add(sheetData)
            }
        }

        // If no sheets resolved through rels, search for any worksheets directly
        if (parsedSheets.isEmpty()) {
            val sheetEntries = entries.keys.filter { it.startsWith("xl/worksheets/sheet") && it.endsWith(".xml") }
                .sorted()
            sheetEntries.forEachIndexed { index, path ->
                entries[path]?.let { bytes ->
                    parsedSheets.add(parseWorksheet("Sheet ${index + 1}", bytes, sharedStrings))
                }
            }
        }

        val finalSheets = if (parsedSheets.isNotEmpty()) parsedSheets else listOf(
            SheetData("Sheet 1", listOf(RowData(0, listOf(CellData("No data found in spreadsheet")))), 1)
        )

        return SpreadsheetDocument(title = fileName, sheets = finalSheets)
    }

    private data class SheetDef(val name: String, val path: String)

    private fun parseWorkbookSheets(workbookBytes: ByteArray?, relsBytes: ByteArray?): List<SheetDef> {
        if (workbookBytes == null) return emptyList()

        val relsMap = mutableMapOf<String, String>()
        if (relsBytes != null) {
            try {
                val db = DocumentBuilderFactory.newInstance().apply {
                    isNamespaceAware = true
                    try { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) } catch (_: Exception) {}
                }.newDocumentBuilder()
                val doc = db.parse(ByteArrayInputStream(relsBytes))
                val relNodes = doc.getElementsByTagName("Relationship")
                for (i in 0 until relNodes.length) {
                    val el = relNodes.item(i) as? Element ?: continue
                    val id = el.getAttribute("Id")
                    var target = el.getAttribute("Target")
                    if (!target.startsWith("xl/") && !target.startsWith("/")) {
                        target = "xl/$target"
                    }
                    if (id.isNotBlank() && target.isNotBlank()) {
                        relsMap[id] = target.removePrefix("/")
                    }
                }
            } catch (_: Exception) {}
        }

        val result = mutableListOf<SheetDef>()
        try {
            val db = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
                try { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) } catch (_: Exception) {}
            }.newDocumentBuilder()
            val doc = db.parse(ByteArrayInputStream(workbookBytes))
            val sheetNodes = doc.getElementsByTagName("sheet")
            for (i in 0 until sheetNodes.length) {
                val el = sheetNodes.item(i) as? Element ?: continue
                val name = el.getAttribute("name").ifBlank { "Sheet ${i + 1}" }
                val rId = el.getAttribute("r:id").ifBlank { el.getAttributeNS("http://schemas.openxmlformats.org/officeDocument/2006/relationships", "id") }
                val path = relsMap[rId] ?: "xl/worksheets/sheet${i + 1}.xml"
                result.add(SheetDef(name, path))
            }
        } catch (_: Exception) {}

        return result
    }

    private fun parseSharedStrings(sharedStringsBytes: ByteArray?): List<String> {
        if (sharedStringsBytes == null) return emptyList()
        val strings = mutableListOf<String>()
        try {
            val db = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
                try { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) } catch (_: Exception) {}
            }.newDocumentBuilder()
            val doc = db.parse(ByteArrayInputStream(sharedStringsBytes))
            val siNodes = doc.getElementsByTagName("si")
            for (i in 0 until siNodes.length) {
                val si = siNodes.item(i) as? Element ?: continue
                val tNodes = si.getElementsByTagName("t")
                val sb = StringBuilder()
                for (j in 0 until tNodes.length) {
                    val t = tNodes.item(j)
                    sb.append(t.textContent ?: "")
                }
                strings.add(sb.toString())
            }
        } catch (_: Exception) {}
        return strings
    }

    private fun parseWorksheet(sheetName: String, sheetBytes: ByteArray, sharedStrings: List<String>): SheetData {
        val rows = mutableListOf<RowData>()
        var maxCols = 0

        try {
            val db = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
                try { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) } catch (_: Exception) {}
            }.newDocumentBuilder()
            val doc = db.parse(ByteArrayInputStream(sheetBytes))
            val rowNodes = doc.getElementsByTagName("row")

            val rowCount = minOf(rowNodes.length, MAX_SHEET_ROWS)
            for (r in 0 until rowCount) {
                val rowEl = rowNodes.item(r) as? Element ?: continue
                val rowIndexAttr = rowEl.getAttribute("r").toIntOrNull() ?: (r + 1)

                val cellsByCol = mutableMapOf<Int, CellData>()
                var child: Node? = rowEl.firstChild
                while (child != null) {
                    if (child is Element && child.nodeName.endsWith("c")) {
                        val ref = child.getAttribute("r")
                        val type = child.getAttribute("t")

                        val colIndex = if (ref.isNotBlank()) {
                            columnRefToIndex(ref)
                        } else {
                            cellsByCol.size
                        }

                        if (colIndex < MAX_SHEET_COLS) {
                            val value = extractCellValue(child, type, sharedStrings)
                            val isHeader = (rowIndexAttr == 1)
                            cellsByCol[colIndex] = CellData(value = value, isHeader = isHeader, columnIndex = colIndex, rowIndex = rowIndexAttr)
                        }
                    }
                    child = child.nextSibling
                }

                if (cellsByCol.isNotEmpty()) {
                    val highestCol = cellsByCol.keys.maxOrNull() ?: 0
                    maxCols = maxOf(maxCols, highestCol + 1)
                    val rowCells = mutableListOf<CellData>()
                    for (c in 0..highestCol) {
                        rowCells.add(cellsByCol[c] ?: CellData(value = "", columnIndex = c, rowIndex = rowIndexAttr))
                    }
                    rows.add(RowData(rowIndex = rowIndexAttr, cells = rowCells))
                }
            }
        } catch (_: Exception) {}

        if (rows.isEmpty()) {
            rows.add(RowData(1, listOf(CellData("Sheet is empty"))))
            maxCols = 1
        }

        return SheetData(name = sheetName, rows = rows, maxColumns = maxCols)
    }

    private fun extractCellValue(cellEl: Element, type: String, sharedStrings: List<String>): String {
        return when (type) {
            "s" -> {
                val v = cellEl.getElementsByTagName("v").item(0)?.textContent?.trim()?.toIntOrNull()
                if (v != null && v in sharedStrings.indices) sharedStrings[v] else ""
            }
            "b" -> {
                val v = cellEl.getElementsByTagName("v").item(0)?.textContent?.trim()
                if (v == "1") "TRUE" else if (v == "0") "FALSE" else v ?: ""
            }
            "inlineStr" -> {
                val t = cellEl.getElementsByTagName("t").item(0)?.textContent
                t ?: ""
            }
            "e" -> {
                cellEl.getElementsByTagName("v").item(0)?.textContent ?: "#ERROR"
            }
            else -> {
                // Number, raw value, or formula evaluated value
                val v = cellEl.getElementsByTagName("v").item(0)?.textContent?.trim()
                if (!v.isNullOrEmpty()) {
                    formatNumberIfApplicable(v)
                } else {
                    cellEl.getElementsByTagName("t").item(0)?.textContent?.trim() ?: ""
                }
            }
        }
    }

    private fun formatNumberIfApplicable(raw: String): String {
        val d = raw.toDoubleOrNull() ?: return raw
        return if (d == d.toLong().toDouble() && !raw.contains(".")) {
            d.toLong().toString()
        } else {
            raw
        }
    }

    fun columnRefToIndex(cellRef: String): Int {
        var col = 0
        for (char in cellRef) {
            if (char in 'A'..'Z') {
                col = col * 26 + (char - 'A' + 1)
            } else if (char in 'a'..'z') {
                col = col * 26 + (char - 'a' + 1)
            } else {
                break
            }
        }
        return (col - 1).coerceAtLeast(0)
    }

    fun indexToColumnName(index: Int): String {
        var n = index + 1
        val sb = StringBuilder()
        while (n > 0) {
            val rem = (n - 1) % 26
            sb.append(('A' + rem))
            n = (n - 1) / 26
        }
        return sb.reverse().toString()
    }

    fun readCsv(inputStream: InputStream, fileName: String): SpreadsheetDocument {
        val reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8))
        val rows = mutableListOf<RowData>()
        var rowIndex = 1
        var maxCols = 0

        var line = reader.readLine()
        // Detect delimiter: comma, semicolon, tab
        val firstLine = line ?: ""
        val commaCount = firstLine.count { it == ',' }
        val semiCount = firstLine.count { it == ';' }
        val tabCount = firstLine.count { it == '\t' }
        val delimiter = when {
            tabCount > commaCount && tabCount > semiCount -> '\t'
            semiCount > commaCount -> ';'
            else -> ','
        }

        while (line != null && rowIndex <= MAX_SHEET_ROWS) {
            val parsedCells = parseCsvLine(line, delimiter)
            if (parsedCells.isNotEmpty()) {
                val isHeader = (rowIndex == 1)
                val cells = parsedCells.take(MAX_SHEET_COLS).mapIndexed { colIdx, text ->
                    CellData(value = text, isHeader = isHeader, columnIndex = colIdx, rowIndex = rowIndex)
                }
                maxCols = maxOf(maxCols, cells.size)
                rows.add(RowData(rowIndex = rowIndex, cells = cells))
            }
            rowIndex++
            line = reader.readLine()
        }

        if (rows.isEmpty()) {
            rows.add(RowData(1, listOf(CellData("CSV file is empty"))))
            maxCols = 1
        }

        return SpreadsheetDocument(
            title = fileName,
            sheets = listOf(SheetData(name = "Sheet 1", rows = rows, maxColumns = maxCols))
        )
    }

    private fun parseCsvLine(line: String, delimiter: Char): List<String> {
        val tokens = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0

        while (i < line.length) {
            val c = line[i]
            if (c == '"') {
                if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                    sb.append('"')
                    i++
                } else {
                    inQuotes = !inQuotes
                }
            } else if (c == delimiter && !inQuotes) {
                tokens.add(sb.toString().trim())
                sb.clear()
            } else {
                sb.append(c)
            }
            i++
        }
        tokens.add(sb.toString().trim())
        return tokens
    }

    private fun fallbackEmptyDocument(title: String, message: String): SpreadsheetDocument {
        return SpreadsheetDocument(
            title = title,
            sheets = listOf(
                SheetData(
                    name = "Sheet 1",
                    rows = listOf(RowData(1, listOf(CellData(message)))),
                    maxColumns = 1
                )
            )
        )
    }
}
