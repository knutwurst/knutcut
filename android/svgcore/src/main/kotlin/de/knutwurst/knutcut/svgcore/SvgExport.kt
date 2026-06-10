package de.knutwurst.knutcut.svgcore

import java.util.Locale

/**
 * Writes placed geometry (millimetres, y-down) to a plain SVG of stroked outlines — the paths the
 * plotter would cut or draw. Coordinates are emitted in millimetres (width/height in mm with a 1:1
 * viewBox), so the file opens at the real size in other tools and re-imports through [SvgParser]
 * without scaling. Closed polylines get a trailing `Z`; every path is stroke-only (no fill), which
 * keeps multi-contour shapes (holes/counters) readable and avoids fill-rule surprises.
 */
object SvgExport {

    /** One outline to write, with an optional packed-ARGB colour (null → black). */
    data class Stroke(val polyline: Polyline, val colorArgb: Int? = null)

    private const val STROKE_WIDTH_MM = 0.5

    fun toSvg(strokes: List<Stroke>): String {
        val pts = strokes.flatMap { it.polyline.points }
        val b = Bounds.ofOrNull(pts)
        val minX = b?.minX ?: 0.0
        val minY = b?.minY ?: 0.0
        val w = (b?.let { it.maxX - it.minX } ?: 0.0).coerceAtLeast(0.001)
        val h = (b?.let { it.maxY - it.minY } ?: 0.0).coerceAtLeast(0.001)

        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<svg xmlns=\"http://www.w3.org/2000/svg\" ")
        sb.append("width=\"").append(f(w)).append("mm\" height=\"").append(f(h)).append("mm\" ")
        sb.append("viewBox=\"").append(f(minX)).append(' ').append(f(minY)).append(' ')
            .append(f(w)).append(' ').append(f(h)).append("\">\n")
        for (s in strokes) {
            if (s.polyline.points.size < 2) continue
            val color = s.colorArgb?.let { String.format(Locale.US, "#%06X", it and 0xFFFFFF) } ?: "#000000"
            sb.append("  <path d=\"").append(pathData(s.polyline)).append("\" ")
                .append("fill=\"none\" stroke=\"").append(color).append("\" ")
                .append("stroke-width=\"").append(f(STROKE_WIDTH_MM)).append("\"/>\n")
        }
        sb.append("</svg>\n")
        return sb.toString()
    }

    private fun pathData(poly: Polyline): String {
        val sb = StringBuilder()
        poly.points.forEachIndexed { i, p ->
            sb.append(if (i == 0) "M" else " L").append(f(p.xMm)).append(',').append(f(p.yMm))
        }
        if (poly.closed) sb.append(" Z")
        return sb.toString()
    }

    // Locale.US so the decimal separator is always '.', never ',' (the device runs a German locale).
    private fun f(v: Double): String = String.format(Locale.US, "%.3f", v)
}
