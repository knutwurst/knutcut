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
/**
 * Heuristic for "did the user mean to draw a closed shape?": true when the stroke's first and last
 * point sit close to each other relative to the stroke's overall size.  Deliberately generous —
 * sketching a heart and lifting the finger near where it started should give a closed, cuttable
 * outline without demanding millimetre-perfect endpoints.
 *
 * Closed when the end-to-end gap is at most [absToleranceMm] OR at most [relTolerance] of the
 * stroke's bounding-box diagonal.  An open line or arc keeps its ends far apart (the gap is roughly
 * the whole extent) and stays open.  Fewer than three points can't enclose an area → false.
 */
fun looksClosed(points: List<Pt>, absToleranceMm: Double = 12.0, relTolerance: Double = 0.30): Boolean {
    if (points.size < 3) return false
    val first = points.first()
    val last = points.last()
    val gap = hypot(last.xMm - first.xMm, last.yMm - first.yMm)
    if (gap <= absToleranceMm) return true
    val minX = points.minOf { it.xMm }; val maxX = points.maxOf { it.xMm }
    val minY = points.minOf { it.yMm }; val maxY = points.maxOf { it.yMm }
    val diag = hypot(maxX - minX, maxY - minY)
    return diag > 1e-9 && gap <= relTolerance * diag
}

/**
 * Reduce a dense polyline to a smooth [EditablePath] with at most ~[targetNodes] nodes (hard cap
 * [hardCap]).  Binary-searches the RDP tolerance in [[minEpsMm]..[maxEpsMm]] mm until the node
 * count fits, then smooths.  Used both for freehand strokes and when converting a warp-baked (very
 * dense) layer to editable nodes.
 *
 * The default budget is kept small on purpose: a hand-drawn shape is easier to edit with a handful
 * of nodes than with dozens, and most of those nodes only carry curvature anyway.  Extra nodes can
 * always be added by hand where more control is wanted.
 *
 * - Inputs already under budget are smoothed directly, without further simplification.
 * - If even [maxEpsMm] cannot reach [targetNodes], the result at [maxEpsMm] is used; but if that
 *   still exceeds [hardCap], the binary search returns the last epsilon whose result was ≤
 *   [hardCap] instead.
 * - Endpoints are always preserved (RDP guarantee).
 * - Empty or single-point inputs are returned as-is via [smoothToPath].
 */
fun simplifyToBudget(
    points: List<Pt>,
    closed: Boolean,
    targetNodes: Int = 8,
    hardCap: Int = 40,
    minEpsMm: Double = 0.1,
    maxEpsMm: Double = 6.0,
): EditablePath {
    // Trivial: already within budget.
    if (points.size <= targetNodes) return smoothToPath(points, closed)

    // Binary-search for the smallest epsilon that brings the count to ≤ targetNodes.
    // We track the last candidate whose count was ≤ hardCap so we can fall back to it
    // if we overshoot the hard cap even at maxEpsMm.
    var lo = minEpsMm
    var hi = maxEpsMm
    var bestUnderHardCap: List<Pt>? = null

    // Quick check: does maxEpsMm already satisfy targetNodes?
    val atMax = simplifyRdp(points, maxEpsMm)
    if (atMax.size <= hardCap) bestUnderHardCap = atMax
    if (atMax.size <= targetNodes) return smoothToPath(atMax, closed)

    // ≤ 16 iterations are more than enough for any [minEps..maxEps] range.
    repeat(16) {
        val mid = (lo + hi) / 2.0
        val reduced = simplifyRdp(points, mid)
        if (reduced.size <= hardCap) bestUnderHardCap = reduced
        when {
            reduced.size <= targetNodes -> hi = mid   // good — try smaller eps (fewer dropped pts)
            else                        -> lo = mid   // still too many — push tolerance up
        }
    }

    // Use the result at the converged hi (≤ targetNodes if achievable), otherwise the best
    // result that stayed within the hard cap.
    val finalReduced = simplifyRdp(points, hi)
    val chosen = when {
        finalReduced.size <= targetNodes -> finalReduced
        bestUnderHardCap != null         -> bestUnderHardCap!!
        else                             -> finalReduced   // nothing satisfied hardCap — accept as-is
    }
    return smoothToPath(chosen, closed)
}

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
        val (ux, uy) = if (tLen > 1e-12) {
            (tx / tLen) to (ty / tLen)
        } else {
            // Zero Catmull-Rom tangent (prev == next).  Fall back to the direction
            // from curr toward the nearest distinct neighbour.
            val ndx = next.xMm - curr.xMm; val ndy = next.yMm - curr.yMm
            val nd = hypot(ndx, ndy)
            if (nd > 1e-12) (ndx / nd) to (ndy / nd) else (1.0 to 0.0)
        }

        val hOut = if (distOut > 1e-12) Pt(curr.xMm + ux * distOut / 3.0, curr.yMm + uy * distOut / 3.0) else null
        val hIn  = if (distIn  > 1e-12) Pt(curr.xMm - ux * distIn  / 3.0, curr.yMm - uy * distIn  / 3.0) else null

        // First and last nodes of an open path only have one handle.
        val finalIn  = if (!closed && i == 0)     null else hIn
        val finalOut = if (!closed && i == n - 1) null else hOut

        PathNode(curr, handleIn = finalIn, handleOut = finalOut)
    }

    return EditablePath(nodes, closed)
}
