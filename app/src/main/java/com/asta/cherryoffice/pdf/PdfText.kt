package com.asta.cherryoffice.pdf

import java.text.BreakIterator
import java.util.Locale

/** UTF-16 ranges match Android clipboard strings; glyph ranges never split a cluster. */
data class PdfGlyph(val start: Int, val end: Int, val left: Float, val top: Float, val right: Float, val bottom: Float)
data class PdfTextPage(val text: String, val glyphs: List<PdfGlyph>, val copyAllowed: Boolean = true, val message: String? = null) {
    fun wordAt(offset: Int): IntRange {
        if (text.isEmpty()) return IntRange.EMPTY
        val at = offset.coerceIn(0, text.lastIndex)
        val source = text
        val words = BreakIterator.getWordInstance(Locale.getDefault()).apply { setText(source) }
        val start = if (words.isBoundary(at)) at else words.preceding(at).coerceAtLeast(0)
        val end = words.following(at).let { if (it == BreakIterator.DONE) text.length else it }
        return start until end
    }
}
data class PdfTextPoint(val page: Int, val offset: Int) : Comparable<PdfTextPoint> {
    override fun compareTo(other: PdfTextPoint): Int = compareValuesBy(this, other, { it.page }, { it.offset })
}
data class PdfSelection(val anchor: PdfTextPoint, val focus: PdfTextPoint) {
    val start get() = minOf(anchor, focus)
    val end get() = maxOf(anchor, focus)
    fun contains(page: Int, glyph: PdfGlyph): Boolean = PdfTextPoint(page, glyph.end) > start && PdfTextPoint(page, glyph.start) < end
}
interface PdfTextReader : AutoCloseable { fun page(index: Int): PdfTextPage }
