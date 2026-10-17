package com.asta.cherryoffice.excel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class XlsxFileReaderTest {

    @Test
    fun columnRefToIndex_convertsCorrectly() {
        assertEquals(0, XlsxFileReader.columnRefToIndex("A1"))
        assertEquals(1, XlsxFileReader.columnRefToIndex("B2"))
        assertEquals(25, XlsxFileReader.columnRefToIndex("Z10"))
        assertEquals(26, XlsxFileReader.columnRefToIndex("AA1"))
        assertEquals(27, XlsxFileReader.columnRefToIndex("AB99"))
    }

    @Test
    fun indexToColumnName_convertsCorrectly() {
        assertEquals("A", XlsxFileReader.indexToColumnName(0))
        assertEquals("B", XlsxFileReader.indexToColumnName(1))
        assertEquals("Z", XlsxFileReader.indexToColumnName(25))
        assertEquals("AA", XlsxFileReader.indexToColumnName(26))
        assertEquals("AB", XlsxFileReader.indexToColumnName(27))
    }

    @Test
    fun readCsv_parsesCommaSeparatedValues() {
        val csv = """
            Name,Role,Salary
            Alice,Engineer,120000
            "Bob, Jr.",Designer,95000
        """.trimIndent()

        val doc = XlsxFileReader.readSpreadsheet(
            ByteArrayInputStream(csv.toByteArray(Charsets.UTF_8)),
            "team.csv"
        )

        assertEquals("team.csv", doc.title)
        assertEquals(1, doc.sheets.size)
        val sheet = doc.sheets[0]
        assertEquals(3, sheet.rows.size)
        assertEquals("Name", sheet.rows[0].cells[0].value)
        assertEquals("Role", sheet.rows[0].cells[1].value)
        assertEquals("Salary", sheet.rows[0].cells[2].value)

        // Quoted comma handling
        assertEquals("Bob, Jr.", sheet.rows[2].cells[0].value)
        assertEquals("Designer", sheet.rows[2].cells[1].value)
        assertEquals("95000", sheet.rows[2].cells[2].value)

        // Cell coordinate indices
        assertEquals(1, sheet.rows[0].cells[0].rowIndex)
        assertEquals(0, sheet.rows[0].cells[0].columnIndex)
        assertEquals(3, sheet.rows[2].cells[1].rowIndex)
        assertEquals(1, sheet.rows[2].cells[1].columnIndex)
    }

    @Test
    fun readCsv_parsesSemicolonDelimitedValues() {
        val csv = """
            ID;Product;Price
            1;Cherry Office;Free
            2;Pro Edition;19.99
        """.trimIndent()

        val doc = XlsxFileReader.readSpreadsheet(
            ByteArrayInputStream(csv.toByteArray(Charsets.UTF_8)),
            "catalog.csv"
        )

        val sheet = doc.sheets[0]
        assertEquals(3, sheet.rows.size)
        assertEquals("Product", sheet.rows[0].cells[1].value)
        assertEquals("Cherry Office", sheet.rows[1].cells[1].value)
        assertEquals("Free", sheet.rows[1].cells[2].value)
    }

    @Test
    fun readXlsx_parsesValidZipWorkbook() {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            // xl/workbook.xml
            zos.putNextEntry(ZipEntry("xl/workbook.xml"))
            val wbXml = """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
                    <sheets>
                        <sheet name="Quarterly Data" sheetId="1" r:id="rId1"/>
                    </sheets>
                </workbook>
            """.trimIndent()
            zos.write(wbXml.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            // xl/_rels/workbook.xml.rels
            zos.putNextEntry(ZipEntry("xl/_rels/workbook.xml.rels"))
            val relsXml = """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                    <Relationship Id="rId1" Target="worksheets/sheet1.xml"/>
                </Relationships>
            """.trimIndent()
            zos.write(relsXml.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            // xl/sharedStrings.xml
            zos.putNextEntry(ZipEntry("xl/sharedStrings.xml"))
            val sstXml = """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" count="2" uniqueCount="2">
                    <si><t>Department</t></si>
                    <si><t>Engineering</t></si>
                </sst>
            """.trimIndent()
            zos.write(sstXml.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            // xl/worksheets/sheet1.xml
            zos.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml"))
            val sheetXml = """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                    <sheetData>
                        <row r="1">
                            <c r="A1" t="s"><v>0</v></c>
                            <c r="B1"><v>100</v></c>
                        </row>
                        <row r="2">
                            <c r="A2" t="s"><v>1</v></c>
                            <c r="B2"><v>250</v></c>
                        </row>
                    </sheetData>
                </worksheet>
            """.trimIndent()
            zos.write(sheetXml.toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }

        val doc = XlsxFileReader.readSpreadsheet(
            ByteArrayInputStream(baos.toByteArray()),
            "Quarterly.xlsx"
        )

        assertEquals("Quarterly.xlsx", doc.title)
        assertEquals(1, doc.sheets.size)
        val sheet = doc.sheets[0]
        assertEquals("Quarterly Data", sheet.name)
        assertEquals(2, sheet.rows.size)

        // Row 1: "Department", "100"
        assertEquals("Department", sheet.rows[0].cells[0].value)
        assertEquals("100", sheet.rows[0].cells[1].value)

        // Row 2: "Engineering", "250"
        assertEquals("Engineering", sheet.rows[1].cells[0].value)
        assertEquals("250", sheet.rows[1].cells[1].value)
        assertEquals(2, sheet.rows[1].cells[0].rowIndex)
        assertEquals(0, sheet.rows[1].cells[0].columnIndex)
        assertEquals(2, sheet.rows[1].cells[1].rowIndex)
        assertEquals(1, sheet.rows[1].cells[1].columnIndex)
    }
}
