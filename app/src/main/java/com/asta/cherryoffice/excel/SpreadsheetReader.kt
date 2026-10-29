package com.asta.cherryoffice.excel

import java.io.InputStream
import java.util.Locale
import org.apache.poi.hssf.usermodel.HSSFWorkbook
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.usermodel.FillPatternType
import org.apache.poi.EncryptedDocumentException
import org.apache.poi.poifs.filesystem.POIFSFileSystem

interface SpreadsheetReader {
    fun read(input: InputStream, name: String): SpreadsheetDocument
}

/** HSSF is deliberately isolated from desktop drawing, autosizing and OOXML APIs. */
object OfficeSpreadsheetReader : SpreadsheetReader {
    override fun read(input: InputStream, name: String): SpreadsheetDocument {
        val ext = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        if (ext in listOf("csv", "tsv", "txt")) return XlsxFileReader.readCsv(input, name)
        val stream = input.buffered()
        stream.mark(8)
        val signature = ByteArray(8)
        var n = 0
        while (n < 8) { val read = stream.read(signature, n, 8 - n); if (read < 0) break; n += read }
        stream.reset()
        return when {
            n >= 4 && signature[0] == 0x50.toByte() && signature[1] == 0x4B.toByte() -> XlsxFileReader.readXlsx(stream, name)
            n == 8 && signature.contentEquals(byteArrayOf(0xD0.toByte(), 0xCF.toByte(), 0x11, 0xE0.toByte(), 0xA1.toByte(), 0xB1.toByte(), 0x1A, 0xE1.toByte())) -> readXls(stream, name)
            else -> throw IllegalArgumentException("This is not a readable Excel workbook. The file may be damaged or have the wrong extension.")
        }
    }

    private fun readXls(input: InputStream, name: String): SpreadsheetDocument {
        // Bound the binary input before POI allocates workbook structures.
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            require(out.size() + n <= 64 * 1024 * 1024) { "This XLS file is too large to preview (maximum 64 MB)." }
            out.write(buffer, 0, n)
        }
        try {
            POIFSFileSystem(out.toByteArray().inputStream()).use { fs ->
                if (fs.root.hasEntry("EncryptedPackage") || fs.root.hasEntry("EncryptionInfo"))
                    throw EncryptedDocumentException("This workbook is password-protected.")
                HSSFWorkbook(fs).use { workbook ->
                    val formatter = DataFormatter(Locale.getDefault()).apply { setUseCachedValuesForFormulaCells(true) }
                    val warnings = linkedSetOf<String>()
                    val sheets = (0 until workbook.numberOfSheets).map { index ->
                        val sheet = workbook.getSheetAt(index)
                        val rows = mutableListOf<RowData>()
                        var maxColumns = 0
                        val iterator = sheet.rowIterator()
                        while (iterator.hasNext()) {
                            val row = iterator.next()
                            if (rows.size >= 10000) { warnings.add("Preview limited to 10000 populated rows per sheet."); break }
                            val count = row.lastCellNum.toInt().coerceIn(0, 256)
                            if (row.lastCellNum > 256) warnings.add("Preview limited to 256 columns.")
                            if (count == 0) continue
                            val cells = (0 until count).map { col ->
                                val cell = row.getCell(col)
                                if (cell == null) CellData("", columnIndex = col, rowIndex = row.rowNum + 1)
                                else {
                                    val style = cell.cellStyle
                                    val font = workbook.getFontAt(style.fontIndexAsInt)
                                    fun color(index: Short): String? = workbook.customPalette.getColor(index)?.triplet
                                        ?.joinToString("") { "%02X".format(it.toInt() and 255) }
                                    val formula = if (cell.cellType == org.apache.poi.ss.usermodel.CellType.FORMULA) cell.cellFormula else null
                                    if (formula != null) warnings.add("Formulas show saved results; they are not recalculated.")
                                    CellData(formatter.formatCellValue(cell), false, col, row.rowNum + 1, formula,
                                        font.bold, font.italic, color(font.color),
                                        if (style.fillPattern == FillPatternType.SOLID_FOREGROUND) color(style.fillForegroundColor) else null,
                                        style.alignment.name.lowercase(Locale.ROOT), style.wrapText)
                                }
                            }
                            maxColumns = maxOf(maxColumns, count)
                            rows.add(RowData(row.rowNum + 1, cells))
                        }
                        if (sheet.numMergedRegions > 0) warnings.add("Merged cell values appear in their starting cells.")
                        SheetData(sheet.sheetName, rows, maxColumns,
                            (0 until maxColumns).associateWith { (sheet.getColumnWidth(it) / 256f * 7 + 12).coerceIn(40f, 400f) })
                    }
                    require(sheets.isNotEmpty()) { "This workbook contains no worksheets." }
                    return SpreadsheetDocument(name, sheets, warnings.toList())
                }
            }
        } catch (e: EncryptedDocumentException) {
            throw IllegalArgumentException("This workbook is password-protected. Unlock it in Excel and save an unprotected copy.", e)
        } catch (e: org.apache.poi.hssf.OldExcelFormatException) {
            throw IllegalArgumentException("This very old Excel format needs to be saved as Excel 97–2003 XLS or XLSX first.", e)
        } catch (e: IllegalArgumentException) { throw e }
        catch (e: Exception) { throw IllegalArgumentException("This XLS workbook could not be opened. It may be damaged.", e) }
    }
}
