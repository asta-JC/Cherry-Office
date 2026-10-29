package com.asta.cherryoffice.formats

import org.w3c.dom.Element
import java.util.Base64

/** Static DrawingML preview. No document scripts, remote resources or executable HTML are used. */
internal class SlideGraphics(private val entries: Map<String, ByteArray>, presentation: org.w3c.dom.Document) {
    private val size = OfficeArchive.elements(presentation, "sldSz").firstOrNull()
    private val width = size?.getAttribute("cx")?.toDoubleOrNull()?.div(9525) ?: 960.0
    private val height = size?.getAttribute("cy")?.toDoubleOrNull()?.div(9525) ?: 540.0
    private val theme = entries.keys.firstOrNull { it.startsWith("ppt/theme/") && it.endsWith(".xml") }
        ?.let { OfficeArchive.xml(entries.getValue(it)) }
    private val colors = mutableMapOf("tx1" to "202020", "bg1" to "FFFFFF", "tx2" to "44546A", "bg2" to "E7E6E6")
    private val media = mutableMapOf<String, String>()
    init {
        theme?.let { doc -> OfficeArchive.elements(doc, "clrScheme").firstOrNull()?.children()?.forEach { c ->
            colors[c.localName] = c.children().firstOrNull()?.let { if (it.localName == "sysClr") it.getAttribute("lastClr") else it.getAttribute("val") } ?: "202020"
        } }
        colors["tx1"] = colors["dk1"] ?: "202020"; colors["bg1"] = colors["lt1"] ?: "FFFFFF"
        colors["tx2"] = colors["dk2"] ?: "44546A"; colors["bg2"] = colors["lt2"] ?: "E7E6E6"
    }
    fun render(path: String): Pair<String, List<String>> {
        val slide = OfficeArchive.xml(entries.getValue(path)).documentElement
        val warnings = linkedSetOf<String>()
        val layoutPath = relationships(path).values.firstOrNull { it.contains("/slideLayouts/") }
        val layout = layoutPath?.let { entries[it]?.let(OfficeArchive::xml)?.documentElement }
        val masterPath = layoutPath?.let { relationships(it).values.firstOrNull { p -> p.contains("/slideMasters/") } }
        val master = masterPath?.let { entries[it]?.let(OfficeArchive::xml)?.documentElement }
        val background = listOfNotNull(slide, layout, master).firstNotNullOfOrNull { root ->
            root.child("cSld")?.child("bg")?.let { bg -> bg.child("bgPr")?.child("solidFill")?.let(::color) }
        } ?: "#FFFFFF"
        val svg = buildString {
            append("<svg xmlns=\"http://www.w3.org/2000/svg\" xmlns:xlink=\"http://www.w3.org/1999/xlink\" viewBox=\"0 0 $width $height\" role=\"img\">")
            append("<defs><marker id=\"arrow\" markerWidth=\"8\" markerHeight=\"8\" refX=\"7\" refY=\"3\" orient=\"auto-start-reverse\" markerUnits=\"strokeWidth\"><path d=\"M0,0 L7,3 L0,6 Z\" fill=\"context-stroke\"/></marker></defs>")
            append("<rect width=\"$width\" height=\"$height\" fill=\"$background\"/>")
            if (slide.getAttribute("showMasterSp") != "0") {
                if (master != null) append(tree(master, masterPath, null, warnings, true))
                if (layout != null) append(tree(layout, layoutPath, master, warnings, true))
            }
            append(tree(slide, path, layout, warnings, false))
            append("</svg>")
        }
        if (OfficeArchive.elements(slide, "timing").isNotEmpty()) warnings.add("Animations are shown as a static slide.")
        return svg to warnings.toList()
    }
    private fun relationships(path: String): Map<String, String> {
        val dir = path.substringBeforeLast('/')
        return entries["$dir/_rels/${path.substringAfterLast('/')}.rels"]?.let { bytes ->
            OfficeArchive.elements(OfficeArchive.xml(bytes), "Relationship").filter { it.getAttribute("TargetMode") != "External" }
                .associate { it.getAttribute("Id") to OfficeArchive.resolve(dir, it.getAttribute("Target")) }
        }.orEmpty()
    }
    private fun tree(root: Element, path: String, inherited: Element?, warnings: MutableSet<String>, decoration: Boolean): String {
        val rels = relationships(path)
        fun shape(e: Element): String {
            if (decoration && OfficeArchive.elements(e, "ph").isNotEmpty()) return ""
            if (e.localName == "grpSp") {
                val t = e.child("grpSpPr")?.child("xfrm")
                val off = t?.child("off"); val ext = t?.child("ext"); val co = t?.child("chOff"); val ce = t?.child("chExt")
                val sx = (ext?.number("cx") ?: 1.0) / (ce?.number("cx") ?: 1.0).coerceAtLeast(1.0)
                val sy = (ext?.number("cy") ?: 1.0) / (ce?.number("cy") ?: 1.0).coerceAtLeast(1.0)
                return "<g transform=\"translate(${off?.emu("x") ?: 0.0} ${off?.emu("y") ?: 0.0}) scale($sx $sy) translate(${-(co?.emu("x") ?: 0.0)} ${-(co?.emu("y") ?: 0.0)})\">${e.children().joinToString("") { shape(it) }}</g>"
            }
            if (e.localName !in listOf("sp", "pic", "cxnSp", "graphicFrame")) return ""
            val ph = OfficeArchive.elements(e, "ph").firstOrNull()
            val inheritedShape = inherited?.let { r -> OfficeArchive.elements(r, "sp").firstOrNull { s ->
                OfficeArchive.elements(s, "ph").firstOrNull()?.let { p -> p.getAttribute("idx").ifBlank { "0" } == ph?.getAttribute("idx")?.ifBlank { "0" } } == true
            } }
            val props = e.child("spPr")
            val t = props?.child("xfrm") ?: e.child("xfrm") ?: inheritedShape?.child("spPr")?.child("xfrm")
            val x = t?.child("off")?.emu("x") ?: 0.0; val y = t?.child("off")?.emu("y") ?: 0.0
            val w = t?.child("ext")?.emu("cx") ?: width; val h = t?.child("ext")?.emu("cy") ?: 80.0
            val rotation = (t?.number("rot") ?: 0.0) / 60000
            val transform = "translate($x $y) rotate($rotation ${w / 2} ${h / 2}) translate(${if (t?.getAttribute("flipH") == "1") w else 0.0} ${if (t?.getAttribute("flipV") == "1") h else 0.0}) scale(${if (t?.getAttribute("flipH") == "1") -1 else 1} ${if (t?.getAttribute("flipV") == "1") -1 else 1})"
            val body = when (e.localName) {
                "pic" -> {
                    val blip = OfficeArchive.elements(e, "blip").firstOrNull()
                    val id = blip?.getAttributeNS("http://schemas.openxmlformats.org/officeDocument/2006/relationships", "embed")
                    val target = rels[id]
                    val image = target?.let { p -> media.getOrPut(p) {
                        val mime = when (p.substringAfterLast('.').lowercase()) { "png" -> "image/png"; "jpg", "jpeg" -> "image/jpeg"; "gif" -> "image/gif"; "svg" -> ""; "webp" -> "image/webp"; else -> "" }
                        if (mime.isBlank()) "" else entries[p]?.let { "data:$mime;base64,${Base64.getEncoder().encodeToString(it)}" }.orEmpty()
                    } }.orEmpty()
                    if (image.isEmpty()) { warnings.add("Some picture formats cannot be displayed."); "" }
                    else {
                        val crop = e.child("blipFill")?.child("srcRect")
                        val l = (crop?.number("l") ?: 0.0) / 100000; val top = (crop?.number("t") ?: 0.0) / 100000
                        val iw = w / (1 - l - (crop?.number("r") ?: 0.0) / 100000).coerceAtLeast(.01)
                        val ih = h / (1 - top - (crop?.number("b") ?: 0.0) / 100000).coerceAtLeast(.01)
                        "<svg width=\"$w\" height=\"$h\" overflow=\"hidden\"><image x=\"${-iw*l}\" y=\"${-ih*top}\" width=\"$iw\" height=\"$ih\" preserveAspectRatio=\"none\" href=\"$image\"/></svg>"
                    }
                }
                "graphicFrame" -> {
                    val chart = OfficeArchive.elements(e, "chart").firstOrNull()
                    val target = chart?.getAttributeNS("http://schemas.openxmlformats.org/officeDocument/2006/relationships", "id")?.let(rels::get)
                    when {
                        target != null -> chart(target, w, h, warnings)
                        OfficeArchive.elements(e, "tbl").isNotEmpty() -> table(OfficeArchive.elements(e, "tbl").first(), w, h)
                        else -> { warnings.add("Some diagrams need an external office app."); "" }
                    }
                }
                else -> {
                    val fill = props?.child("solidFill")?.let(::color) ?: if (props?.child("noFill") != null) "none" else e.child("style")?.child("fillRef")?.takeUnless { it.getAttribute("idx") == "0" }?.let(::color) ?: "none"
                    val line = props?.child("ln")
                    val stroke = if (line?.child("noFill") != null) "none" else line?.child("solidFill")?.let(::color) ?: if (e.localName == "cxnSp") "#333333" else "none"
                    val arrows = (if (line?.child("headEnd")?.getAttribute("type").orEmpty() !in listOf("", "none")) " marker-start=\"url(#arrow)\"" else "") +
                        (if (line?.child("tailEnd")?.getAttribute("type").orEmpty() !in listOf("", "none")) " marker-end=\"url(#arrow)\"" else "")
                    val style = "fill=\"$fill\" stroke=\"$stroke\" stroke-width=\"${line?.emu("w") ?: 1.0}\"$arrows"
                    val geometry = props?.child("prstGeom")?.getAttribute("prst")
                    val custom = props?.child("custGeom")
                    val outline = if (custom != null) customPaths(custom, w, h, style, warnings) else when (geometry) {
                        "ellipse" -> "<ellipse cx=\"${w/2}\" cy=\"${h/2}\" rx=\"${w/2}\" ry=\"${h/2}\" $style/>"
                        "line" -> "<line x1=\"0\" y1=\"0\" x2=\"$w\" y2=\"$h\" $style/>"
                        "diamond" -> "<polygon points=\"${w/2},0 $w,${h/2} ${w/2},$h 0,${h/2}\" $style/>"
                        "rightArrow" -> "<polygon points=\"0,${h*.25} ${w*.65},${h*.25} ${w*.65},0 $w,${h/2} ${w*.65},$h ${w*.65},${h*.75} 0,${h*.75}\" $style/>"
                        "triangle" -> "<polygon points=\"${w/2},0 $w,$h 0,$h\" $style/>"
                        else -> if (e.localName == "cxnSp") "<line x1=\"0\" y1=\"0\" x2=\"$w\" y2=\"$h\" $style/>" else "<rect width=\"$w\" height=\"$h\" rx=\"${if (geometry == "roundRect") 12 else 0}\" $style/>"
                    }
                    if (custom == null && geometry !in listOf(null, "rect", "roundRect", "ellipse", "line", "triangle", "diamond", "rightArrow")) warnings.add("Some custom shapes are approximated.")
                    outline + (e.child("txBody")?.let { text(it, w, h, inheritedShape?.child("txBody")) } ?: "")
                }
            }
            return "<g transform=\"$transform\">$body</g>"
        }
        return root.child("cSld")?.child("spTree")?.children()?.joinToString("") { shape(it) }.orEmpty()
    }
    private fun customPaths(geometry: Element, w: Double, h: Double, style: String, warnings: MutableSet<String>): String {
        return geometry.child("pathLst")?.children().orEmpty().joinToString("") { path ->
            val pw = path.number("w").takeIf { it > 0 } ?: w
            val ph = path.number("h").takeIf { it > 0 } ?: h
            val commands = path.children().joinToString(" ") { command ->
                val points = command.children().map { p ->
                    val px = p.getAttribute("x").toDoubleOrNull(); val py = p.getAttribute("y").toDoubleOrNull()
                    if (px == null || py == null) warnings.add("Some custom shape formulas are not supported.")
                    "${(px ?: 0.0)*w/pw} ${(py ?: 0.0)*h/ph}"
                }
                when (command.localName) {
                    "moveTo" -> "M ${points.joinToString(" ")}"
                    "lnTo" -> "L ${points.joinToString(" ")}"
                    "cubicBezTo" -> "C ${points.joinToString(" ")}"
                    "quadBezTo" -> "Q ${points.joinToString(" ")}"
                    "close" -> "Z"
                    else -> { warnings.add("Some custom curve segments are not supported."); "" }
                }
            }
            val pathStyle = if (path.getAttribute("fill") == "none") style.replace(Regex("fill=\"[^\"]*\""), "fill=\"none\"") else style
            "<path d=\"$commands\" $pathStyle/>"
        }
    }
    private fun text(body: Element, w: Double, h: Double, inherited: Element? = null): String {
        val bp = body.child("bodyPr")
        val inheritedBp = inherited?.child("bodyPr")
        fun inset(name: String, fallback: Double) = (bp?.takeIf { it.hasAttribute(name) }
            ?: inheritedBp?.takeIf { it.hasAttribute(name) })?.emu(name) ?: fallback
        val left = inset("lIns", 9.6); val right = inset("rIns", 9.6)
        val top = inset("tIns", 4.8); val bottom = inset("bIns", 4.8)
        val anchor = bp?.getAttribute("anchor")?.ifBlank { null } ?: inheritedBp?.getAttribute("anchor")
        val alignment = when (anchor) { "ctr" -> "center"; "b" -> "flex-end"; else -> "flex-start" }
        val wrap = bp?.getAttribute("wrap") != "none"
        val fontScale = (bp?.child("normAutofit")?.getAttribute("fontScale")?.toDoubleOrNull()
            ?.div(100000.0) ?: 1.0).coerceIn(.01, 1.0)
        fun fontSize(properties: Element?) = properties?.getAttribute("sz")?.toDoubleOrNull()?.takeIf { it > 0 }?.div(75)
        // Keep paragraphs at their intrinsic height. The browser measures the complete text and
        // reduces all run sizes together when fallback fonts would otherwise clip the final line.
        return "<foreignObject width=\"$w\" height=\"$h\" overflow=\"visible\"><div xmlns=\"http://www.w3.org/1999/xhtml\" data-slide-text=\"true\" style=\"box-sizing:border-box;width:100%;height:100%;padding:${top}px ${right}px ${bottom}px ${left}px;display:flex;flex-direction:column;justify-content:$alignment;overflow:visible;font-family:Arial,sans-serif\"><div data-slide-content=\"true\" style=\"flex:none;min-width:0;width:100%\">" + body.children().filter { it.localName == "p" }.joinToString("") { p ->
            val pp = p.child("pPr")
            val level = (pp?.getAttribute("lvl")?.toIntOrNull() ?: 0) + 1
            val defaults = pp?.child("defRPr") ?: body.child("lstStyle")?.child("lvl${level}pPr")?.child("defRPr")
                ?: inherited?.child("lstStyle")?.child("lvl${level}pPr")?.child("defRPr")
                ?: inherited?.let { OfficeArchive.elements(it,"defRPr").firstOrNull() }
            val paragraphSize = (fontSize(defaults) ?: 24.0) * fontScale
            val align = when (pp?.getAttribute("algn")) { "ctr" -> "center"; "r" -> "right"; "just" -> "justify"; else -> "left" }
            val lineSpacing = pp?.child("lnSpc")
            val lineHeight = lineSpacing?.child("spcPct")?.getAttribute("val")?.toDoubleOrNull()?.let { (it/100000).coerceAtLeast(1.0).toString() } ?: "normal"
            "<div data-fit-font=\"$paragraphSize\" style=\"margin:0;text-align:$align;white-space:${if (wrap) "pre-wrap" else "pre"};overflow-wrap:${if (wrap) "break-word" else "normal"};line-height:$lineHeight;font-size:${paragraphSize}px\">" +
                (if (pp?.child("buChar") != null) "• " else "") + p.children().joinToString("") { r ->
                    when (r.localName) {
                        "br" -> "<br/>"
                        "r", "fld" -> {
                            val rp = r.child("rPr")
                            val size = (fontSize(rp) ?: fontSize(defaults) ?: 24.0) * fontScale
                            val font = (rp?.child("latin") ?: defaults?.child("latin"))?.getAttribute("typeface")?.takeUnless { it.startsWith('+') } ?: "Arial"
                            fun attr(name: String) = rp?.getAttribute(name)?.ifBlank { null } ?: defaults?.getAttribute(name)
                            val color = (rp?.child("solidFill") ?: defaults?.child("solidFill"))?.let(::color) ?: "#${colors["tx1"]}"
                            "<span data-fit-font=\"$size\" style=\"font-size:${size}px;color:$color;font-family:${escape(font)};font-weight:${if (attr("b") == "1") "bold" else "normal"};font-style:${if (attr("i") == "1") "italic" else "normal"};text-decoration:${if (attr("u").orEmpty() !in listOf("", "none")) "underline" else "none"}\">${escape(r.child("t")?.textContent.orEmpty())}</span>"
                        }
                        else -> ""
                    }
                }.ifEmpty { "<br/>" } + "</div>"
        } + "</div></div></foreignObject>"
    }
    private fun table(tbl: Element, w: Double, h: Double): String {
        val rows = tbl.children().filter { it.localName == "tr" }
        var y = 0.0
        val cols = tbl.child("tblGrid")?.children().orEmpty().map { it.emu("w") }
        val total = cols.sum().coerceAtLeast(1.0)
        return rows.joinToString("") { row ->
            val rh = row.emu("h").takeIf { it > 0 } ?: h/rows.size
            var x = 0.0
            val result = row.children().filter { it.localName == "tc" }.mapIndexed { i, cell ->
                val cw = (cols.getOrNull(i) ?: total/row.children().size) / total*w
                val fill = cell.child("tcPr")?.child("solidFill")?.let(::color) ?: "#FFFFFF"
                val content = "<g transform=\"translate($x $y)\"><rect width=\"$cw\" height=\"$rh\" fill=\"$fill\" stroke=\"#B8B8B8\"/>${cell.child("txBody")?.let { text(it,cw,rh) }.orEmpty()}</g>"
                x += cw; content
            }.joinToString("")
            y += rh; result
        }
    }
    private fun chart(path: String, w: Double, h: Double, warnings: MutableSet<String>): String {
        val doc = entries[path]?.let(OfficeArchive::xml) ?: return ""
        val plot = OfficeArchive.elements(doc,"plotArea").firstOrNull() ?: return ""
        val kind = plot.children().firstOrNull { it.localName.endsWith("Chart") }?.localName
        if (kind !in listOf("barChart", "lineChart", "pieChart", "doughnutChart")) {
            warnings.add("Some chart types need an external office app."); return ""
        }
        val series = OfficeArchive.elements(plot,"ser")
        val values = series.map { s -> OfficeArchive.elements(s.child("val") ?: s.child("yVal") ?: s,"numCache").firstOrNull()?.let { cache ->
            OfficeArchive.elements(cache,"pt").map { pt -> pt.child("v")?.textContent?.toDoubleOrNull() ?: 0.0 }
        }.orEmpty() }
        val n = values.maxOfOrNull { it.size } ?: 0
        if (n == 0) { warnings.add("A chart has no saved values to display."); return "" }
        val palette = listOf("#4472C4","#ED7D31","#A5A5A5","#FFC000","#5B9BD5","#70AD47")
        val max = values.flatten().maxOrNull()?.coerceAtLeast(1.0) ?: 1.0
        val min = values.flatten().minOrNull()?.coerceAtMost(0.0) ?: 0.0
        fun py(v: Double) = h-35-(v-min)/(max-min)*(h-65).coerceAtLeast(1.0)
        val pw = (w-60).coerceAtLeast(1.0)
        warnings.add("Charts use saved values; advanced chart styling may differ.")
        return buildString {
            append("<rect width=\"$w\" height=\"$h\" fill=\"white\"/>")
            val title = OfficeArchive.elements(doc,"title").firstOrNull()?.let { OfficeArchive.elements(it,"t").joinToString("") { t -> t.textContent } }.orEmpty()
            append("<text x=\"${w/2}\" y=\"18\" text-anchor=\"middle\" font-family=\"Arial\" font-size=\"14\">${escape(title)}</text>")
            if (kind == "pieChart" || kind == "doughnutChart") {
                val data = values.first(); val sum = data.sum().coerceAtLeast(1.0); var angle = -Math.PI/2
                val r = minOf(w,h)*.35; val cx = w/2; val cy = h/2
                data.forEachIndexed { i,v ->
                    val end = angle+v.coerceAtLeast(0.0)/sum*2*Math.PI
                    if (v/sum >= .99999) append("<circle cx=\"$cx\" cy=\"$cy\" r=\"$r\" fill=\"${palette[i%palette.size]}\"/>")
                    else append("<path d=\"M $cx $cy L ${cx+r*kotlin.math.cos(angle)} ${cy+r*kotlin.math.sin(angle)} A $r $r 0 ${if(end-angle>Math.PI) 1 else 0} 1 ${cx+r*kotlin.math.cos(end)} ${cy+r*kotlin.math.sin(end)} Z\" fill=\"${palette[i%palette.size]}\"/>")
                    angle=end
                }
                if (kind=="doughnutChart") append("<circle cx=\"$cx\" cy=\"$cy\" r=\"${r*.5}\" fill=\"white\"/>")
            } else {
                append("<path d=\"M 40 28 V ${h-35} H ${w-15}\" fill=\"none\" stroke=\"#999\"/>")
                values.forEachIndexed { s, data ->
                    val c = series.getOrNull(s)?.child("spPr")?.child("solidFill")?.let(::color) ?: palette[s%palette.size]
                    if (kind == "barChart") data.forEachIndexed { i,v ->
                        val bw = pw/n/(values.size+1)
                        append("<rect x=\"${40+i*pw/n+s*bw}\" y=\"${minOf(py(v),py(0.0))}\" width=\"$bw\" height=\"${kotlin.math.abs(py(0.0)-py(v))}\" fill=\"$c\"/>")
                    } else append("<polyline points=\"${data.mapIndexed { i,v -> "${40+(i+.5)*pw/n},${py(v)}" }.joinToString(" ")}\" fill=\"none\" stroke=\"$c\" stroke-width=\"2.5\"/>")
                }
                val categories = series.firstOrNull()?.child("cat")?.let { OfficeArchive.elements(it,"pt").map { p -> p.child("v")?.textContent.orEmpty() } }.orEmpty()
                categories.take(n).forEachIndexed { i, label -> append("<text x=\"${40+(i+.5)*pw/n}\" y=\"${h-15}\" text-anchor=\"middle\" font-size=\"10\" font-family=\"Arial\">${escape(label)}</text>") }
            }
        }
    }
    private fun color(e: Element): String {
        val c = e.children().firstOrNull() ?: return "#202020"
        val value = when(c.localName) { "srgbClr" -> c.getAttribute("val"); "schemeClr" -> colors[c.getAttribute("val")] ?: "202020"; "sysClr" -> c.getAttribute("lastClr"); else -> "202020" }
        return "#${value.takeIf { it.matches(Regex("[0-9a-fA-F]{6}")) } ?: "202020"}"
    }
    private fun Element.number(name: String) = getAttribute(name).toDoubleOrNull() ?: 0.0
    private fun Element.emu(name: String) = number(name)/9525
    private fun Element.child(name: String) = children().firstOrNull { it.localName == name }
    private fun Element.children(): List<Element> = (0 until childNodes.length).mapNotNull { childNodes.item(it) as? Element }
    private fun escape(s: String) = s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&#39;")
}
