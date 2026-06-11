package de.knutwurst.knutcut.svgcore

import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Parses an SVG document into polylines in millimetres, with all transforms applied and curves
 * flattened. Targets the flattened-mm SVGs Cricut Export produces, but handles the common shape
 * elements and units generally.
 */
object SvgParser {
    private const val MM_PER_INCH = 25.4
    private const val DEFAULT_DPI = 96.0
    private const val MAX_SVG_CHARS = 20_000_000

    private data class ViewBox(val minX: Double, val minY: Double, val w: Double, val h: Double)

    /** Parsed shapes plus a count of elements that were skipped because they couldn't be read. */
    data class Result(val shapes: List<SvgShape>, val skipped: Int)

    /** Flat list of all polylines in the document (every shape merged). */
    fun parse(svg: String, toleranceMm: Double = PathFlattener.DEFAULT_TOLERANCE_MM): List<Polyline> =
        parseShapes(svg, toleranceMm).flatMap { it.polylines }

    /** One [SvgShape] per drawable element (path/rect/…), in document order. */
    fun parseShapes(svg: String, toleranceMm: Double = PathFlattener.DEFAULT_TOLERANCE_MM): List<SvgShape> =
        parseShapesResult(svg, toleranceMm).shapes

    /** Like [parseShapes] but also reports how many malformed elements were skipped. */
    fun parseShapesResult(svg: String, toleranceMm: Double = PathFlattener.DEFAULT_TOLERANCE_MM): Result {
        val root = buildDoc(svg).documentElement ?: return Result(emptyList(), 0)
        val shapes = ArrayList<SvgShape>()
        val skipped = intArrayOf(0)
        walk(root, rootUnitMatrix(root), shapes, toleranceMm, intArrayOf(0), skipped, null, hidden = false, inheritedFill = null, inheritedStroke = null)
        return Result(shapes, skipped[0])
    }

