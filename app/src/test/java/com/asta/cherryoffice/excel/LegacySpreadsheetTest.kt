package com.asta.cherryoffice.excel

import org.apache.poi.hssf.usermodel.HSSFWorkbook
import org.apache.poi.hssf.record.crypto.Biff8EncryptionKey
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream

class LegacySpreadsheetTest {
    private fun workbook(encrypted: Boolean = false): ByteArray {
        try {
            if (encrypted) Biff8EncryptionKey.setCurrentUserPassword("secret")
            return HSSFWorkbook().use { book ->
                val sheet = book.createSheet("Revenue")
                val row = sheet.createRow(4)
                row.createCell(0).setCellValue("Actual XLS")
                val currency = book.createCellStyle().apply { dataFormat = book.createDataFormat().getFormat("$#,##0.00") }
                row.createCell(1).apply { setCellValue(1234.5); cellStyle = currency }
                val date = book.createCellStyle().apply { dataFormat = book.createDataFormat().getFormat("yyyy-mm-dd") }
                row.createCell(2).apply { setCellValue(45292.0); cellStyle = date }
                row.createCell(3).cellFormula = "B5*2"
                book.creationHelper.createFormulaEvaluator().evaluateAll()
                sheet.setColumnWidth(0, 24 * 256)
                book.createSheet("Second sheet").createRow(0).createCell(2).setCellValue("Sparse")
                ByteArrayOutputStream().also(book::write).toByteArray()
            }
        } finally { Biff8EncryptionKey.setCurrentUserPassword(null) }
    }
    @Test fun readsBinaryXlsWithSavedFormulaResultsAndStyles() {
        val doc = OfficeSpreadsheetReader.read(workbook().inputStream(), "budget.xls")
        assertEquals(listOf("Revenue", "Second sheet"), doc.sheets.map { it.name })
        val row = doc.sheets[0].rows.single()
        assertEquals(5, row.rowIndex)
        assertEquals("Actual XLS", row.cells[0].value)
        assertTrue(row.cells[1].value.contains("1,234.50"))
        assertEquals("2024-01-01", row.cells[2].value)
        assertEquals("2469", row.cells[3].value)
        assertEquals("B5*2", row.cells[3].formula)
        assertEquals("", doc.sheets[1].rows[0].cells[0].value)
        assertTrue(doc.sheets[0].columnWidths[0]!! > 150f)
    }
    @Test fun detectsBinaryContentDespiteXlsxFileName() {
        assertEquals("Actual XLS", OfficeSpreadsheetReader.read(workbook().inputStream(), "wrong.xlsx").sheets[0].rows[0].cells[0].value)
    }
    @Test fun encryptedWorkbookHasSpecificError() {
        try {
            OfficeSpreadsheetReader.read(workbook(true).inputStream(), "locked.xls")
            fail("Encrypted XLS must not appear as an empty workbook")
        } catch (e: IllegalArgumentException) { assertTrue(e.message.orEmpty().contains("password-protected")) }
    }
    @Test(expected = IllegalArgumentException::class) fun corruptFileReportsFailure() {
        OfficeSpreadsheetReader.read(byteArrayOf(0, 1, 2, 3).inputStream(), "broken.xls")
    }
}
