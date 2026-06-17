package de.knutwurst.knutcut.svgcore

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sqrt

// A node whose incoming/outgoing directions turn by more than ~70° is treated as a sharp corner and
// kept crisp (no rounding). cos(70°) ≈ 0.342: the corner test fires when the direction dot-product
// drops below this. Gentle turns (a coarse circle's ≤60° steps) stay smooth.
private const val CORNER_COS = 0.342

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
 * point sit close to each other relative to the stroke's overall size.  Kept deliberately on the
 * conservative side — for a plotter an accidental close turns an open line into a cut contour and can
 * waste material, and the node editor's manual Open/Close button covers the cases this misses.
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

/**
 * Convert a CLEAN polyline (e.g. a loaded SVG outline) into an editable path that PRESERVES its
 * shape. A loaded outline is already a flattened polygon, so the faithful editable form is a polygon
 * TRACE of it: drop only near-collinear/duplicate vertices (tight, size-relative RDP tolerance) and
 * keep every remaining vertex as a CORNER node — straight segments, no Bézier handles.
 *
 * It deliberately does NOT smooth. Fitting Catmull-Rom handles (as [smoothToPath] / [simplifyToBudget]
 * do) bows each segment into a curve the original didn't have, which visibly deforms a loaded shape
 * the moment it's converted. The points already capture the outline within the tolerance; a segment
 * can be curved on demand by dragging it. [simplifyToBudget] stays for hand-drawn strokes, where a
 * smooth few-node result is exactly what's wanted.
 *
 * The node count follows the shape's own complexity (a square → 4 nodes). A hard [cap] coarsens the
 * tolerance for a pathologically dense input so editing stays manageable.
 */
fun toEditablePreservingShape(points: List<Pt>, closed: Boolean, cap: Int = 200): EditablePath {
    // Drop a closed path's duplicate closing vertex so the seam isn't a zero-length segment.
    val pts = if (points.size > 1 && closed &&
        points.first().xMm == points.last().xMm && points.first().yMm == points.last().yMm
    ) points.dropLast(1) else points
    if (pts.size <= 2) return EditablePath(pts.map { PathNode(it) }, closed)

    val minX = pts.minOf { it.xMm }; val maxX = pts.maxOf { it.xMm }
    val minY = pts.minOf { it.yMm }; val maxY = pts.maxOf { it.yMm }
    val diag = hypot(maxX - minX, maxY - minY)
    // ~0.4% of the shape's diagonal, clamped: tight enough that the trace stays faithful, loose
    // enough to drop the dense runs a curve was flattened into.
    var tol = (diag * 0.004).coerceIn(0.3, 2.0)
    var reduced = simplifyRdp(pts, tol)
    var guard = 0
    while (reduced.size > cap && guard++ < 12) { tol *= 1.6; reduced = simplifyRdp(pts, tol) }
    return EditablePath(reduced.map { PathNode(it, handleIn = null, handleOut = null, smooth = false) }, closed)
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

        // Neighbour distances. On a closed path the seam node wraps so it still gets both handles.
        val distOut = if (closed || i < n - 1) hypot(next.xMm - curr.xMm, next.yMm - curr.yMm) else 0.0
        val distIn  = if (closed || i > 0)     hypot(curr.xMm - prev.xMm, curr.yMm - prev.yMm) else 0.0

        // Normalise the tangent; fall back to the nearest-neighbour direction when it is zero.
        val tLen = hypot(tx, ty)
        val (ux, uy) = if (tLen > 1e-12) {
            (tx / tLen) to (ty / tLen)
        } else {
            val ndx = next.xMm - curr.xMm; val ndy = next.yMm - curr.yMm
            val nd = hypot(ndx, ndy)
            if (nd > 1e-12) (ndx / nd) to (ndy / nd) else (1.0 to 0.0)
        }

        // Nodes with a neighbour on both sides (interior, or any node on a closed path) can be smooth;
        // open-path endpoints keep their single handle.
        val symmetric = closed || (i in 1 until n - 1)

        // Corner detection: where the path turns sharply, keep a crisp corner (no handles) so squares,
        // stars, hearts and other pointed shapes are not rounded off. Gentle turns stay smooth.
        val isCorner = symmetric && run {
            val inx = curr.xMm - prev.xMm; val iny = curr.yMm - prev.yMm
            val outx = next.xMm - curr.xMm; val outy = next.yMm - curr.yMm
            val li = hypot(inx, iny); val lo = hypot(outx, outy)
            li > 1e-9 && lo > 1e-9 && (inx * outx + iny * outy) / (li * lo) < CORNER_COS
        }

        if (isCorner) {
            PathNode(curr, handleIn = null, handleOut = null, smooth = false)
        } else {
            // Equal-length handles, clamped to 1/3 of the SHORTER neighbour so they never overshoot —
            // an overshooting handle bulges the curve past its neighbour and distorts the shape.
            val len = if (symmetric) min(distIn, distOut) / 3.0 else 0.0
            val lenOut = if (symmetric) len else distOut / 3.0
            val lenIn  = if (symmetric) len else distIn / 3.0

            val hOut = if (distOut > 1e-12) Pt(curr.xMm + ux * lenOut, curr.yMm + uy * lenOut) else null
            val hIn  = if (distIn  > 1e-12) Pt(curr.xMm - ux * lenIn,  curr.yMm - uy * lenIn)  else null

            // First and last nodes of an open path only have one handle.
            val finalIn  = if (!closed && i == 0)     null else hIn
            val finalOut = if (!closed && i == n - 1) null else hOut

            PathNode(curr, handleIn = finalIn, handleOut = finalOut, smooth = symmetric)
        }
    }

    return EditablePath(nodes, closed)
}
