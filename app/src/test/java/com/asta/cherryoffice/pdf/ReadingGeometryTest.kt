package com.asta.cherryoffice.pdf

import com.asta.cherryoffice.ui.components.ZoomAnchor
import com.asta.cherryoffice.data.repository.AppearanceMode
import org.junit.Assert.*
import org.junit.Test

class ReadingGeometryTest {
    @Test fun movingPinchKeepsDocumentPointUnderFingers() {
        val before = ZoomAnchor(140f, 800f)
        val after = before.scaled(1.25f, 2.5f, 120f, 300f, 140f, 340f)
        assertEquals((before.scrollX + 120) / 1.25f, (after.scrollX + 140) / 2.5f, .0001f)
        assertEquals((before.scrollY + 300) / 1.25f, (after.scrollY + 340) / 2.5f, .0001f)
    }
    @Test fun zoomOutRestoresAnchorAtCenterAndCorners() {
        listOf(0f to 0f, 400f to 800f, 200f to 400f).forEach { (x, y) ->
            val original = ZoomAnchor(600f, 1400f)
            val zoomed = original.scaled(1f, 4f, x, y)
            val restored = zoomed.scaled(4f, 1f, x, y)
            assertEquals(original.scrollX, restored.scrollX, .001f)
            assertEquals(original.scrollY, restored.scrollY, .001f)
        }
    }
    @Test fun selectionNormalizesReversedHandlesAcrossPages() {
        val selection = PdfSelection(PdfTextPoint(2, 10), PdfTextPoint(0, 3))
        assertEquals(PdfTextPoint(0, 3), selection.start)
        assertEquals(PdfTextPoint(2, 10), selection.end)
        assertTrue(selection.contains(1, PdfGlyph(0, 1, 0f, 0f, 1f, 1f)))
        assertFalse(selection.contains(0, PdfGlyph(0, 3, 0f, 0f, 1f, 1f)))
    }
    @Test fun wordSelectionPreservesUnicode() {
        val page = PdfTextPage("Hello Привет cafe\u0301", emptyList())
        val range = page.wordAt(8)
        assertEquals("Привет", page.text.substring(range))
        val accented = page.wordAt(page.text.lastIndex)
        assertEquals("cafe\u0301", page.text.substring(accented))
    }
    @Test fun savedThemesMigrateWithoutResettingUserChoices() {
        assertEquals(AppearanceMode.DARK, AppearanceMode.fromStored("DARK_GLOSSY"))
        assertEquals(AppearanceMode.LIGHT, AppearanceMode.fromStored("LIGHT_CHERRY"))
        assertEquals(AppearanceMode.AMOLED, AppearanceMode.fromStored("AMOLED_BLACK"))
        assertEquals(AppearanceMode.SYSTEM, AppearanceMode.fromStored(null))
    }
}
