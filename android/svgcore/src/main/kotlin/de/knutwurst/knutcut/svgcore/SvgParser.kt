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

    private data class ViewBox(val minX: Double, val minY: Double, val w: Double, val h: Double)

    fun parse(svg: String, toleranceMm: Double = PathFlattener.DEFAULT_TOLERANCE_MM): List<Polyline> {
        val root = buildDoc(svg).documentElement ?: return emptyList()
        val out = ArrayList<Polyline>()
        walk(root, rootUnitMatrix(root), out, toleranceMm)
        return out
    }

    private fun buildDoc(svg: String): Document {
        val dbf = DocumentBuilderFactory.newInstance()
        dbf.isNamespaceAware = true
        runCatching { dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) }
        runCatching { dbf.setFeature("http://xml.org/sax/features/external-general-entities", false) }
        runCatching { dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
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

    private fun walk(el: Element, parent: Matrix, out: MutableList<Polyline>, tol: Double) {
        val m = parent * Matrix.parse(el.getAttribute("transform"))
        val subs = shapeToSubPaths(el)
        if (subs != null) {
            for (sub in subs) out.add(flatten(sub, m, tol))
            return
        }
        var child = el.firstChild
        while (child != null) {
            if (child.nodeType == Node.ELEMENT_NODE) walk(child as Element, m, out, tol)
            child = child.nextSibling
        }
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
        val nums = points.trim().split(Regex("[\\s,]+")).filter { it.isNotEmpty() }.map { it.toDouble() }
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
