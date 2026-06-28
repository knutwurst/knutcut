package de.knutwurst.knutcut.svgcore

import kotlin.math.hypot

/**
 * Picks which contour of a multi-contour layer a tap refers to, so the node editor can switch the
 * contour being edited by tapping its outline. Pure geometry (no Android), shared with the editor.
 *
 * The rule is boundary proximity: the contour whose outline is nearest the tap wins. Interior taps
 * (far from every outline) are intentionally not matched here — the caller treats those as "no
 * switch" so tapping the empty inside of the active shape keeps its current meaning (deselect).
 */
object ContourPick {

    /** Index of the contour whose boundary is closest to [p], paired with that distance (mm), or
     *  null when [contours] is empty. Ignores contours with no points. */
    fun nearestBoundary(contours: List<Polyline>, p: Pt): Pair<Int, Double>? {
        var bestI = -1
        var bestD = Double.MAX_VALUE
        for ((i, c) in contours.withIndex()) {
            if (c.points.isEmpty()) continue
            val d = boundaryDist(p, c)
            if (d < bestD) { bestD = d; bestI = i }
        }
        return if (bestI < 0) null else bestI to bestD
    }

    /** Shortest distance (mm) from [p] to the contour's outline (its segments; the closing edge too
     *  when the contour is closed). A single-point contour returns the distance to that point. */
    fun boundaryDist(p: Pt, c: Polyline): Double {
        val pts = c.points
        if (pts.isEmpty()) return Double.MAX_VALUE
        if (pts.size == 1) return hypot(p.xMm - pts[0].xMm, p.yMm - pts[0].yMm)
        val n = pts.size
        val segs = if (c.closed) n else n - 1
        var best = Double.MAX_VALUE
        for (i in 0 until segs) {
            val a = pts[i]
            val b = pts[(i + 1) % n]
            val d = segDist(p, a, b)
            if (d < best) best = d
        }
        return best
    }

    /** Index of the largest-area contour (by absolute shoelace area), or -1 when [contours] is empty.
     *  Used to pick a sensible default contour to edit (usually the main outline). */
    fun largestByArea(contours: List<Polyline>): Int {
        var bestI = -1
        var bestA = -1.0
        for ((i, c) in contours.withIndex()) {
            val a = area(c)
            if (a > bestA) { bestA = a; bestI = i }
        }
        return bestI
    }

    /** Absolute polygon area (mm²) via the shoelace formula; 0 for degenerate contours. */
    fun area(c: Polyline): Double {
        val pts = c.points
        if (pts.size < 3) return 0.0
        var sum = 0.0
        var j = pts.size - 1
        for (i in pts.indices) {
            sum += (pts[j].xMm + pts[i].xMm) * (pts[j].yMm - pts[i].yMm)
            j = i
        }
        return kotlin.math.abs(sum) / 2.0
    }

    /** Distance from point [p] to the segment a–b. */
    private fun segDist(p: Pt, a: Pt, b: Pt): Double {
        val vx = b.xMm - a.xMm
        val vy = b.yMm - a.yMm
        val len2 = vx * vx + vy * vy
        if (len2 < 1e-12) return hypot(p.xMm - a.xMm, p.yMm - a.yMm)
        var t = ((p.xMm - a.xMm) * vx + (p.yMm - a.yMm) * vy) / len2
        if (t < 0.0) t = 0.0 else if (t > 1.0) t = 1.0
        val cx = a.xMm + t * vx
        val cy = a.yMm + t * vy
        return hypot(p.xMm - cx, p.yMm - cy)
    }
}