    private fun buildDoc(svg: String): Document {
        require(svg.length <= MAX_SVG_CHARS) { "SVG too large" }
        val dbf = DocumentBuilderFactory.newInstance()
        dbf.isNamespaceAware = true
        // XML hardening: no DOCTYPE/DTD, no external or expanded entities, no XInclude.
        runCatching { dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        runCatching { dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) }
        runCatching { dbf.setFeature("http://xml.org/sax/features/external-general-entities", false) }
        runCatching { dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        runCatching { dbf.isXIncludeAware = false }
        runCatching { dbf.isExpandEntityReferences = false }
        val db = dbf.newDocumentBuilder()
        db.setEntityResolver { _, _ -> InputSource(StringReader("")) }
        return db.parse(InputSource(StringReader(svg)))
    }

    /** Matrix mapping the document's user units to millimetres (from viewBox + width/height). */
    private fun rootUnitMatrix(root: Element): Matrix {
        val vb = parseViewBox(root.getAttribute("viewBox"))
        val wMm = lengthToMm(root.getAttribute("width"))
        val hMm = lengthToMm(root.getAttribute("height"))
        if (vb != null && vb.w > 0 && vb.h > 0) {
            val sx = (wMm ?: (vb.w * MM_PER_INCH / DEFAULT_DPI)) / vb.w
            val sy = (hMm ?: (vb.h * MM_PER_INCH / DEFAULT_DPI)) / vb.h
            return Matrix.scale(sx, sy) * Matrix.translate(-vb.minX, -vb.minY)
        }
        val s = MM_PER_INCH / DEFAULT_DPI
        return Matrix.scale(s, s)
    }

    private fun walk(el: Element, parent: Matrix, shapes: MutableList<SvgShape>, tol: Double, count: IntArray, skipped: IntArray, inheritedColor: Int?, hidden: Boolean, inheritedFill: String?, inheritedStroke: String?) {
        // Hidden subtrees (construction/guide layers) must not become cut paths. display:none and
        // opacity:0 remove the element and everything under it. visibility is inherited but a
        // descendant can switch it back on, so it's tracked and re-evaluated per element.
        if (cssOrAttr(el, "display").equals("none", ignoreCase = true)) return
        val opacity = cssOrAttr(el, "opacity")?.toDoubleOrNull()
        if (opacity != null && opacity <= 0.0) return
        val effHidden = when (cssOrAttr(el, "visibility")?.lowercase()) {
            "visible" -> false
            "hidden", "collapse" -> true
            else -> hidden
        }
        // fill/stroke are inherited. An element paints only if its effective fill or stroke isn't
        // "none"; fill defaults to black (visible), stroke defaults to none. An element with both
        // none is invisible (e.g. fill="none" stroke="none") and must not become a cut path — but
        // fill="none" stroke="#…" (a real outline) is kept.
        val effFill = cssOrAttr(el, "fill")?.lowercase() ?: inheritedFill
        val effStroke = cssOrAttr(el, "stroke")?.lowercase() ?: inheritedStroke

        // A malformed transform/geometry on one element must not abort the whole import — skip it and
        // count it. A bad transform falls back to the parent matrix so the element can still be drawn.
        val m = runCatching { parent * Matrix.parse(el.getAttribute("transform")) }.getOrElse { skipped[0]++; parent }
        val color = elementColor(el, inheritedColor)
        val subs = runCatching { shapeToSubPaths(el) }.getOrElse { skipped[0]++; null }
        if (subs != null) {
            if (effHidden) return   // a hidden shape is not cut and not counted
            val fillVisible = (effFill ?: "black") != "none"   // unset fill defaults to black
            val strokeVisible = (effStroke ?: "none") != "none" // unset stroke defaults to none
            if (!fillVisible && !strokeVisible) return          // nothing painted → not a cut path
            val polys = runCatching { subs.map { flatten(it, m, tol) }.filter { it.points.size >= 2 } }
                .getOrElse { skipped[0]++; emptyList() }
            if (polys.isNotEmpty()) {
                count[0]++
                val id = el.getAttribute("id")
                shapes.add(SvgShape(if (id.isNotBlank()) id else "Ebene ${count[0]}", polys, color))
            }
            return
        }
        var child = el.firstChild
        while (child != null) {
            if (child.nodeType == Node.ELEMENT_NODE) walk(child as Element, m, shapes, tol, count, skipped, color, effHidden, effFill, effStroke)
            child = child.nextSibling
        }
    }

    /** A CSS property from the `style` attribute, else the presentation attribute; null if neither. */
    private fun cssOrAttr(el: Element, name: String): String? =
        (styleProp(el.getAttribute("style"), name) ?: el.getAttribute(name))?.trim()?.takeIf { it.isNotBlank() }

    /** The element's drawing colour as ARGB: its own fill (preferred) or stroke, read from the
     *  `style` attribute first then presentation attributes, falling back to the inherited group colour.
     *  "none"/unparseable on one property falls through to the next. */
    private fun elementColor(el: Element, inherited: Int?): Int? {
        val style = el.getAttribute("style")
        val fill = styleProp(style, "fill") ?: el.getAttribute("fill")
        SvgColor.parse(fill)?.let { return it }
        val stroke = styleProp(style, "stroke") ?: el.getAttribute("stroke")
        SvgColor.parse(stroke)?.let { return it }
        return inherited
    }

    /** Reads one `prop: value` declaration from a CSS `style` attribute, or null if absent. */
    private fun styleProp(style: String?, prop: String): String? {
        if (style.isNullOrBlank()) return null
        for (decl in style.split(";")) {
            val i = decl.indexOf(':')
            if (i > 0 && decl.substring(0, i).trim().equals(prop, ignoreCase = true)) return decl.substring(i + 1).trim()
        }
        return null
    }

    private fun shapeToSubPaths(el: Element): List<SubPath>? = when (localName(el)) {
        "path" -> SvgPath.parse(el.getAttribute("d"))
        "rect" -> {
            val x = num(el, "x"); val y = num(el, "y")
            val w = num(el, "width"); val h = num(el, "height")
            if (w <= 0 || h <= 0) emptyList()
            else listOf(SubPath(Pt(x, y), listOf(Line(Pt(x + w, y)), Line(Pt(x + w, y + h)), Line(Pt(x, y + h)), Line(Pt(x, y))), closed = true))
        }
        "circle" -> ellipse(num(el, "cx"), num(el, "cy"), num(el, "r"), num(el, "r"))
        "ellipse" -> ellipse(num(el, "cx"), num(el, "cy"), num(el, "rx"), num(el, "ry"))
        "line" -> listOf(SubPath(Pt(num(el, "x1"), num(el, "y1")), listOf(Line(Pt(num(el, "x2"), num(el, "y2")))), closed = false))
        "polyline" -> pointsSubPath(el.getAttribute("points"), closed = false)
        "polygon" -> pointsSubPath(el.getAttribute("points"), closed = true)
        else -> null
    }

    private fun ellipse(cx: Double, cy: Double, rx: Double, ry: Double): List<SubPath> {
        if (rx <= 0 || ry <= 0) return emptyList()
        val k = 0.5522847498307936
        val start = Pt(cx + rx, cy)
        val segs = listOf(
            Cubic(Pt(cx + rx, cy + ry * k), Pt(cx + rx * k, cy + ry), Pt(cx, cy + ry)),
            Cubic(Pt(cx - rx * k, cy + ry), Pt(cx - rx, cy + ry * k), Pt(cx - rx, cy)),
            Cubic(Pt(cx - rx, cy - ry * k), Pt(cx - rx * k, cy - ry), Pt(cx, cy - ry)),
            Cubic(Pt(cx + rx * k, cy - ry), Pt(cx + rx, cy - ry * k), Pt(cx + rx, cy)),
        )
        return listOf(SubPath(start, segs, closed = true))
    }

    private fun pointsSubPath(points: String, closed: Boolean): List<SubPath> {
        val nums = points.trim().split(Regex("[\\s,]+")).filter { it.isNotEmpty() }.mapNotNull { it.toDoubleOrNull() }
        if (nums.size < 4) return emptyList()
        val start = Pt(nums[0], nums[1])
        val segs = ArrayList<Seg>()
        var i = 2
        while (i + 1 < nums.size) { segs.add(Line(Pt(nums[i], nums[i + 1]))); i += 2 }
        return listOf(SubPath(start, segs, closed))
    }

    /** Apply [m] to every control point, then flatten beziers, producing a polyline in millimetres. */
    private fun flatten(sub: SubPath, m: Matrix, tol: Double): Polyline {
        val pts = ArrayList<Pt>()
        var cur = m.apply(sub.start)
        pts.add(cur)
        for (seg in sub.segs) when (seg) {
            is Line -> { cur = m.apply(seg.to); pts.add(cur) }
            is Cubic -> {
                val c1 = m.apply(seg.c1); val c2 = m.apply(seg.c2); val to = m.apply(seg.to)
                PathFlattener.cubic(cur, c1, c2, to, tol, pts)
                cur = to
            }
        }
        return Polyline(pts, sub.closed)
    }

    private fun parseViewBox(s: String?): ViewBox? {
        if (s.isNullOrBlank()) return null
        val n = s.trim().split(Regex("[\\s,]+")).filter { it.isNotEmpty() }.mapNotNull { it.toDoubleOrNull() }
        return if (n.size == 4) ViewBox(n[0], n[1], n[2], n[3]) else null
    }

    /** Convert an SVG length (with optional unit) to millimetres, or null if blank/unparseable. */
    private fun lengthToMm(s: String?): Double? {
        if (s.isNullOrBlank()) return null
        val m = Regex("^\\s*([+-]?[0-9.eE]+)\\s*([a-z%]*)\\s*$").find(s) ?: return null
        val v = m.groupValues[1].toDoubleOrNull() ?: return null
        return when (m.groupValues[2].lowercase()) {
            "mm" -> v
            "cm" -> v * 10.0
            "in" -> v * MM_PER_INCH
            "pt" -> v * MM_PER_INCH / 72.0
            "pc" -> v * MM_PER_INCH / 6.0
            "px", "" -> v * MM_PER_INCH / DEFAULT_DPI
            else -> null
        }
    }

    private fun num(el: Element, name: String): Double = el.getAttribute(name).trim().toDoubleOrNull() ?: 0.0

    private fun localName(el: Element): String = (el.localName ?: el.nodeName).substringAfterLast(':').lowercase()
}
