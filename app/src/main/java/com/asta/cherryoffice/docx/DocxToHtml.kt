package com.asta.cherryoffice.docx

// Pure JVM/Kotlin converter: DOCX (Office Open XML) -> styled HTML.
// ------------------------------------------------------------------
// IMPORTANT: this file must NOT import anything from android.* so that
// plain local unit tests (no Robolectric, no device) can exercise it.
// Image bytes are handed to [imageHandler], which decides where the
// image lives and what URL the HTML should use (implemented for Android
// in DocxFileReader.kt).
//
// Parsing uses javax.xml (DOM), which exists both on Android and on the
// JVM, instead of XmlPullParser (which only works on a device).

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Locale
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.w3c.dom.Node

/** Why a conversion failed, mapped to a friendly message by the caller. */
enum class DocxFailureKind {
    /** File is password-protected / encrypted. */
    ENCRYPTED,

    /** Not a zip, truncated download, or unreadable XML. */
    CORRUPT,

    /** Valid zip but no readable document content. */
    EMPTY
}

/** Thrown by [DocxToHtmlConverter.convert]. Never crashes the app: catch it. */
class DocxParseException(val kind: DocxFailureKind, message: String) : Exception(message)

/** Result of a successful conversion. */
data class ConvertedDocx(
    /** Complete HTML page (head + CSS + body + pagination script). */
    val html: String,
    /** Non-fatal notes, e.g. skipped features. Shown nowhere by default. */
    val warnings: List<String>
)

// ---------------------------------------------------------------------------
// Small formatting holders (internal - callers only see ConvertedDocx).
// ---------------------------------------------------------------------------

/** Character formatting of one run of text. Null = inherit from style. */
private data class RunStyle(
    val bold: Boolean? = null,
    val italic: Boolean? = null,
    val underline: Boolean? = null,
    val strike: Boolean? = null,
    val color: String? = null,
    val highlight: String? = null,
    val sizePt: Float? = null,
    val font: String? = null,
    val caps: Boolean? = null,
    val smallCaps: Boolean? = null,
    val vertAlign: String? = null, // "super" / "sub"
    val shading: String? = null,
    val letterSpacingPt: Float? = null
)

/** Paragraph formatting. Null = inherit from style. */
private data class ParaStyle(
    val align: String? = null, // left | center | right | justify
    val leftDxa: Int? = null,
    val rightDxa: Int? = null,
    val firstLineDxa: Int? = null,
    val hangingDxa: Int? = null,
    val beforePt: Float? = null,
    val afterPt: Float? = null,
    val lineRule: String? = null,
    val lineVal: Int? = null,
    val shading: String? = null,
    val border: String? = null, // ready-made CSS border value
    val styleId: String? = null,
    val pageBreakBefore: Boolean? = null
)

/** A named style from styles.xml (headings, Normal, ...). */
private data class DocStyle(
    val id: String,
    val name: String,
    val basedOn: String?,
    val run: RunStyle,
    val para: ParaStyle,
    val headingLevel: Int? // 1..6 for Heading 1..6, else null
)

/** One numbering level (which bullet / number shape to use). */
private data class NumLevel(
    val format: String, // "bullet", "decimal", "upperRoman", ...
    val indentDxa: Int
)

/** One open <ul>/<ol> while rendering lists, with its indent level. */
private data class OpenList(val tag: String, val numId: String, val level: Int)

/** One table cell found during the first scan pass. */
private data class CellInfo(
    val element: Element,
    val gridStart: Int, // starting column index in tblGrid
    val gridSpan: Int,
    val vMergeContinue: Boolean
)

// ---------------------------------------------------------------------------
// XML helpers (namespace-agnostic: OOXML uses w:/a:/wp:/... prefixes).
// ---------------------------------------------------------------------------

private fun localName(node: Node): String =
    node.localName ?: node.nodeName.substringAfter(':')

private fun childElements(parent: Element): List<Element> {
    val out = ArrayList<Element>()
    val kids = parent.childNodes
    for (i in 0 until kids.length) {
        val n = kids.item(i)
        if (n.nodeType == Node.ELEMENT_NODE) out.add(n as Element)
    }
    return out
}

/** Attribute value matched by local name, e.g. attr(el, "val") finds w:val. */
private fun attr(el: Element, local: String): String? {
    val attrs = el.attributes ?: return null
    for (i in 0 until attrs.length) {
        val a = attrs.item(i)
        val name = a.localName ?: a.nodeName.substringAfter(':')
        if (name == local) return a.nodeValue
    }
    return null
}

private fun firstChild(parent: Element, vararg names: String): Element? =
    childElements(parent).firstOrNull { localName(it) in names }

/** Unwrap mc:AlternateContent by picking its first Choice branch. */
private fun unwrap(el: Element): Element {
    if (localName(el) != "AlternateContent") return el
    for (c in childElements(el)) {
        if (localName(c) == "Choice") return c
    }
    return el
}

/** Deep text of an element (used for headers/footers/footnotes fallback). */
private fun deepText(el: Element): String {
    val sb = StringBuilder()
    fun walk(n: Node) {
        if (n.nodeType == Node.TEXT_NODE) sb.append(n.nodeValue)
        val kids = n.childNodes
        for (i in 0 until kids.length) walk(kids.item(i))
    }
    walk(el)
    return sb.toString()
}

private fun parseXml(bytes: ByteArray): org.w3c.dom.Document {
    val factory = DocumentBuilderFactory.newInstance()
    factory.isNamespaceAware = true
    // Security: docx files must not fetch external entities (XXE protection).
    try {
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false)
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false)
    } catch (_: Exception) {
        // Older parsers may not know these flags; parsing still works.
    }
    val builder = factory.newDocumentBuilder()
    return builder.parse(ByteArrayInputStream(bytes))
}

// ---------------------------------------------------------------------------
// Unit conversions used by Word.
// ---------------------------------------------------------------------------

private const val DXA_PER_INCH = 1440f
private const val PX_PER_INCH = 96f
private fun dxaToPx(dxa: Int): Float = dxa / DXA_PER_INCH * PX_PER_INCH
private fun ptToPx(pt: Float): Float = pt / 72f * PX_PER_INCH
private fun emuToPx(emu: Long): Float = emu / 914400f * PX_PER_INCH
private fun Float.px1(): String = String.format(Locale.US, "%.1f", this)

/** #RRGGBB or #RGB only; Word's "auto" and anything else mean "inherit". */
private fun cleanColor(raw: String?): String? {
    if (raw == null || raw.equals("auto", ignoreCase = true)) return null
    val hex = raw.trim().removePrefix("#")
    if (hex.length != 6 && hex.length != 3) return null
    if (!hex.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) return null
    return "#" + hex.lowercase(Locale.US)
}

private val HIGHLIGHTS = mapOf(
    "yellow" to "#ffff00", "green" to "#00ff00", "cyan" to "#00ffff",
    "magenta" to "#ff00ff", "blue" to "#0000ff", "red" to "#ff0000",
    "darkBlue" to "#000080", "darkCyan" to "#008080", "darkGreen" to "#008000",
    "darkMagenta" to "#800080", "darkRed" to "#800000", "darkYellow" to "#808000",
    "darkGray" to "#808080", "lightGray" to "#c0c0c0", "black" to "#000000"
)

/** Escape user text for HTML. */
private fun esc(text: String): String {
    val sb = StringBuilder(text.length + 16)
    for (c in text) {
        when (c) {
            '&' -> sb.append("&amp;")
            '<' -> sb.append("&lt;")
            '>' -> sb.append("&gt;")
            '"' -> sb.append("&quot;")
            else -> sb.append(c)
        }
    }
    return sb.toString()
}

