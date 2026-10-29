package com.asta.cherryoffice.formats

import com.asta.cherryoffice.excel.XlsxFileReader
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class OfficeCompatibilityTest {
    private fun archive(vararg entries: Pair<String, String>) = ByteArrayOutputStream().also { out ->
        ZipOutputStream(out).use { zip -> entries.forEach { (path, xml) ->
            zip.putNextEntry(ZipEntry(path)); zip.write(xml.toByteArray()); zip.closeEntry()
        } }
    }.toByteArray().inputStream()

    private fun workbook(sheet: String, styles: String = "", props: String = "") = archive(
        "xl/workbook.xml" to """<x:workbook xmlns:x="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">$props<x:sheets><x:sheet name="Actual data" r:id="sheet9"/></x:sheets></x:workbook>""",
        "xl/_rels/workbook.xml.rels" to """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="sheet9" Target="/xl/worksheets/custom.xml"/></Relationships>""",
        "xl/worksheets/custom.xml" to """<x:worksheet xmlns:x="http://schemas.openxmlformats.org/spreadsheetml/2006/main">$sheet</x:worksheet>""",
        "xl/styles.xml" to styles.ifBlank { "<styleSheet/>" })

    @Test fun prefixedWorkbookPreservesCoordinatesRichTextAndColumnWidths() {
        val doc = XlsxFileReader.readXlsx(workbook("""<x:cols><x:col min="1" max="1" width="20"/></x:cols><x:sheetData><x:row r="7"><x:c r="C7" t="inlineStr"><x:is><x:r><x:t>Hello </x:t></x:r><x:r><x:t>world</x:t></x:r></x:is></x:c></x:row></x:sheetData>"""), "data.xlsx")
        assertEquals("Actual data", doc.sheets.single().name)
        val row = doc.sheets.single().rows.single()
        assertEquals(7, row.rowIndex)
        assertEquals("", row.cells[0].value)
        assertEquals("Hello world", row.cells[2].value)
        assertEquals(152f, doc.sheets.single().columnWidths[0]!!, .01f)
    }

    @Test fun numberFormatsAndSavedFormulaResultsAreReadable() {
        val styles = """<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><fonts><font/><font><b/><i/><color rgb="FFFF0000"/></font></fonts><fills><fill/><fill><patternFill><fgColor rgb="FF00FF00"/></patternFill></fill></fills><cellXfs><xf numFmtId="0"/><xf numFmtId="14"/><xf numFmtId="10"/><xf numFmtId="4" fontId="1" fillId="1"><alignment horizontal="right" wrapText="1"/></xf></cellXfs></styleSheet>"""
        val doc = XlsxFileReader.readXlsx(workbook("""<x:sheetData><x:row r="1"><x:c r="A1" s="1"><x:v>45292</x:v></x:c><x:c r="B1" s="2"><x:v>0.125</x:v></x:c><x:c r="C1" s="3"><x:f>SUM(A2:A3)</x:f><x:v>1234.5</x:v></x:c><x:c r="D1"><x:f>A2+1</x:f></x:c></x:row></x:sheetData>""", styles), "data.xlsx")
        val cells = doc.sheets.single().rows.single().cells
        assertEquals("2024-01-01", cells[0].value)
        assertEquals("12.50%", cells[1].value)
        assertEquals("1,234.50", cells[2].value)
        assertEquals("SUM(A2:A3)", cells[2].formula)
        assertTrue(cells[2].bold && cells[2].italic && cells[2].wrapText)
        assertEquals("FF0000", cells[2].fontColor)
        assertEquals("00FF00", cells[2].fillColor)
        assertEquals("=A2+1", cells[3].value)
        assertTrue(doc.warnings.any { it.contains("not recalculated") })
    }

    @Test fun macWorkbookUses1904DateSystem() {
        val doc = XlsxFileReader.readXlsx(workbook("""<x:sheetData><x:row r="1"><x:c r="A1" s="0"><x:v>0</x:v></x:c></x:row></x:sheetData>""",
            """<styleSheet><cellXfs><xf numFmtId="14"/></cellXfs></styleSheet>""", """<x:workbookPr date1904="1"/>"""), "mac.xlsx")
        assertEquals("1904-01-01", doc.sheets.single().rows.single().cells.single().value)
    }

    @Test fun csvPreservesBomMultilineEscapesAndWhitespace() {
        val csv = "\uFEFFName;Notes\r\nAlice;\"first;line\r\nsecond \"\"quote\"\"\"\r\nBob;  keep spaces  \r\n"
        val sheet = XlsxFileReader.readCsv(csv.byteInputStream(), "people.csv").sheets.single()
        assertEquals(3, sheet.rows.size)
        assertEquals("Name", sheet.rows[0].cells[0].value)
        assertEquals("first;line\r\nsecond \"quote\"", sheet.rows[1].cells[1].value)
        assertEquals("  keep spaces  ", sheet.rows[2].cells[1].value)
    }

    @Test fun tsvKeepsCommasInsideFields() {
        val sheet = XlsxFileReader.readCsv("a,b,c\tvalue\n1,2,3\t4".byteInputStream(), "table.tsv").sheets.single()
        assertEquals(2, sheet.maxColumns)
        assertEquals("1,2,3", sheet.rows[1].cells[0].value)
    }

    @Test(expected = IllegalArgumentException::class) fun invalidWorkbookFailsInsteadOfShowingInventedCells() {
        XlsxFileReader.readXlsx("not a workbook".byteInputStream(), "broken.xlsx")
    }

    @Test(expected = IllegalArgumentException::class) fun legacyExcelRequiresCompatibleEngine() {
        XlsxFileReader.readSpreadsheet("binary".byteInputStream(), "old.xls")
    }

    @Test(expected = IllegalArgumentException::class) fun malformedCsvReportsUnclosedQuote() {
        XlsxFileReader.readCsv("a,\"unfinished".byteInputStream(), "broken.csv")
    }

    @Test fun presentationUsesRelationshipOrderAndActualText() {
        val slides = PresentationReader.read(archive(
            "ppt/presentation.xml" to """<p:presentation xmlns:p="p" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><p:sldIdLst><p:sldId r:id="b"/><p:sldId r:id="a"/></p:sldIdLst></p:presentation>""",
            "ppt/_rels/presentation.xml.rels" to """<Relationships><Relationship Id="a" Target="slides/slide1.xml"/><Relationship Id="b" Target="/ppt/slides/slide2.xml"/></Relationships>""",
            "ppt/slides/slide1.xml" to """<p:sld xmlns:p="p" xmlns:a="a"><a:p><a:r><a:t>Last slide</a:t></a:r></a:p></p:sld>""",
            "ppt/slides/slide2.xml" to """<p:sld xmlns:p="p" xmlns:a="a"><a:p><a:r><a:t>First </a:t></a:r><a:r><a:t>slide</a:t></a:r><a:br/><a:r><a:t>Next line</a:t></a:r></a:p></p:sld>"""))
        assertEquals(listOf("First slide\nNext line"), slides[0].paragraphs)
        assertEquals(listOf("Last slide"), slides[1].paragraphs)
    }

    @Test fun resolvesPackagePaths() {
        assertEquals("xl/worksheets/sheet1.xml", OfficeArchive.resolve("xl", "../xl/worksheets/sheet1.xml"))
    }

    @Test(expected = org.xml.sax.SAXParseException::class) fun rejectsExternalXmlEntities() {
        OfficeArchive.xml("""<!DOCTYPE sheet [<!ENTITY ex SYSTEM "file:///not-readable">]><sheet>&ex;</sheet>""".toByteArray())
    }

    @Test(expected = org.xml.sax.SAXParseException::class) fun rejectsUtf16DocumentTypeDeclarations() {
        OfficeArchive.xml("<!DOCTYPE sheet><sheet/>".toByteArray(Charsets.UTF_16))
    }

    @Test fun acceptsUtf16OfficeXmlWithoutDeclarations() {
        assertEquals("sheet", OfficeArchive.xml("<sheet/>".toByteArray(Charsets.UTF_16)).documentElement.localName)
    }

    @Test(expected = IllegalArgumentException::class) fun rejectsOversizedDecompressedEntry() {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("xl/huge.xml"))
            val megabyte = ByteArray(1024 * 1024)
            repeat(25) { zip.write(megabyte) }
            zip.closeEntry()
        }
        OfficeArchive.read(out.toByteArray().inputStream()) { true }
    }
}
