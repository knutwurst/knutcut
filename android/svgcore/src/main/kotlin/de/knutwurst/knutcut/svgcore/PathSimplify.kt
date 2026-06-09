package de.knutwurst.knutcut.svgcore

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sqrt

// ---------------------------------------------------------------------------
// Ramer–Douglas–Peucker polyline simplification
// ---------------------------------------------------------------------------

/**
 * Simplify [points] using the Ramer–Douglas–Peucker algorithm.
 *
 * Always keeps the first and last point. Points whose perpendicular distance to the chord between
 * the current endpoint pair is less than [toleranceMm] are dropped.  Returns a new list; the
 * input is not modified.
 */
fun simplifyRdp(points: List<Pt>, toleranceMm: Double): List<Pt> {
    if (points.size <= 2) return points.toList()
    val result = mutableListOf<Boolean>().apply { repeat(points.size) { add(false) } }
    result[0] = true
    result[points.lastIndex] = true
    rdpRecursive(points, 0, points.lastIndex, toleranceMm, result)
    return points.filterIndexed { i, _ -> result[i] }
}

private fun rdpRecursive(
    pts: List<Pt>,
    start: Int,
    end: Int,
    tol: Double,
    keep: MutableList<Boolean>,
) {
    if (end <= start + 1) return
    val ax = pts[start].xMm; val ay = pts[start].yMm
    val bx = pts[end].xMm;   val by = pts[end].yMm
    val dx = bx - ax;        val dy = by - ay
    val len2 = dx * dx + dy * dy

    var maxDist = -1.0
    var splitAt = start
    for (i in start + 1 until end) {
        val px = pts[i].xMm - ax; val py = pts[i].yMm - ay
        val dist = if (len2 < 1e-18) {
            hypot(px, py)
        } else {
            // perpendicular distance from pts[i] to segment [start, end]
            abs(px * dy - py * dx) / sqrt(len2)
        }
        if (dist > maxDist) { maxDist = dist; splitAt = i }
    }

    if (maxDist >= tol) {
        keep[splitAt] = true
        rdpRecursive(pts, start, splitAt, tol, keep)
        rdpRecursive(pts, splitAt, end, tol, keep)
    }
}

// ---------------------------------------------------------------------------
// Catmull–Rom → cubic Bézier conversion
// ---------------------------------------------------------------------------

/**
 * Fit a smooth [EditablePath] through [points] using Catmull–Rom tangents converted to cubic
 * Bézier handles.  Each node's handle length is 1/6 of the distance to its neighbour, giving a
 * conservative but faithful approximation.
 *
 * Node count equals [points].size for an open path.  The resulting path's [EditablePath.toPolyline]
 * stays close to the original point list.
 *
 * For a [closed] path the tangent of the first node also considers its predecessor (the last point)
 * and vice versa.
 */
fun smoothToPath(points: List<Pt>, closed: Boolean = false): EditablePath {
    if (points.isEmpty()) return EditablePath(emptyList(), closed)
    if (points.size == 1) return EditablePath(listOf(PathNode(points[0])), closed)
    if (points.size == 2) {
        // Degenerate: straight line, no Bézier handles needed.
        return EditablePath(listOf(PathNode(points[0]), PathNode(points[1])), closed)
    }

    val n = points.size
    val nodes = (0 until n).map { i ->
        val prev = if (closed) points[(i - 1 + n) % n] else if (i == 0) points[0] else points[i - 1]
        val curr = points[i]
        val next = if (closed) points[(i + 1) % n] else if (i == n - 1) points[n - 1] else points[i + 1]

        // Catmull–Rom tangent vector at this node.
        val tx = (next.xMm - prev.xMm) / 2.0
        val ty = (next.yMm - prev.yMm) / 2.0

        // Scale the handle to 1/3 of the segment length so the cubic reproduces
        // the Catmull–Rom curve. Handle = anchor ± tangent/3.
        val distOut = if (i < n - 1) hypot(points[i + 1].xMm - curr.xMm, points[i + 1].yMm - curr.yMm) else 0.0
        val distIn  = if (i > 0)     hypot(curr.xMm - points[i - 1].xMm, curr.yMm - points[i - 1].yMm) else 0.0

        // Normalise tangent then scale to distNeighbour/3.
        val tLen = hypot(tx, ty)
        val (ux, uy) = if (tLen > 1e-12) (tx / tLen) to (ty / tLen) else (1.0 to 0.0)

        val hOut = if (distOut > 1e-12) Pt(curr.xMm + ux * distOut / 3.0, curr.yMm + uy * distOut / 3.0) else null
        val hIn  = if (distIn  > 1e-12) Pt(curr.xMm - ux * distIn  / 3.0, curr.yMm - uy * distIn  / 3.0) else null

        // First and last nodes of an open path only have one handle.
        val finalIn  = if (!closed && i == 0)     null else hIn
        val finalOut = if (!closed && i == n - 1) null else hOut

        PathNode(curr, handleIn = finalIn, handleOut = finalOut)
    }

    return EditablePath(nodes, closed)
}
