package com.asta.cherryoffice.formats

import java.io.InputStream

/** Static slides in presentation order, with text retained for accessibility. */
data class PresentationSlide(val paragraphs: List<String>, val svg: String = "", val warnings: List<String> = emptyList())

object PresentationReader {
    fun read(input: InputStream): List<PresentationSlide> {
        val entries = OfficeArchive.read(input) { it.startsWith("ppt/") }
        val presentation = OfficeArchive.xml(entries["ppt/presentation.xml"]
            ?: throw IllegalArgumentException("This is not a readable PPTX presentation."))
        val relationships = OfficeArchive.xml(entries["ppt/_rels/presentation.xml.rels"]
            ?: throw IllegalArgumentException("The presentation is missing its slide references."))
        val paths = OfficeArchive.elements(relationships, "Relationship")
            .filter { it.getAttribute("TargetMode") != "External" }
            .associate { it.getAttribute("Id") to OfficeArchive.resolve("ppt", it.getAttribute("Target")) }
        val ids = OfficeArchive.elements(presentation, "sldId")
        require(ids.size <= 2000) { "This presentation has too many slides to preview." }
        val graphics = SlideGraphics(entries, presentation)
        val slides = ids.map { slide ->
            val id = slide.getAttributeNS("http://schemas.openxmlformats.org/officeDocument/2006/relationships", "id")
            val bytes = entries[paths[id]] ?: throw IllegalArgumentException("A slide is missing from this presentation.")
            val xml = OfficeArchive.xml(bytes)
            val visual = graphics.render(paths.getValue(id))
            PresentationSlide(OfficeArchive.elements(xml, "p").map { paragraph ->
                val text = StringBuilder()
                fun visit(node: org.w3c.dom.Node) {
                    when (node.localName) {
                        "t" -> text.append(node.textContent)
                        "br" -> text.append('\n')
                        else -> { var child = node.firstChild; while (child != null) { visit(child); child = child.nextSibling } }
                    }
                }
                visit(paragraph)
                text.toString()
            }.filter { it.isNotBlank() }, visual.first, visual.second)
        }
        require(slides.isNotEmpty()) { "This presentation contains no slides." }
        return slides
    }
}
