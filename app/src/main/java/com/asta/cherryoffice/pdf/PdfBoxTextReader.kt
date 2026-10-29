package com.asta.cherryoffice.pdf

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import java.io.File

class PdfBoxTextReader(file: File) : PdfTextReader {
    private val document = PDDocument.load(file, MemoryUsageSetting.setupMixed(16L * 1024 * 1024))
    override fun page(index: Int): PdfTextPage {
        if (!document.currentAccessPermission.canExtractContent()) return PdfTextPage("", emptyList(), false, "Copying is restricted by this PDF.")
        val text = StringBuilder()
        val glyphs = mutableListOf<PdfGlyph>()
        val stripper = object : PDFTextStripper() {
            override fun writeString(value: String?, positions: MutableList<TextPosition>?) {
                positions.orEmpty().forEach { position ->
                    val value = position.unicode.orEmpty()
                    if (value.isEmpty()) return@forEach
                    val start = text.length
                    text.append(value)
                    val x = position.x
                    val y = position.y
                    val w = position.widthDirAdj.coerceAtLeast(1f)
                    val h = position.heightDir.coerceAtLeast(1f)
                    // PDFBox exposes crop-adjusted, top-left coordinates after page rotation.
                    val rotation = ((position.dir.toInt() - position.rotation) % 360 + 360) % 360
                    val box = when (rotation) {
                        90 -> floatArrayOf(x - h, y - w, x, y)
                        180 -> floatArrayOf(x - w, y, x, y + h)
                        270 -> floatArrayOf(x, y, x + h, y + w)
                        else -> floatArrayOf(x, y - h, x + w, y)
                    }
                    glyphs.add(PdfGlyph(start, text.length, box[0], box[1], box[2], box[3]))
                }
            }
            override fun writeWordSeparator() { text.append(' ') }
            override fun writeLineSeparator() { text.append('\n') }
            override fun writeParagraphSeparator() { if (text.isNotEmpty() && text.last() != '\n') text.append('\n') }
        }.apply { startPage = index + 1; endPage = index + 1; sortByPosition = true }
        stripper.getText(document)
        return PdfTextPage(text.toString(), glyphs, message = if (glyphs.isEmpty()) "This page has no selectable text. Scanned pages need OCR." else null)
    }
    override fun close() = document.close()
}
