package com.asta.cherryoffice.docx

// Unit tests for the pure-JVM converter (no device needed).
// The fixture builds a minimal but representative .docx in memory:
// headings, styled runs, lists, a table with merges, an image,
// a hyperlink, a page break, a page-number field, a footnote,
// plus header/footer parts.

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class DocxToHtmlTest {

    // Smallest valid 1x1 transparent PNG (68 bytes).
    private val tinyPng = byteArrayOf(
        137.toByte(), 80, 78, 71, 13, 10, 26, 10, 0, 0, 0, 13, 73, 72, 68, 82,
        0, 0, 0, 1, 0, 0, 0, 1, 8, 6, 0, 0, 0, 31, 21, 196.toByte(), 137.toByte(),
        0, 0, 0, 13, 73, 68, 65, 84, 120, 1, 99, 0, 1, 0, 0, 5, 0, 1, 13, 10, 45, 180.toByte(),
        0, 0, 0, 0, 73, 69, 78, 68, 174.toByte(), 66, 96, 130.toByte()
    )

    private fun zipOf(vararg entries: Pair<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            for ((name, bytes) in entries) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun fixtureDocx(): ByteArray = zipOf(
        "[Content_Types].xml" to "<Types/>".toByteArray(),
        "word/document.xml" to DOCUMENT_XML.toByteArray(Charsets.UTF_8),
        "word/styles.xml" to STYLES_XML.toByteArray(Charsets.UTF_8),
        "word/numbering.xml" to NUMBERING_XML.toByteArray(Charsets.UTF_8),
        "word/_rels/document.xml.rels" to RELS_XML.toByteArray(Charsets.UTF_8),
        "word/header1.xml" to HEADER_XML.toByteArray(Charsets.UTF_8),
        "word/footer1.xml" to FOOTER_XML.toByteArray(Charsets.UTF_8),
        "word/footnotes.xml" to FOOTNOTES_XML.toByteArray(Charsets.UTF_8),
        "word/media/image1.png" to tinyPng
    )

    @Test
    fun headings_and_runFormatting_arePreserved() {
        val html = DocxToHtmlConverter.convert(fixtureDocx()).html
        assertTrue("heading tag", html.contains("<h1"))
        assertTrue("heading text", html.contains("Quarterly Report"))
        assertTrue("bold", html.contains("font-weight:bold"))
        assertTrue("italic", html.contains("font-style:italic"))
        assertTrue("underline", html.contains("underline"))
        assertTrue("run color", html.contains("#ff0000"))
        assertTrue("highlight", html.contains("#ffff00"))
        assertTrue("centered", html.contains("text-align:center"))
        assertTrue("justified", html.contains("text-align:justify"))
    }

    @Test
    fun lists_tables_and_images_render() {
        val seenImages = ArrayList<String>()
        val html = DocxToHtmlConverter.convert(fixtureDocx()) { name, bytes ->
            seenImages.add(name)
            assertTrue("image bytes", bytes.isNotEmpty())
            "media/$name"
        }.html
        assertTrue("bullet list", html.contains("<ul"))
        assertTrue("numbered list", html.contains("<ol"))
        assertTrue("list item", html.contains("<li"))
        assertTrue("table", html.contains("<table"))
        assertTrue("header shading", html.contains("#d9e2f3"))
        assertTrue("colspan", html.contains("colspan=\"2\""))
        assertTrue("rowspan", html.contains("rowspan=\"2\""))
        assertTrue("img tag", html.contains("<img"))
        assertTrue("img url", html.contains("media/image1.png"))
        assertEquals(listOf("image1.png"), seenImages)
        assertTrue("hyperlink", html.contains("href=\"https://example.com\""))
    }

    @Test
    fun documentChrome_paginationHooks_footnotes() {
        val html = DocxToHtmlConverter.convert(fixtureDocx()).html
        assertTrue("header", html.contains("Acme Corp"))
        assertTrue("footer", html.contains("Confidential"))
        assertTrue("page break marker", html.contains("data-page-break-before"))
        assertTrue("page number slot", html.contains("class=\"pnum\""))
        assertTrue("endnotes", html.contains("Notes"))
        assertTrue("footnote link", html.contains("#note-1"))
        assertTrue("pagination script", html.contains("__currentPage"))
        assertTrue("A4 sheet css", html.contains("class=\"sheet\"") || html.contains(".sheet"))
    }

    @Test
    fun sampleDocx_convertsWithAllFeatures() {
        // End-to-end check on the richer manual-test file (test resources).
        val stream = javaClass.getResourceAsStream("/sample.docx")
            ?: error("test resource sample.docx missing")
        val bytes = stream.readBytes()
        val seenImages = ArrayList<String>()
        val html = DocxToHtmlConverter.convert(bytes) { name, _ ->
            seenImages.add(name)
            "media/$name"
        }.html
        assertTrue("h1", html.contains("<h1"))
        assertTrue("h2", html.contains("<h2"))
        assertTrue("table", html.contains("<table"))
        assertTrue("colspan", html.contains("colspan=\"3\""))
        assertTrue("rowspan", html.contains("rowspan=\"2\""))
        assertTrue("image", html.contains("<img"))
        assertEquals(listOf("image1.png"), seenImages)
        assertTrue("link", html.contains("https://example.com"))
        assertTrue("page break", html.contains("data-page-break-before"))
        assertTrue("header", html.contains("Cherry Office Sample"))
        assertTrue("footer page num", html.contains("class=\"pnum\""))
    }

    @Test
    fun contentVisibleByDefault_paginateHidesIt() {
        // Regression: raw content must not be pre-hidden, so a JavaScript
        // failure can never produce a blank page (paginate hides it on success).
        val stream = javaClass.getResourceAsStream("/sample.docx")
            ?: error("test resource sample.docx missing")
        val html = DocxToHtmlConverter.convert(stream.readBytes()).html
        assertTrue("content not pre-hidden", html.contains("<div id=\"content\">"))
        assertTrue(
            "paginate hides source",
            html.contains("src.style.display='none'") || html.contains("src.style.display=\"none\"")
        )
    }

    @Test
    fun corruptFile_reportsCorrupt() {        try {
            DocxToHtmlConverter.convert("this is not a zip".toByteArray())
            fail("expected DocxParseException")
        } catch (e: DocxParseException) {
            assertEquals(DocxFailureKind.CORRUPT, e.kind)
        }
    }

    @Test
    fun encryptedFile_reportsEncrypted() {
        val bytes = zipOf("EncryptedPackage" to ByteArray(16))
        try {
            DocxToHtmlConverter.convert(bytes)
            fail("expected DocxParseException")
        } catch (e: DocxParseException) {
            assertEquals(DocxFailureKind.ENCRYPTED, e.kind)
        }
    }

    companion object {
        private const val DOCUMENT_XML = """<?xml version="1.0" encoding="UTF-8"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"
  xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"
  xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing"
  xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"
  xmlns:pic="http://schemas.openxmlformats.org/drawingml/2006/picture">
<w:body>
<w:p><w:pPr><w:pStyle w:val="Heading1"/></w:pPr>
 <w:r><w:t>Quarterly Report</w:t></w:r></w:p>
<w:p><w:pPr><w:jc w:val="center"/></w:pPr>
 <w:r><w:rPr><w:b/><w:color w:val="FF0000"/></w:rPr><w:t xml:space="preserve">Bold red </w:t></w:r>
 <w:r><w:rPr><w:i/><w:u w:val="single"/><w:highlight w:val="yellow"/></w:rPr><w:t>italic underlined</w:t></w:r></w:p>
<w:p><w:pPr><w:jc w:val="both"/></w:pPr>
 <w:r><w:t>Justified body text.</w:t></w:r>
 <w:hyperlink r:id="rId1"><w:r><w:t>Example link</w:t></w:r></w:hyperlink></w:p>
<w:p><w:pPr><w:numPr><w:numId w:val="1"/><w:ilvl w:val="0"/></w:numPr></w:pPr>
 <w:r><w:t>Bullet one</w:t></w:r></w:p>
<w:p><w:pPr><w:numPr><w:numId w:val="1"/><w:ilvl w:val="1"/></w:numPr></w:pPr>
 <w:r><w:t>Nested bullet</w:t></w:r></w:p>
<w:p><w:pPr><w:numPr><w:numId w:val="2"/><w:ilvl w:val="0"/></w:numPr></w:pPr>
 <w:r><w:t>Step one</w:t></w:r></w:p>
<w:tbl><w:tblPr><w:tblW w:w="5000" w:type="dxa"/></w:tblPr>
 <w:tblGrid><w:gridCol w:w="2500"/><w:gridCol w:w="2500"/></w:tblGrid>
 <w:tr><w:tc><w:tcPr><w:shd w:fill="D9E2F3"/><w:gridSpan w:val="2"/></w:tcPr>
  <w:p><w:r><w:t>Merged header</w:t></w:r></w:p></w:tc></w:tr>
 <w:tr><w:tc><w:tcPr><w:vMerge w:val="restart"/></w:tcPr>
  <w:p><w:r><w:t>Tall cell</w:t></w:r></w:p></w:tc>
  <w:tc><w:p><w:r><w:t>Right A</w:t></w:r></w:p></w:tc></w:tr>
 <w:tr><w:tc><w:tcPr><w:vMerge/></w:tcPr><w:p/></w:tc>
  <w:tc><w:p><w:r><w:t>Right B</w:t></w:r></w:p></w:tc></w:tr>
</w:tbl>
<w:p><w:r><w:drawing><wp:inline><wp:extent cx="914400" cy="914400"/>
 <wp:docPr id="1" descr="Logo"/>
 <a:graphic><a:graphicData><pic:pic><pic:blipFill>
 <a:blip r:embed="rId2"/></pic:blipFill></pic:pic></a:graphicData></a:graphic>
 </wp:inline></w:drawing></w:r></w:p>
<w:p><w:r><w:br w:type="page"/></w:r></w:p>
<w:p><w:r><w:fldChar w:fldCharType="begin"/></w:r></w:p>
<w:p><w:r><w:instrText> PAGE </w:instrText></w:r></w:p>
<w:p><w:r><w:t>Footnote here</w:t></w:r>
 <w:r><w:footnoteReference w:id="1"/></w:r></w:p>
<w:sectPr/>
</w:body></w:document>"""

        private const val STYLES_XML = """<?xml version="1.0" encoding="UTF-8"?>
<w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
<w:docDefaults><w:rPrDefault><w:rPr><w:sz w:val="22"/>
 <w:rFonts w:ascii="Calibri" w:hAnsi="Calibri"/></w:rPr></w:rPrDefault></w:docDefaults>
<w:style w:styleId="Normal"><w:name w:val="Normal"/></w:style>
<w:style w:styleId="Heading1"><w:name w:val="heading 1"/><w:basedOn w:val="Normal"/>
 <w:rPr><w:b/><w:sz w:val="40"/><w:color w:val="1F4E79"/></w:rPr></w:style>
</w:styles>"""

        private const val NUMBERING_XML = """<?xml version="1.0" encoding="UTF-8"?>
<w:numbering xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
<w:abstractNum w:abstractNumId="0">
 <w:lvl w:ilvl="0"><w:numFmt w:val="bullet"/><w:pPr><w:ind w:left="720"/></w:pPr></w:lvl>
 <w:lvl w:ilvl="1"><w:numFmt w:val="bullet"/><w:pPr><w:ind w:left="1440"/></w:pPr></w:lvl>
</w:abstractNum>
<w:abstractNum w:abstractNumId="1">
 <w:lvl w:ilvl="0"><w:numFmt w:val="decimal"/><w:pPr><w:ind w:left="720"/></w:pPr></w:lvl>
</w:abstractNum>
<w:num w:numId="1"><w:abstractNumId w:val="0"/></w:num>
<w:num w:numId="2"><w:abstractNumId w:val="1"/></w:num>
</w:numbering>"""

        private const val RELS_XML = """<?xml version="1.0" encoding="UTF-8"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/hyperlink" Target="https://example.com" TargetMode="External"/>
<Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="media/image1.png"/>
</Relationships>"""

        private const val HEADER_XML = """<?xml version="1.0" encoding="UTF-8"?>
<w:hdr xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
<w:p><w:pPr><w:jc w:val="center"/></w:pPr><w:r><w:t>Acme Corp</w:t></w:r></w:p></w:hdr>"""

        private const val FOOTER_XML = """<?xml version="1.0" encoding="UTF-8"?>
<w:ftr xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
<w:p><w:pPr><w:jc w:val="center"/></w:pPr><w:r><w:t>Confidential</w:t></w:r></w:p></w:ftr>"""

        private const val FOOTNOTES_XML = """<?xml version="1.0" encoding="UTF-8"?>
<w:footnotes xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
<w:footnote w:id="1"><w:p><w:r><w:t>Source: finance team.</w:t></w:r></w:p></w:footnote>
</w:footnotes>"""
    }
}