// ---------------------------------------------------------------------------
// The converter.
// ---------------------------------------------------------------------------

object DocxToHtmlConverter {

    // Safety caps so a malicious "zip bomb" cannot freeze or crash the app.
    private const val MAX_ENTRIES = 2000
    private const val MAX_ENTRY_BYTES = 30 * 1024 * 1024
    private const val MAX_TOTAL_BYTES = 100 * 1024 * 1024

    /**
     * Convert raw .docx bytes to a full HTML page.
     *
     * @param docxBytes the whole .docx file in memory.
     * @param imageHandler called for every embedded image with (fileName, bytes);
     *   returns the URL the HTML should use. The default keeps a relative
     *   "media/..." URL; the Android wrapper saves (and downsamples) the file.
     */
    fun convert(
        docxBytes: ByteArray,
        imageHandler: (fileName: String, bytes: ByteArray) -> String = { name, _ -> "media/$name" }
    ): ConvertedDocx {
        val parts = unzip(docxBytes)
        if (parts.keys.any { it.contains("EncryptedPackage") }) {
            throw DocxParseException(DocxFailureKind.ENCRYPTED, "password-protected")
        }
        val docBytes = parts["word/document.xml"]
            ?: throw DocxParseException(
                DocxFailureKind.CORRUPT,
                "missing word/document.xml - not a valid Word file"
            )
        val renderer = Renderer(parts, imageHandler)
        return renderer.render(docBytes)
    }

