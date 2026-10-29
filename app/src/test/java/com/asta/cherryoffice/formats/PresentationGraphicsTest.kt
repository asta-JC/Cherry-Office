package com.asta.cherryoffice.formats

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class PresentationGraphicsTest {
    @Test fun rendersPicturesChartLinesShapesAndTablesInSlideOrder() {
        val slides = File("src/androidTest/assets/slide-preview.pptx").inputStream().use(PresentationReader::read)
        assertEquals(3, slides.size)
        val first = slides[0].svg
        assertTrue(first.contains("viewBox=\"0 0 960.0 540.0\""))
        assertTrue(first.contains("data:image/png;base64,"))
        assertTrue(first.contains("translate(40.0 120.0)"))
        assertTrue(first.contains("<line"))
        assertTrue(first.contains("marker-end=\"url(#arrow)\""))
        assertTrue(first.contains("Pictures &amp; shapes"))
        assertTrue(first.contains(">Q2</text>"))
        assertTrue(slides[0].warnings.any { it.contains("saved values") })
        assertTrue(slides[1].svg.contains("Images and graphics"))
        assertTrue(slides[2].svg.contains("<ellipse"))
        assertTrue(slides[2].svg.contains("#B34A66"))
        assertEquals("Third slide", slides[2].paragraphs.first())
    }
    @Test fun textKeepsIntrinsicLineHeightAndProvidesFontMetricsForFitting() {
        val slide = File("src/androidTest/assets/text-clipping.pptx").inputStream().use(PresentationReader::read).single()
        assertTrue(slide.svg.contains("data-slide-content"))
        assertTrue(slide.svg.contains("data-fit-font=\"40.0\""))
        assertTrue(slide.svg.contains("line-height:normal"))
        assertFalse(slide.svg.contains("overflow:hidden"))
        assertTrue(slide.svg.contains("OXIRGI SATR: gypqj"))
        assertTrue(slide.svg.contains("justify-content:center"))
        assertTrue(slide.svg.contains("justify-content:flex-end"))
    }

}