    private fun unzip(docxBytes: ByteArray): Map<String, ByteArray> {
        val out = LinkedHashMap<String, ByteArray>()
        try {
            ZipInputStream(ByteArrayInputStream(docxBytes)).use { zip ->
                var total = 0L
                var entry = zip.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        if (out.size >= MAX_ENTRIES) break
                        val buffer = ByteArrayOutputStream()
                        val chunk = ByteArray(8192)
                        var entryBytes = 0
                        while (true) {
                            val n = zip.read(chunk)
                            if (n < 0) break
                            entryBytes += n
                            total += n
                            if (entryBytes > MAX_ENTRY_BYTES || total > MAX_TOTAL_BYTES) {
                                throw DocxParseException(DocxFailureKind.CORRUPT, "file too large")
                            }
                            buffer.write(chunk, 0, n)
                        }
                        out[entry.name] = buffer.toByteArray()
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        } catch (e: DocxParseException) {
            throw e
        } catch (_: Exception) {
            throw DocxParseException(DocxFailureKind.CORRUPT, "cannot unzip file")
        }
        if (out.isEmpty()) throw DocxParseException(DocxFailureKind.CORRUPT, "empty archive")
        return out
    }

    // ------------------------------------------------------------------
    // Per-document renderer: holds styles, numbering, links, warnings.
    // ------------------------------------------------------------------

    private class Renderer(
        val parts: Map<String, ByteArray>,
        val imageHandler: (String, ByteArray) -> String
    ) {
        val warnings = ArrayList<String>()
        val rels = HashMap<String, String>() // rId -> hyperlink target
        val imageRels = HashMap<String, String>() // rId -> media file name
        val styles = HashMap<String, DocStyle>()
        var docDefaultRun = RunStyle(sizePt = 11f)
        var docDefaultFont = "Calibri, 'Segoe UI', Arial, sans-serif"
        val numbering = HashMap<String, Map<Int, NumLevel>>() // numId -> (ilvl -> level)
        val footnotes = HashMap<String, String>() // id -> html
        var footnoteSeq = 0
        val usedFootnoteIds = ArrayList<String>()
        var skippedComments = 0
        var skippedShapes = 0

        // ----- styles.xml ------------------------------------------------

        fun parseStyles() {
            val bytes = parts["word/styles.xml"] ?: return
            val root = try {
                parseXml(bytes).documentElement
            } catch (_: Exception) {
                return
            }
            for (styleEl in childElements(root)) {
                if (localName(styleEl) != "style") {
                    if (localName(styleEl) == "docDefaults") parseDocDefaults(styleEl)
                    continue
                }
                val id = attr(styleEl, "styleId") ?: continue
                val name = firstChild(styleEl, "name")?.let { attr(it, "val") } ?: id
                var basedOn: String? = null
                var run = RunStyle()
                var para = ParaStyle()
                for (c in childElements(styleEl)) {
                    when (localName(c)) {
                        "basedOn" -> basedOn = attr(c, "val")
                        "rPr" -> run = readRunProps(c, run)
                        "pPr" -> para = readParaProps(c, para)
                    }
                }
                // Headings: "Heading1".."Heading6" (localized names differ, ids don't).
                val headingLevel = Regex("(?i)heading\\s?([1-6])").find(id)?.groupValues
                    ?.getOrNull(1)?.toIntOrNull()
                    ?: Regex("(?i)heading\\s?([1-6])").find(name)?.groupValues
                        ?.getOrNull(1)?.toIntOrNull()
                styles[id] = DocStyle(id, name, basedOn, run, para, headingLevel)
            }
        }

        private fun parseDocDefaults(el: Element) {
            for (c in childElements(el)) {
                when (localName(c)) {
                    "rPrDefault" -> {
                        val rPr = firstChild(c, "rPr") ?: continue
                        docDefaultRun = readRunProps(rPr, RunStyle(sizePt = 11f))
                        runFont(docDefaultRun)?.let { docDefaultFont = it }
                    }
                }
            }
        }

        /** Merge a style with its basedOn chain (max depth guards cycles). */
        fun resolveStyle(styleId: String?): Pair<RunStyle, ParaStyle> {
            var run = RunStyle()
            var para = ParaStyle()
            var current = styleId
            var depth = 0
            val chain = ArrayList<DocStyle>()
            while (current != null && depth < 8) {
                val s = styles[current] ?: break
                chain.add(0, s)
                current = s.basedOn
                depth++
            }
            for (s in chain) {
                run = mergeRun(run, s.run)
                para = mergePara(para, s.para)
            }
            run = mergeRun(
                RunStyle(sizePt = docDefaultRun.sizePt, font = docDefaultFont),
                mergeRun(RunStyle(font = runFont(docDefaultRun)), run)
            )
            return run to para
        }

        // ----- numbering.xml (bullets & numbered lists) -------------------

        fun parseNumbering() {
            val bytes = parts["word/numbering.xml"] ?: return
            val root = try {
                parseXml(bytes).documentElement
            } catch (_: Exception) {
                return
            }
            val abstracts = HashMap<String, Map<Int, NumLevel>>()
            val numToAbstract = HashMap<String, String>()
            // Level start overrides: numId -> (ilvl -> start). Applied loosely
            // (restart numbering); full restart semantics are approximated.
            for (top in childElements(root)) {
                when (localName(top)) {
                    "abstractNum" -> {
                        val id = attr(top, "abstractNumId") ?: continue
                        val levels = HashMap<Int, NumLevel>()
                        for (c in childElements(top)) {
                            if (localName(c) != "lvl") continue
                            val ilvl = attr(c, "ilvl")?.toIntOrNull() ?: continue
                            var fmt = "decimal"
                            var indent = 720 // default 0.5 inch
                            for (lc in childElements(c)) {
                                when (localName(lc)) {
                                    "numFmt" -> fmt = attr(lc, "val") ?: "decimal"
                                    "pPr" ->
                                        firstChild(lc, "ind")?.let {
                                            attr(it, "left")?.toIntOrNull()?.let { v -> indent = v }
                                        }
                                }
                            }
                            levels[ilvl] = NumLevel(fmt, indent)
                        }
                        abstracts[id] = levels
                    }
                    "num" -> {
                        val numId = attr(top, "numId") ?: continue
                        val absId = firstChild(top, "abstractNumId")?.let { attr(it, "val") }
                            ?: continue
                        numToAbstract[numId] = absId
                    }
                }
            }
            for ((numId, absId) in numToAbstract) {
                abstracts[absId]?.let { numbering[numId] = it }
            }
        }

        // ----- relationships (hyperlinks + images) -------------------------

        fun parseRels() {
            val bytes = parts["word/_rels/document.xml.rels"] ?: return
            val root = try {
                parseXml(bytes).documentElement
            } catch (_: Exception) {
                return
            }
            for (rel in childElements(root)) {
                if (localName(rel) != "Relationship") continue
                val id = attr(rel, "Id") ?: continue
                val target = attr(rel, "Target") ?: continue
                val type = attr(rel, "Type") ?: ""
                when {
                    type.endsWith("/hyperlink") -> rels[id] = target
                    type.endsWith("/image") -> imageRels[id] = target.substringAfterLast('/')
                }
            }
        }

        // ----- headers, footers, footnotes ---------------------------------

        /** Render header/footer/footnote parts into HTML fragments. */
        fun parseSubPart(name: String, allowLists: Boolean = false): String {
            val bytes = parts[name] ?: return ""
            val root = try {
                parseXml(bytes).documentElement
            } catch (_: Exception) {
                return ""
            }
            val body = BodyRenderer(this)
            for (c in childElements(unwrap(root))) {
                if (localName(c) == "body" || localName(c) == "hdr" || localName(c) == "ftr" ||
                    localName(c) == "footnotes" || localName(c) == "endnotes"
                ) {
                    for (block in childElements(c)) body.renderBlock(block, allowLists)
                } else {
                    body.renderBlock(c, allowLists)
                }
            }
            return body.finish()
        }

        fun parseFootnotes() {
            val bytes = parts["word/footnotes.xml"] ?: return
            val root = try {
                parseXml(bytes).documentElement
            } catch (_: Exception) {
                return
            }
            for (fn in childElements(root)) {
                if (localName(fn) != "footnote") continue
                val id = attr(fn, "id") ?: continue
                if (id == "-1" || id == "0") continue // separator marks, not content
                val body = BodyRenderer(this)
                for (block in childElements(fn)) body.renderBlock(block, false)
                footnotes[id] = body.finish()
            }
        }

        // ----- main render ---------------------------------------------------

        fun render(docBytes: ByteArray): ConvertedDocx {
            parseStyles()
            parseNumbering()
            parseRels()
            parseFootnotes()
            // First header/footer part wins (Word usually has just one pair).
            val headerHtml = parts.keys
                .filter { it.matches(Regex("word/header\\d*\\.xml")) }
                .sorted()
                .firstNotNullOfOrNull { parseSubPart(it) }
                .orEmpty()
            val footerHtml = parts.keys
                .filter { it.matches(Regex("word/footer\\d*\\.xml")) }
                .sorted()
                .firstNotNullOfOrNull { parseSubPart(it) }
                .orEmpty()

            val root = try {
                parseXml(docBytes).documentElement
            } catch (_: Exception) {
                throw DocxParseException(DocxFailureKind.CORRUPT, "document.xml is broken")
            }
            val bodyEl = childElements(root).firstOrNull { localName(it) == "body" }
                ?: throw DocxParseException(DocxFailureKind.EMPTY, "no readable content")
            val body = BodyRenderer(this)
            for (block in childElements(bodyEl)) {
                val n = localName(block)
                if (n == "sectPr") continue // section settings live at the end; page size ignored
                body.renderBlock(block, true)
            }
            var content = body.finish()
            if (content.isBlank()) {
                throw DocxParseException(DocxFailureKind.EMPTY, "document has no readable text")
            }
            // Endnotes section for collected footnotes.
            if (usedFootnoteIds.isNotEmpty()) {
                val notes = StringBuilder()
                notes.append("<div class=\"endnotes\"><h2>Notes</h2><ol>")
                for (fid in usedFootnoteIds) {
                    notes.append("<li id=\"note-").append(esc(fid)).append("\">")
                    notes.append(footnotes[fid] ?: "")
                    notes.append("</li>")
                }
                notes.append("</ol></div>")
                content += notes.toString()
            }
            if (skippedComments > 0) warnings.add("$skippedComments comment(s) skipped")
            if (skippedShapes > 0) warnings.add("$skippedShapes shape(s) simplified")
            return ConvertedDocx(
                html = pageSkeleton(content, headerHtml, footerHtml),
                warnings = warnings.toList()
            )
        }
    }

    // ------------------------------------------------------------------
    // Body renderer: paragraphs, lists, tables, images, breaks.
    // ------------------------------------------------------------------

    private class BodyRenderer(val r: Renderer) {
        private val out = StringBuilder()
        private val lists = ArrayDeque<OpenList>()
        private var inTableDepth = 0

        fun finish(): String {
            closeLists(0)
            return out.toString()
        }

        fun renderBlock(el: Element, allowLists: Boolean) {
            when (localName(unwrap(el))) {
                "p" -> renderParagraph(el, allowLists)
                "tbl" -> {
                    closeLists(0)
                    renderTable(el)
                }
                "ins", "smartTag", "permStart", "permEnd", "proofErr", "bookmarkStart",
                "bookmarkEnd", "commentRangeStart", "commentRangeEnd", "moveFrom",
                "moveFromRangeStart", "moveFromRangeEnd" -> {
                    // Transparent wrappers / tracked-move markers: render children.
                    for (c in childElements(el)) renderBlock(c, allowLists)
                }
                "moveTo", "moveToRangeStart", "moveToRangeEnd" -> {
                    for (c in childElements(el)) renderBlock(c, allowLists)
                }
                "del" -> {
                    // Deleted text: show struck through, like Word with markup.
                    for (c in childElements(el)) {
                        if (localName(c) == "p") renderParagraph(c, allowLists, forceStrike = true)
                        else renderBlock(c, allowLists)
                    }
                }
                "commentReference" ->
                    r.skippedComments++ // counted in renderRun; harmless here too
                "sectPr", "sdt" -> {
                    // sdt (structured controls like TOC blocks): render inner content.
                    for (c in childElements(el)) {
                        val inner = firstChild(c, "sdtContent")
                        if (inner != null) {
                            for (b in childElements(inner)) renderBlock(b, allowLists)
                        } else if (localName(c) != "sdtPr") {
                            renderBlock(c, allowLists)
                        }
                    }
                }
                else -> { /* unknown block: skip silently */ }
            }
        }

        // ----- paragraphs ----------------------------------------------

        fun renderParagraph(pEl: Element, allowLists: Boolean, forceStrike: Boolean = false) {
            var pPr = ParaStyle()
            var numId: String? = null
            var ilvl = 0
            for (c in childElements(pEl)) {
                if (localName(c) == "pPr") {
                    pPr = readParaProps(c, pPr)
                    for (pp in childElements(c)) {
                        if (localName(pp) == "numPr") {
                            for (np in childElements(pp)) {
                                when (localName(np)) {
                                    "numId" -> attr(np, "val")?.let { numId = it }
                                    "ilvl" -> attr(np, "val")?.toIntOrNull()?.let { ilvl = it }
                                }
                            }
                        }
                    }
                }
            }
            val (styleRun, stylePara) = r.resolveStyle(pPr.styleId)
            val para = mergePara(stylePara, pPr)
            val heading = pPr.styleId?.let { r.styles[it]?.headingLevel }

            // Hyperlink runs live under w:hyperlink, not w:r - collect inner HTML.
            val inner = StringBuilder()
            for (c in childElements(pEl)) {
                when (localName(c)) {
                    "r" -> inner.append(renderRun(c, styleRun, forceStrike))
                    "hyperlink" -> {
                        val target = attr(c, "id")?.let { r.rels[it] }
                        val linkInner = StringBuilder()
                        for (lr in childElements(c)) {
                            if (localName(lr) == "r") linkInner.append(renderRun(lr, styleRun, forceStrike))
                        }
                        if (linkInner.isEmpty()) continue
                        if (target != null) {
                            inner.append("<a href=\"").append(esc(target)).append("\">")
                            inner.append(linkInner).append("</a>")
                        } else {
                            inner.append(linkInner) // internal bookmark link: plain text
                        }
                    }
                    "fldSimple" -> {
                        val instr = attr(c, "instr") ?: ""
                        inner.append(fieldHtml(instr, c, styleRun))
                    }
                }
            }
            if (inner.isBlank() && para.pageBreakBefore != true && numId == null) {
                // Truly empty paragraph: keep a small vertical gap like Word does.
                out.append("<p class=\"empty\">&nbsp;</p>")
                return
            }

            val css = para.toCss()
            val breakAttr = if (para.pageBreakBefore == true) " data-page-break-before" else ""

            // List paragraph?
            val listLevel = if (allowLists && numId != null && numId != "0") {
                r.numbering[numId]?.get(ilvl)
            } else null
            if (listLevel != null && numId != null) {
                renderListItem(numId, ilvl, listLevel, inner.toString(), css)
                return
            }
            closeLists(0)

            val tag = if (heading != null) "h$heading" else "p"
            out.append("<").append(tag)
            if (css.isNotEmpty()) out.append(" style=\"").append(css).append("\"")
            out.append(breakAttr).append(">")
            out.append(inner)
            out.append("</").append(tag).append(">")
        }

        private fun renderListItem(
            numId: String,
            ilvl: Int,
            level: NumLevel,
            innerHtml: String,
            css: String
        ) {
            val isBullet = level.format.equals("bullet", ignoreCase = true)
            val tag = if (isBullet) "ul" else "ol"
            // Pop lists deeper than this level, or same-level lists of another numbering.
            while (lists.isNotEmpty() && (lists.last().level > ilvl ||
                        (lists.last().level == ilvl && lists.last().numId != numId))
            ) {
                out.append("</").append(lists.removeLast().tag).append(">")
            }
            val top = lists.lastOrNull()
            if (top == null || top.level < ilvl || (top.level == ilvl && top.numId != numId)) {
                // Open intermediate levels too (Word allows jumping ilvl 0 -> 2).
                val from = if (top == null) 0 else top.level + 1
                for (lvl in from..ilvl) {
                    val listCss = if (lvl == ilvl) {
                        "margin-left:" + dxaToPx(level.indentDxa).px1() + "px;" + cssListStyle(level.format)
                    } else {
                        "margin-left:" + (lvl * 24) + "px;"
                    }
                    out.append("<").append(tag).append(" style=\"").append(listCss).append("\">")
                    lists.add(OpenList(tag, numId, lvl))
                }
            }
            out.append("<li")
            if (css.isNotEmpty()) out.append(" style=\"").append(css).append("\"")
            out.append(">").append(innerHtml).append("</li>")
        }

        private fun closeLists(keep: Int) {
            while (lists.size > keep) out.append("</").append(lists.removeLast().tag).append(">")
        }

        private fun cssListStyle(format: String): String = when (format.lowercase(Locale.US)) {
            "upperroman" -> "list-style-type:upper-roman;"
            "lowerroman" -> "list-style-type:lower-roman;"
            "upperletter" -> "list-style-type:upper-alpha;"
            "lowerletter" -> "list-style-type:lower-alpha;"
            "bullet" -> "list-style-type:disc;"
            "decimalzero" -> "list-style-type:decimal-leading-zero;"
            "none" -> "list-style-type:none;"
            else -> "" // decimal and exotic formats: browser default numbering
        }

        // ----- runs -----------------------------------------------------

        fun renderRun(rEl: Element, styleRun: RunStyle, forceStrike: Boolean = false): String {
            var run = RunStyle()
            val sb = StringBuilder()
            for (c in childElements(rEl)) {
                when (localName(c)) {
                    "rPr" -> run = readRunProps(c, run)
                    "t" -> {
                        var t = c.textContent ?: ""
                        if (attr(c, "space") != "preserve") {
                            // Word collapses edge spaces in HTML the same way browsers do;
                            // keep inner spacing intact.
                            t = t.trim(' ', '\t')
                        }
                        sb.append(esc(t))
                    }
                    "tab" -> sb.append("&nbsp;&nbsp;&nbsp;&nbsp;")
                    "br" -> {
                        when (attr(c, "type")) {
                            "page", "column" -> sb.append("<span class=\"pagebreak\" data-page-break-before></span>")
                            else -> sb.append("<br>")
                        }
                    }
                    "lastRenderedPageBreak" -> {
                        sb.append("<span class=\"pagebreak\" data-page-break-before></span>")
                    }
                    "sym" -> {
                        // Symbol font character, e.g. Wingdings bullets.
                        val code = attr(c, "char") ?: continue
                        val cp = code.toIntOrNull(16) ?: continue
                        sb.append("&#").append(cp).append(";")
                    }
                    "fldChar" -> { /* field separators: handled with instrText below */ }
                    "instrText" -> {
                        val instr = c.textContent?.trim() ?: ""
                        // PAGE / NUMPAGES fields that appear as complex fields.
                        if (instr.startsWith("PAGE", ignoreCase = true)) {
                            sb.append("<span class=\"pnum\"></span>")
                        } else if (instr.startsWith("NUMPAGES", ignoreCase = true)) {
                            sb.append("<span class=\"ptotal\"></span>")
                        }
                    }
                    "footnoteReference" -> {
                        val id = attr(c, "id") ?: continue
                        r.footnoteSeq++
                        val n = r.footnoteSeq
                        if (!r.usedFootnoteIds.contains(id)) r.usedFootnoteIds.add(id)
                        sb.append("<a class=\"fnref\" href=\"#note-").append(esc(id))
                        sb.append("\"><sup>[").append(n).append("]</sup></a>")
                    }
                    "endnoteReference" -> {
                        val id = attr(c, "id") ?: continue
                        r.footnoteSeq++
                        val n = r.footnoteSeq
                        if (!r.usedFootnoteIds.contains(id)) r.usedFootnoteIds.add(id)
                        sb.append("<a class=\"fnref\" href=\"#note-").append(esc(id))
                        sb.append("\"><sup>[").append(n).append("]</sup></a>")
                    }
                    "commentReference", "commentRangeStart", "commentRangeEnd" ->
                        r.skippedComments++
                    "drawing" -> renderDrawing(c)?.let { sb.append(it) }
                    "pict" -> renderPict(c)?.let { sb.append(it) }
                    "object" -> {
                        sb.append("<span class=\"unsupported\">[Embedded object - not supported]</span>")
                    }
                }
            }
            if (sb.isEmpty()) return ""
            var merged = mergeRun(styleRun, run)
            if (forceStrike) merged = merged.copy(strike = true)
            val css = merged.toCss()
            return if (css.isEmpty()) sb.toString()
            else "<span style=\"$css\">$sb</span>"
        }

        /** Simple fields: <w:fldSimple w:instr=" PAGE ">. */
        private fun fieldHtml(instr: String, fldEl: Element, styleRun: RunStyle): String {
            val up = instr.trim().uppercase(Locale.US)
            val sb = StringBuilder()
            when {
                up.startsWith("PAGE") -> sb.append("<span class=\"pnum\"></span>")
                up.startsWith("NUMPAGES") -> sb.append("<span class=\"ptotal\"></span>")
                up.startsWith("HYPERLINK") -> {
                    val url = up.substringAfter("HYPERLINK").trim().trim('"')
                    sb.append("<a href=\"").append(esc(url)).append("\">")
                    for (c in childElements(fldEl)) {
                        if (localName(c) == "r") sb.append(renderRun(c, styleRun))
                    }
                    sb.append("</a>")
                    return sb.toString()
                }
                else -> {
                    // Other fields (DATE, TOC entries, ...): show their cached result text.
                    for (c in childElements(fldEl)) {
                        if (localName(c) == "r") sb.append(renderRun(c, styleRun))
                    }
                }
            }
            return sb.toString()
        }

        // ----- images ----------------------------------------------------

        /** Modern drawings: wp:inline (inline) and wp:anchor (floating -> inline). */
        private fun renderDrawing(drawingEl: Element): String? {
            for (wrap in childElements(drawingEl)) {
                val w = unwrap(wrap)
                val lname = localName(w)
                if (lname != "inline" && lname != "anchor") continue
                var widthPx: Float? = null
                var embed: String? = null
                var alt = "image"
                for (part in childElements(w)) {
                    when (localName(part)) {
                        "extent" -> {
                            val cx = attr(part, "cx")?.toLongOrNull()
                            if (cx != null) widthPx = emuToPx(cx)
                        }
                        "docPr" -> attr(part, "descr")?.let { if (it.isNotBlank()) alt = it }
                        "graphic" -> embed = findBlipEmbed(part)
                    }
                }
                if (embed != null) {
                    r.skippedShapes++ // anchor positioning is simplified to inline below
                    return imageTag(embed, widthPx, alt)
                }
            }
            return null
        }

        private fun findBlipEmbed(el: Element): String? {
            if (localName(el) == "blip") {
                return attr(el, "embed")?.takeIf { it.isNotBlank() }
            }
            for (c in childElements(el)) {
                findBlipEmbed(unwrap(c))?.let { return it }
            }
            return null
        }

        /** Legacy VML pictures (Word 97-2003 style drawings inside .docx). */
        private fun renderPict(pictEl: Element): String? {
            var embed: String? = null
            var widthPx: Float? = null
            var fallbackText = ""
            fun walk(e: Element) {
                when (localName(e)) {
                    "imagedata" -> {
                        attr(e, "id")?.takeIf { it.isNotBlank() }?.let { embed = it }
                    }
                    "shape", "rect" -> {
                        // style="width:120pt;height:80pt"
                        attr(e, "style")?.let { style ->
                            Regex("width\\s*:\\s*([0-9.]+)pt").find(style)?.groupValues
                                ?.getOrNull(1)?.toFloatOrNull()?.let { widthPx = ptToPx(it) }
                        }
                    }
                    "txbxContent" -> {
                        // Text inside the shape: render its paragraphs inline.
                        val inner = BodyRenderer(r)
                        for (b in childElements(e)) inner.renderBlock(b, false)
                        fallbackText += inner.finish()
                    }
                }
                for (c in childElements(e)) walk(c)
            }
            walk(pictEl)
            return when {
                embed != null -> imageTag(embed!!, widthPx, "image")
                fallbackText.isNotBlank() ->
                    "<span class=\"shape-text\">$fallbackText</span>".also { r.skippedShapes++ }
                else -> null
            }
        }

        private fun imageTag(embedId: String, widthPx: Float?, alt: String): String {
            val fileName = r.imageRels[embedId] ?: return ""
            val bytes = r.parts["word/media/$fileName"] ?: r.parts["word/$fileName"]
                ?: return "<span class=\"unsupported\">[Missing image]</span>"
            val url = try {
                r.imageHandler(fileName, bytes)
            } catch (_: Exception) {
                return "<span class=\"unsupported\">[Image could not be loaded]</span>"
            }
            val style = if (widthPx != null && widthPx > 0) {
                " style=\"width:" + String.format(Locale.US, "%.0f", widthPx.coerceAtMost(640f)) + "px;\""
            } else ""
            return "<img src=\"" + esc(url) + "\" alt=\"" + esc(alt) + "\"" + style + ">"
        }

        // ----- tables -----------------------------------------------------

        fun renderTable(tblEl: Element) {
            if (inTableDepth >= 2) {
                out.append("<p class=\"unsupported\">[Nested table - simplified]</p>")
                return
            }
            inTableDepth++
            try {
                var bordersCss = ""
                var align = ""
                var widthCss = "width:100%;min-width:100%;"
                var cellMarginPx = 4f
                val gridCols = ArrayList<Int>()
                val rows = ArrayList<Element>()
                for (c in childElements(tblEl)) {
                    when (localName(c)) {
                        "tblPr" -> for (p in childElements(c)) {
                            when (localName(p)) {
                                "tblBorders" -> readBorders(p)?.let { bordersCss = it }
                                "jc" -> align = when (attr(p, "val")) {
                                    "center" -> "margin-left:auto;margin-right:auto;"
                                    "right", "end" -> "margin-left:auto;"
                                    else -> ""
                                }
                                "tblW" -> {
                                    val w = attr(p, "w")?.toFloatOrNull()
                                    val t = attr(p, "type")
                                    if (w != null && w > 0) {
                                        widthCss = if (t == "pct") "width:${w / 50f}%;min-width:100%;"
                                        else "width:100%;min-width:${dxaToPx(w.toInt()).px1()}px;"
                                    }
                                }
                                "cellMar" -> for (m in childElements(p)) {
                                    if (localName(m) == "left" || localName(m) == "right") {
                                        attr(m, "w")?.toIntOrNull()?.let {
                                            cellMarginPx = dxaToPx(it)
                                        }
                                    }
                                }
                            }
                        }
                        "tblGrid" -> for (g in childElements(c)) {
                            if (localName(g) == "gridCol") {
                                gridCols.add(attr(g, "w")?.toIntOrNull() ?: 1440)
                            }
                        }
                        "tr" -> rows.add(c)
                    }
                }
                if (rows.isEmpty()) return
                val totalGrid = gridCols.sum().takeIf { it > 0 } ?: (rows.size * 1440)

                // Pass 1: scan cells (handles gridSpan + vertical merges).
                // coveredPerRow[r] = columns covered by a vMerge continue in row r.
                data class Row(val cells: List<CellInfo>)
                val scanned = ArrayList<Row>()
                val coveredPerRow = ArrayList<Set<Int>>()
                val rowspanLeft = HashMap<Int, Int>() // col -> rows still merged below
                for (tr in rows) {
                    val cells = ArrayList<CellInfo>()
                    val covered = HashSet<Int>()
                    var col = 0
                    // Skip columns covered by a merge from above.
                    fun skipCovered() {
                        while ((rowspanLeft[col] ?: 0) > 0) {
                            rowspanLeft[col] = (rowspanLeft[col] ?: 1) - 1
                            covered.add(col)
                            col++
                        }
                    }
                    for (tc in childElements(tr)) {
                        if (localName(tc) != "tc") continue
                        skipCovered()
                        var span = 1
                        var vContinue = false
                        for (tcp in childElements(tc)) {
                            if (localName(tcp) != "tcPr") continue
                            for (pp in childElements(tcp)) {
                                when (localName(pp)) {
                                    "gridSpan" ->
                                        span = attr(pp, "val")?.toIntOrNull()?.coerceAtLeast(1) ?: 1
                                    "vMerge" ->
                                        vContinue = attr(pp, "val") == null || attr(pp, "val") == "continue"
                                }
                            }
                        }
                        if (vContinue) {
                            // Covered by the cell above: extend its rowspan.
                            for (k in col until col + span) {
                                rowspanLeft[k] = (rowspanLeft[k] ?: 0) + 1
                                covered.add(k)
                            }
                            col += span
                            continue
                        }
                        cells.add(CellInfo(tc, col, span, vMergeContinue = false))
                        col += span
                    }
                    scanned.add(Row(cells))
                    coveredPerRow.add(covered)
                }
                // Pass 2: rowspan = 1 + consecutive rows below whose columns in
                // this cell's range are ALL merge-continues (a gap or a new
                // origin cell ends the merge).
                val rowspans = HashMap<CellInfo, Int>()
                for ((ri, row) in scanned.withIndex()) {
                    for (cell in row.cells) {
                        var rs = 1
                        var rj = ri + 1
                        while (rj < scanned.size && rs < 50) {
                            val range = cell.gridStart until cell.gridStart + cell.gridSpan
                            val belowOrigins = scanned[rj].cells.map { it.gridStart }.toSet()
                            var extends = true
                            for (k in range) {
                                if (k in belowOrigins || k !in coveredPerRow[rj]) {
                                    extends = false
                                    break
                                }
                            }
                            if (!extends) break
                            rs++
                            rj++
                        }
                        rowspans[cell] = rs
                    }
                }

                out.append("<div class=\"table-container\">")
                out.append("<table style=\"").append(widthCss).append(align)
                    .append("border-collapse:collapse;")
                if (bordersCss.startsWith("border:")) out.append(bordersCss)
                out.append("\">")
                if (gridCols.isNotEmpty()) {
                    out.append("<colgroup>")
                    for (g in gridCols) {
                        val colMinPx = maxOf(dxaToPx(g), 70f).px1()
                        out.append("<col style=\"width:")
                            .append(String.format(Locale.US, "%.2f", g * 100f / totalGrid))
                            .append("%;min-width:")
                            .append(colMinPx)
                            .append("px;\">")
                    }
                    out.append("</colgroup>")
                }
                for (row in scanned) {
                    out.append("<tr>")
                    for (cell in row.cells) {
                        out.append("<td")
                        if (cell.gridSpan > 1) out.append(" colspan=\"").append(cell.gridSpan).append("\"")
                        val rs = rowspans[cell] ?: 1
                        if (rs > 1) out.append(" rowspan=\"").append(rs).append("\"")
                        // Cell style: width, shading, borders, vertical align.
                        val css = StringBuilder()
                        var vAlign = "top"
                        for (tcp in childElements(cell.element)) {
                            if (localName(tcp) != "tcPr") continue
                            for (pp in childElements(tcp)) {
                                when (localName(pp)) {
                                    "tcW" -> {
                                        val w = attr(pp, "w")?.toFloatOrNull()
                                        val t = attr(pp, "type")
                                        if (w != null && w > 0 && t != "pct") {
                                            val px = dxaToPx(w.toInt())
                                            css.append("min-width:").append(maxOf(px, 70f).px1()).append("px;")
                                        }
                                    }
                                    "shd" -> cleanColor(attr(pp, "fill"))?.let {
                                        css.append("background-color:").append(it).append(";")
                                    }
                                    "tcBorders" -> readBorders(pp)?.let { css.append(it) }
                                    "vAlign" -> attr(pp, "val")?.let {
                                        vAlign = when (it) {
                                            "center" -> "middle"
                                            "bottom" -> "bottom"
                                            else -> "top"
                                        }
                                    }
                                }
                            }
                        }
                        css.append("min-width:70px;word-break:normal;white-space:normal;")
                        css.append("vertical-align:").append(vAlign).append(";")
                        css.append("padding:").append(maxOf(cellMarginPx, 4f).px1()).append("px;")
                        if (!bordersCss.startsWith("border:") && bordersCss.isNotEmpty()) {
                            css.append(bordersCss)
                        } else if (bordersCss.isEmpty()) {
                            css.append("border:1px solid #d1d5db;")
                        }
                        out.append(" style=\"").append(css).append("\">")
                        // Cell content: paragraphs + nested tables.
                        for (b in childElements(cell.element)) {
                            if (localName(b) == "tcPr") continue
                            renderBlock(b, false)
                        }
                        out.append("</td>")
                    }
                    out.append("</tr>")
                }
                out.append("</table></div>")
            } finally {
                inTableDepth--
            }
        }
    }

    // ------------------------------------------------------------------
    // Full HTML page: dark backdrop, white A4 sheets, pagination script.
    // ------------------------------------------------------------------

    private fun pageSkeleton(content: String, headerHtml: String, footerHtml: String): String {
        // NOTE: the script below contains no '$' so Kotlin raw strings are safe.
        return """
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=860, initial-scale=1.0, user-scalable=yes">
<style>
*{box-sizing:border-box}
html,body{margin:0;padding:0;background:#141118;color:#111827;
 font-family:Calibri,'Segoe UI',-apple-system,Roboto,Arial,sans-serif;font-size:11pt;line-height:1.45}
body.dark-mode{background:#0E0C12 !important;color:#E8E5EE !important}
body.dark-mode .sheet{background:#1C1824 !important;color:#E8E5EE !important;border:1px solid #2B2538 !important;box-shadow:0 6px 24px rgba(0,0,0,.7) !important}
body.dark-mode .sheet-head,body.dark-mode .sheet-foot{border-color:#2B2538 !important;color:#9A92A6 !important}
body.dark-mode td,body.dark-mode th{border-color:#383046 !important;color:#E8E5EE !important}
body.dark-mode a{color:#60A5FA !important}
body.dark-mode .pagebreak{border-top-color:#383046 !important}
body.dark-mode .pagebreak::after{background:#1C1824 !important;color:#9A92A6 !important}
body.dark-mode .unsupported{color:#64748B !important}

#pages,#content{width:816px;margin:0 auto;padding:18px 0 140px;position:relative}
.sheet{width:816px;min-height:1056px;background:#FFFFFF;color:#111827;border-radius:4px;
 box-shadow:0 6px 24px rgba(0,0,0,.5),0 0 0 1px rgba(255,255,255,.06);margin:0 auto 20px;
 padding:64px 72px;position:relative;overflow:hidden;word-wrap:normal;overflow-wrap:break-word}
.sheet-head{border-bottom:1px solid #E5E7EB;color:#6B7280;font-size:9pt;
 margin-bottom:18px;padding-bottom:8px}
.sheet-foot{border-top:1px solid #E5E7EB;color:#6B7280;font-size:9pt;
 margin-top:18px;padding-top:8px;text-align:center}
.sheet-body{min-height:860px}
h1{font-size:18pt;margin:14px 0 8px;font-weight:bold}
h2{font-size:14pt;margin:12px 0 6px;font-weight:bold}
h3{font-size:12pt;margin:10px 0 5px;font-weight:bold}
h4{font-size:11pt;margin:8px 0 4px;font-weight:bold}
h5,h6{font-size:10pt;margin:6px 0 3px}
p{margin:0 0 10px;line-height:1.45;word-break:normal}p.empty{margin:0 0 4px;min-height:8px}
ul,ol{margin:4px 0 8px;padding-left:28px}
li{margin:2px 0}
.table-container{width:100%;max-width:100%;overflow-x:auto;-webkit-overflow-scrolling:touch;margin:12px 0}
table{border-collapse:collapse;width:100%;min-width:100%;max-width:none;margin:0;font-size:10pt;word-break:normal}
td,th{padding:6px 10px;vertical-align:top;min-width:70px;word-break:normal !important;overflow-wrap:normal !important;white-space:normal}
img{max-width:100%;height:auto}
a{color:#1A56DB}.fnref{text-decoration:none}
.unsupported{color:#9CA3AF;font-style:italic}
.endnotes h2{font-size:14pt}
.shape-text{display:inline-block;border:1px dashed #9CA3AF;padding:6px;margin:4px 0}
.pagebreak{display:block;height:1px;border-top:1px dashed #CBD5E1;margin:24px 0;position:relative;text-align:center;clear:both}
.pagebreak::after{content:'PAGE BREAK';position:relative;top:-10px;background:#FFFFFF;padding:0 8px;font-size:8pt;color:#94A3B8;letter-spacing:.5px}
</style>
</head>
<body>
<div id="content">
  <div class="sheet">
    ${if (headerHtml.isNotBlank()) "<div class=\"sheet-head\">$headerHtml</div>" else ""}
    <div class="sheet-body">
      $content
    </div>
    ${if (footerHtml.isNotBlank()) "<div class=\"sheet-foot\">$footerHtml</div>" else ""}
  </div>
</div>
<template id="raw-blocks">${content}</template>
<template id="tpl-header">${headerHtml}</template>
<template id="tpl-footer">${footerHtml}</template>
<div id="pages"></div>
<script>
(function(){
var timer=null;
var USABLE_H=920;
function paginate(){
 var raw=document.getElementById('raw-blocks');
 var src=document.getElementById('content');
 var pages=document.getElementById('pages');
 if(!raw||!pages)return;
 var tplContent=raw.content?raw.content.cloneNode(true):null;
 var blocks=[];
 if(tplContent){
  blocks=Array.prototype.slice.call(tplContent.childNodes);
 }else{
  var div=document.createElement('div');div.innerHTML=raw.innerHTML;
  blocks=Array.prototype.slice.call(div.childNodes);
 }
 blocks=blocks.filter(function(b){return b.nodeType===1||(b.nodeType===3&&b.textContent.trim().length>0);});
 if(blocks.length===0)return;
 var head=document.getElementById('tpl-header'),foot=document.getElementById('tpl-footer');
 var headHtml=head?head.innerHTML:'';
 var footHtml=foot?foot.innerHTML:'';
 pages.innerHTML='';window.__tops=[];
 var idx=0,body=null;
 function newsheet(){
  idx++;
  var s=document.createElement('div');s.className='sheet';s.id='sheet-'+idx;
  if(headHtml&&headHtml.replace(/^\s+|\s+$/g,'')){
   var h=document.createElement('div');h.className='sheet-head';h.innerHTML=headHtml;s.appendChild(h);}
  body=document.createElement('div');body.className='sheet-body';s.appendChild(body);
  if(footHtml&&footHtml.replace(/^\s+|\s+$/g,'')){
   var f=document.createElement('div');f.className='sheet-foot';f.innerHTML=footHtml;s.appendChild(f);}
  s.querySelectorAll('.pnum').forEach(function(e){e.textContent=idx;});
  pages.appendChild(s);
  window.__tops.push(s.offsetTop);
 }
 newsheet();
 for(var i=0;i<blocks.length;i++){
  var b=blocks[i];
  var force=b.nodeType===1&&(
   (b.hasAttribute&&b.hasAttribute('data-page-break-before'))||
   (b.classList&&b.classList.contains('pagebreak'))||
   (b.querySelector&&b.querySelector('[data-page-break-before]'))||
   (b.querySelector&&b.querySelector('.pagebreak'))
  );
  if(force&&body.childNodes.length>0)newsheet();
  body.appendChild(b);
  if(body.offsetHeight>USABLE_H){
   if(body.childNodes.length>1){
    body.removeChild(b);
    newsheet();
    body.appendChild(b);
   }
  }
 }
 if(idx>0&&body&&body.childNodes.length>0){
  if(src)src.style.display='none';
 }
 pages.querySelectorAll('.ptotal').forEach(function(e){e.textContent=idx;});
 window.__effectivePages=idx;
 if(window.Android&&window.Android.onPagesReady){try{window.Android.onPagesReady(idx);}catch(err){}}
}
function schedule(){if(timer)clearTimeout(timer);timer=setTimeout(paginate,80);}
var pages=document.getElementById('pages');
pages.addEventListener('load',function(e){if(e.target&&e.target.tagName==='IMG')schedule();},true);
document.addEventListener('DOMContentLoaded',paginate);
window.addEventListener('load',paginate);
window.addEventListener('resize',schedule);
window.__repaginate=schedule;
window.__currentPage=function(y){
 if(window.__tops&&window.__tops.length>1){
  var t=window.__tops,i=0;
  for(var k=0;k<t.length;k++){if(y>=t[k]-30)i=k;}
  return i+1;
 }
 return 1;
};
paginate();
})();
</script>
</body>
</html>
""".trimIndent()
    }
}

// ------------------------------------------------------------------
// Run / paragraph property readers.
// ------------------------------------------------------------------

private fun readRunProps(rPr: Element, base: RunStyle): RunStyle {
    var r = base
    for (c in childElements(rPr)) {
        when (localName(c)) {
            "b" -> r = r.copy(bold = attr(c, "val") != "false" && attr(c, "val") != "0")
            "i" -> r = r.copy(italic = attr(c, "val") != "false" && attr(c, "val") != "0")
            "caps" -> r = r.copy(caps = attr(c, "val") != "false" && attr(c, "val") != "0")
            "smallCaps" -> r = r.copy(smallCaps = attr(c, "val") != "false" && attr(c, "val") != "0")
            "strike" -> r = r.copy(strike = attr(c, "val") != "false" && attr(c, "val") != "0")
            "dstrike" -> r = r.copy(strike = true)
            "u" -> r = r.copy(underline = attr(c, "val") != "none")
            "color" -> cleanColor(attr(c, "val"))?.let { r = r.copy(color = it) }
            "highlight" -> HIGHLIGHTS[attr(c, "val")]?.let { r = r.copy(highlight = it) }
            "sz" -> attr(c, "val")?.toFloatOrNull()?.let { r = r.copy(sizePt = it / 2f) }
            "szCs" -> attr(c, "val")?.toFloatOrNull()?.let {
                if (r.sizePt == null) r = r.copy(sizePt = it / 2f)
            }
            "rFonts" -> runFontFrom(c)?.let { r = r.copy(font = it) }
            "vertAlign" -> attr(c, "val")?.let {
                if (it == "superscript" || it == "subscript") r = r.copy(vertAlign = it)
            }
            "shd" -> cleanColor(attr(c, "fill"))?.let { r = r.copy(shading = it) }
            "spacing" -> attr(c, "val")?.toFloatOrNull()?.let {
                r = r.copy(letterSpacingPt = it / 20f)
            }
        }
    }
    return r
}

private fun runFontFrom(rFonts: Element): String? {
    val f = attr(rFonts, "ascii") ?: attr(rFonts, "hAnsi") ?: attr(rFonts, "cs") ?: return null
    if (f.isBlank()) return null
    return "'$f', Calibri, Arial, sans-serif"
}

private fun runFont(r: RunStyle): String? = r.font

private fun readParaProps(pPr: Element, base: ParaStyle): ParaStyle {
    var p = base
    for (c in childElements(pPr)) {
        when (localName(c)) {
            "pStyle" -> attr(c, "val")?.let { p = p.copy(styleId = it) }
            "jc" -> attr(c, "val")?.let {
                p = p.copy(
                    align = when (it) {
                        "center" -> "center"
                        "right", "end" -> "right"
                        "both", "distribute" -> "justify"
                        else -> "left"
                    }
                )
            }
            "ind" -> {
                attr(c, "left")?.toIntOrNull()?.let { p = p.copy(leftDxa = it) }
                attr(c, "right")?.toIntOrNull()?.let { p = p.copy(rightDxa = it) }
                attr(c, "firstLine")?.toIntOrNull()?.let { p = p.copy(firstLineDxa = it) }
                attr(c, "hanging")?.toIntOrNull()?.let { p = p.copy(hangingDxa = it) }
            }
            "spacing" -> {
                attr(c, "before")?.toFloatOrNull()?.let { p = p.copy(beforePt = it / 20f) }
                attr(c, "after")?.toFloatOrNull()?.let { p = p.copy(afterPt = it / 20f) }
                p = p.copy(
                    lineRule = attr(c, "lineRule") ?: p.lineRule,
                    lineVal = attr(c, "line")?.toIntOrNull() ?: p.lineVal
                )
            }
            "shd" -> cleanColor(attr(c, "fill"))?.let { p = p.copy(shading = it) }
            "pBdr" -> readBorders(c)?.let { p = p.copy(border = it) }
            "pageBreakBefore" -> p = p.copy(pageBreakBefore = true)
        }
    }
    return p
}

/** Merge: non-null fields of [over] win. */
private fun mergeRun(base: RunStyle, over: RunStyle) = RunStyle(
    bold = over.bold ?: base.bold,
    italic = over.italic ?: base.italic,
    underline = over.underline ?: base.underline,
    strike = over.strike ?: base.strike,
    color = over.color ?: base.color,
    highlight = over.highlight ?: base.highlight,
    sizePt = over.sizePt ?: base.sizePt,
    font = over.font ?: base.font,
    caps = over.caps ?: base.caps,
    smallCaps = over.smallCaps ?: base.smallCaps,
    vertAlign = over.vertAlign ?: base.vertAlign,
    shading = over.shading ?: base.shading,
    letterSpacingPt = over.letterSpacingPt ?: base.letterSpacingPt
)

private fun mergePara(base: ParaStyle, over: ParaStyle) = ParaStyle(
    align = over.align ?: base.align,
    leftDxa = over.leftDxa ?: base.leftDxa,
    rightDxa = over.rightDxa ?: base.rightDxa,
    firstLineDxa = over.firstLineDxa ?: base.firstLineDxa,
    hangingDxa = over.hangingDxa ?: base.hangingDxa,
    beforePt = over.beforePt ?: base.beforePt,
    afterPt = over.afterPt ?: base.afterPt,
    lineRule = over.lineRule ?: base.lineRule,
    lineVal = over.lineVal ?: base.lineVal,
    shading = over.shading ?: base.shading,
    border = over.border ?: base.border,
    styleId = over.styleId ?: base.styleId,
    pageBreakBefore = over.pageBreakBefore ?: base.pageBreakBefore
)

/** Read w:pBdr / w:tcBorders into a CSS border shorthand (top/right/bottom/left). */
private fun readBorders(bordersEl: Element): String? {
    val sides = HashMap<String, String>()
    for (side in childElements(bordersEl)) {
        val name = localName(side)
        if (name !in setOf("top", "left", "bottom", "right")) continue
        val v = attr(side, "val") ?: "single"
        if (v == "nil" || v == "none") continue
        val style = when (v) {
            "double", "doubleWave" -> "double"
            "dashed", "dashDot", "dashDotStroked", "dashSmallGap" -> "dashed"
            "dotted" -> "dotted"
            else -> "solid"
        }
        val px = String.format(Locale.US, "%.1f", (attr(side, "sz")?.toFloatOrNull() ?: 4f) / 8f * 1.333f)
        val color = cleanColor(attr(side, "color")) ?: "#000000"
        sides[name] = "$px" + "px $style $color"
    }
    if (sides.isEmpty()) return null
    // Single shorthand only valid if all four equal; else emit per-side below.
    return if (sides.size == 4 && sides.values.toSet().size == 1) {
        "border:${sides["top"]};"
    } else {
        sides.entries.joinToString("") { (k, v) -> "border-$k:$v;" }
    }
}

// ------------------------------------------------------------------
// CSS builders.
// ------------------------------------------------------------------

private fun RunStyle.toCss(): String {
    val sb = StringBuilder()
    if (bold == true) sb.append("font-weight:bold;")
    if (italic == true) sb.append("font-style:italic;")
    val deco = ArrayList<String>()
    if (underline == true) deco.add("underline")
    if (strike == true) deco.add("line-through")
    if (deco.isNotEmpty()) sb.append("text-decoration:").append(deco.joinToString(" ")).append(";")
    color?.let { sb.append("color:").append(it).append(";") }
    highlight?.let { sb.append("background-color:").append(it).append(";") }
    shading?.let { sb.append("background-color:").append(it).append(";") }
    sizePt?.let { sb.append("font-size:").append(String.format(Locale.US, "%.1f", it)).append("pt;") }
    font?.let { sb.append("font-family:").append(it).append(";") }
    if (caps == true) sb.append("text-transform:uppercase;")
    if (smallCaps == true) sb.append("font-variant:small-caps;")
    if (vertAlign == "superscript") sb.append("vertical-align:super;font-size:smaller;")
    if (vertAlign == "subscript") sb.append("vertical-align:sub;font-size:smaller;")
    letterSpacingPt?.let {
        sb.append("letter-spacing:").append(String.format(Locale.US, "%.2f", ptToPx(it))).append("px;")
    }
    return sb.toString()
}

private fun ParaStyle.toCss(): String {
    val sb = StringBuilder()
    align?.let { sb.append("text-align:").append(it).append(";") }
    leftDxa?.let {
        val px = dxaToPx(it)
        sb.append("margin-left:min(").append(px.px1()).append("px, 30%);")
    }
    rightDxa?.let {
        val px = dxaToPx(it)
        sb.append("margin-right:min(").append(px.px1()).append("px, 30%);")
    }
    firstLineDxa?.let {
        val px = dxaToPx(it)
        sb.append("text-indent:min(").append(px.px1()).append("px, 48px);")
    }
    hangingDxa?.let {
        val px = dxaToPx(it)
        sb.append("padding-left:min(").append(px.px1()).append("px, 30%);")
        sb.append("text-indent:-min(").append(px.px1()).append("px, 30%);")
    }
    beforePt?.let { sb.append("margin-top:").append(ptToPx(it).px1()).append("px;") }
    afterPt?.let { sb.append("margin-bottom:").append(ptToPx(it).px1()).append("px;") }
    if (lineRule != null && lineVal != null) {
        when (lineRule) {
            "exact", "atLeast" -> sb.append("line-height:").append(ptToPx(lineVal / 20f).px1()).append("px;")
            else -> sb.append("line-height:").append(String.format(Locale.US, "%.2f", lineVal / 240f)).append(";")
        }
    }
    shading?.let { sb.append("background-color:").append(it).append(";") }
    border?.let { sb.append(it) }
    return sb.toString()
}

