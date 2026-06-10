package de.knutwurst.knutcut.svgcore

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sqrt

// ---------------------------------------------------------------------------
// HandleSide
// ---------------------------------------------------------------------------

/** Which control handle of a node is being referenced or moved. */
enum class HandleSide { IN, OUT }

// ---------------------------------------------------------------------------
// Matrix.inverse
// ---------------------------------------------------------------------------

/**
 * Invert the affine matrix.  Returns null when the determinant is too small
 * (degenerate transform — collinear axes or zero scale).
 *
 * The 2×2 linear part is:
 *   | a  c |      inverse  |  d -c |   / det
 *   | b  d |             |-b  a |
 *
 * Translation part follows from -(linear_inv) * (e, f).
 */
fun Matrix.inverse(): Matrix? {
    val det = a * d - b * c
    if (abs(det) < 1e-12) return null
    val id = 1.0 / det
    val ia =  d * id
    val ib = -b * id
    val ic = -c * id
    val id2 = a * id
    val ie = -(ia * e + ic * f)
    val if_ = -(ib * e + id2 * f)
    return Matrix(ia, ib, ic, id2, ie, if_)
}

// ---------------------------------------------------------------------------
// PathNode — smooth flag added (backward-compatible default = false)
// ---------------------------------------------------------------------------

/*
 * NOTE: PathNode is defined in Deform.kt. We cannot redefine it here.
 * The `smooth` field is added to the existing data class in Deform.kt.
 * See the companion edit to Deform.kt.
 */

// ---------------------------------------------------------------------------
// Node mutators
// ---------------------------------------------------------------------------

/**
 * Move node [i]'s anchor to [to], translating its handles by the same delta
 * so the node's local shape is preserved.
 */
fun EditablePath.moveAnchor(i: Int, to: Pt): EditablePath {
    val old = nodes[i]
    val dx = to.xMm - old.anchor.xMm
    val dy = to.yMm - old.anchor.yMm
    fun Pt?.shift() = this?.let { Pt(it.xMm + dx, it.yMm + dy) }
    val updated = old.copy(
        anchor = to,
        handleIn = old.handleIn.shift(),
        handleOut = old.handleOut.shift(),
    )
    return EditablePath(nodes.toMutableList().also { it[i] = updated }, closed)
}

/**
 * Move one control handle of node [i] to [to].
 *
 * When the node is marked smooth, the opposite handle is mirrored across the anchor:
 * its direction is reversed and its length is preserved (C1 continuity).
 * When the node is a corner, the opposite handle is left unchanged.
 */
fun EditablePath.moveHandle(i: Int, which: HandleSide, to: Pt): EditablePath {
    val node = nodes[i]
    val anchor = node.anchor

    fun mirrorAcrossAnchor(target: Pt): Pt {
        // Reflect target across anchor, preserving distance from anchor.
        val dx = anchor.xMm - target.xMm
        val dy = anchor.yMm - target.yMm
        return Pt(anchor.xMm + dx, anchor.yMm + dy)
    }

    val updated = when (which) {
        HandleSide.OUT -> {
            val newOut = to
            val newIn = if (node.smooth) {
                // Mirror: opposite length, mirrored direction.
                // Direction from anchor to new out-handle:
                val dox = to.xMm - anchor.xMm
                val doy = to.yMm - anchor.yMm
                val dist = hypot(dox, doy)
                // Keep the in-handle's original distance from anchor if it exists.
                val oldIn = node.handleIn
                if (oldIn != null && dist > 1e-12) {
                    val oldInDist = hypot(oldIn.xMm - anchor.xMm, oldIn.yMm - anchor.yMm)
                    Pt(anchor.xMm - dox / dist * oldInDist, anchor.yMm - doy / dist * oldInDist)
                } else {
                    mirrorAcrossAnchor(to)
                }
            } else {
                node.handleIn
            }
            node.copy(handleIn = newIn, handleOut = newOut)
        }
        HandleSide.IN -> {
            val newIn = to
            val newOut = if (node.smooth) {
                val dix = to.xMm - anchor.xMm
                val diy = to.yMm - anchor.yMm
                val dist = hypot(dix, diy)
                val oldOut = node.handleOut
                if (oldOut != null && dist > 1e-12) {
                    val oldOutDist = hypot(oldOut.xMm - anchor.xMm, oldOut.yMm - anchor.yMm)
                    Pt(anchor.xMm - dix / dist * oldOutDist, anchor.yMm - diy / dist * oldOutDist)
                } else {
                    mirrorAcrossAnchor(to)
                }
            } else {
                node.handleOut
            }
            node.copy(handleIn = newIn, handleOut = newOut)
        }
    }
    return EditablePath(nodes.toMutableList().also { it[i] = updated }, closed)
}

/**
 * Set the smooth flag on node [i].
 *
 * When [smooth] = true: make handles collinear through the anchor by averaging their
 * directions and keeping their individual distances.  If only one handle exists it is
 * mirrored to the other side.
 * When [smooth] = false: just flip the flag; handles stay where they are (corner).
 */
fun EditablePath.setSmooth(i: Int, smooth: Boolean): EditablePath {
    val node = nodes[i]
    val updated = if (!smooth) {
        node.copy(smooth = false)
    } else {
        val anchor = node.anchor
        val hIn = node.handleIn
        val hOut = node.handleOut

        // Compute average outward direction.
        val outDir: Pt? = when {
            hIn != null && hOut != null -> {
                // Average direction of -hIn and hOut vectors from anchor.
                val dix = anchor.xMm - hIn.xMm; val diy = anchor.yMm - hIn.yMm
                val dox = hOut.xMm - anchor.xMm; val doy = hOut.yMm - anchor.yMm
                val ni = hypot(dix, diy); val no_ = hypot(dox, doy)
                val ux = (if (ni > 1e-12) dix / ni else 0.0) + (if (no_ > 1e-12) dox / no_ else 0.0)
                val uy = (if (ni > 1e-12) diy / ni else 0.0) + (if (no_ > 1e-12) doy / no_ else 0.0)
                val ulen = hypot(ux, uy)
                if (ulen > 1e-12) Pt(ux / ulen, uy / ulen) else null
            }
            hOut != null -> {
                val dx = hOut.xMm - anchor.xMm; val dy = hOut.yMm - anchor.yMm
                val d = hypot(dx, dy)
                if (d > 1e-12) Pt(dx / d, dy / d) else null
            }
            hIn != null -> {
                // Out direction is the reverse of in.
                val dx = anchor.xMm - hIn.xMm; val dy = anchor.yMm - hIn.yMm
                val d = hypot(dx, dy)
                if (d > 1e-12) Pt(dx / d, dy / d) else null
            }
            else -> null
        }

        if (outDir == null) {
            node.copy(smooth = true)
        } else {
            val newOut = if (hOut != null) {
                val dist = hypot(hOut.xMm - anchor.xMm, hOut.yMm - anchor.yMm)
                Pt(anchor.xMm + outDir.xMm * dist, anchor.yMm + outDir.yMm * dist)
            } else null
            val newIn = if (hIn != null) {
                val dist = hypot(hIn.xMm - anchor.xMm, hIn.yMm - anchor.yMm)
                Pt(anchor.xMm - outDir.xMm * dist, anchor.yMm - outDir.yMm * dist)
            } else null
            node.copy(handleIn = newIn, handleOut = newOut, smooth = true)
        }
    }
    return EditablePath(nodes.toMutableList().also { it[i] = updated }, closed)
}

/**
 * Delete node [i].  No-op when the path would become degenerate:
 * open paths keep at least 2 nodes, closed paths keep at least 3.
 */
fun EditablePath.deleteNode(i: Int): EditablePath {
    if (i !in nodes.indices) return this
    val minNodes = if (closed) 3 else 2
    if (nodes.size <= minNodes) return this
    return EditablePath(nodes.toMutableList().also { it.removeAt(i) }, closed)
}

// ---------------------------------------------------------------------------
// insertNode — de Casteljau split
// ---------------------------------------------------------------------------

/**
 * Insert a new node on the segment from node[[segmentIndex]] to node[[segmentIndex]+1]
 * (or last→first when the path is closed) at parameter [t] ∈ [0,1].
 *
 * The segment is split using de Casteljau so the path's rendered shape is preserved.
 * For a straight segment (both adjacent handles null on that side) the split is a
 * simple linear interpolation.
 */
fun EditablePath.insertNode(segmentIndex: Int, t: Double): EditablePath {
    val n = nodes.size
    require(segmentIndex in nodes.indices) { "segmentIndex out of range" }
    val fromIdx = segmentIndex
    val toIdx = if (closed) (segmentIndex + 1) % n else segmentIndex + 1
    require(toIdx in nodes.indices) { "segmentIndex out of range" }

    val from = nodes[fromIdx]
    val to = nodes[toIdx]

    // The cubic control points for this segment (null handle = degenerate to anchor).
    val p0 = from.anchor
    val p1 = from.handleOut ?: from.anchor   // degenerate
    val p2 = to.handleIn ?: to.anchor         // degenerate
    val p3 = to.anchor

    val straight = from.handleOut == null && to.handleIn == null

    // de Casteljau one level at a time.
    fun lerp(a: Pt, b: Pt, u: Double) = Pt(a.xMm + u * (b.xMm - a.xMm), a.yMm + u * (b.yMm - a.yMm))
    // Suppress a computed handle if it coincides exactly with the anchor (zero-length handle).
    fun nonDegenerate(h: Pt, anchor: Pt): Pt? =
        if (h.xMm == anchor.xMm && h.yMm == anchor.yMm) null else h

    val p01  = lerp(p0, p1, t)
    val p12  = lerp(p1, p2, t)
    val p23  = lerp(p2, p3, t)
    val p012 = lerp(p01, p12, t)
    val p123 = lerp(p12, p23, t)
    val p0123 = lerp(p012, p123, t)  // new anchor

    // New node handles. For fully straight segments suppress all handles.  Also drop any
    // handle that de Casteljau collapsed to exactly the anchor (degenerate t=0 / t=1 case).
    val newHandleIn  = if (straight) null else nonDegenerate(p012, p0123)
    val newHandleOut = if (straight) null else nonDegenerate(p123, p0123)

    // Update from-node's handleOut and to-node's handleIn.
    val newFromHandleOut = if (straight) null else nonDegenerate(p01, from.anchor)
    val newToHandleIn    = if (straight) null else nonDegenerate(p23, to.anchor)

    val newFrom = from.copy(handleOut = newFromHandleOut)
    val mid     = PathNode(p0123, handleIn = newHandleIn, handleOut = newHandleOut)
    val newTo   = to.copy(handleIn = newToHandleIn)

    val result = nodes.toMutableList()
    result[fromIdx] = newFrom
    // Insert mid after fromIdx; if the path wraps (toIdx == 0) we insert at the end.
    val insertAt = if (toIdx == 0) result.size else toIdx
    result.add(insertAt, mid)
    result[if (toIdx == 0) 0 else insertAt + 1] = newTo

    return EditablePath(result, closed)
}

// ---------------------------------------------------------------------------
// Polyline → EditablePath
// ---------------------------------------------------------------------------

/**
 * Convert a [Polyline] to an [EditablePath] with corner nodes (no handles).
 * The [closed] flag is carried over from the polyline.
 * When the polyline is closed its last point typically duplicates the first;
 * that duplicate is dropped so the path has one node per distinct vertex.
 */
fun Polyline.toEditablePath(): EditablePath {
    var pts = points
    if (closed && pts.size >= 2 && pts.first() == pts.last()) {
        pts = pts.dropLast(1)
    }
    val nodeList = pts.map { PathNode(it) }
    return EditablePath(nodeList, closed)
}

// ---------------------------------------------------------------------------
// Hit-testing
// ---------------------------------------------------------------------------

/**
 * Index of the closest anchor within [maxDistMm], or null if none qualifies.
 */
fun EditablePath.nearestNode(p: Pt, maxDistMm: Double): Int? {
    var bestIdx: Int? = null
    var bestDist = maxDistMm
    for ((i, node) in nodes.withIndex()) {
        val d = dist(p, node.anchor)
        if (d < bestDist) { bestDist = d; bestIdx = i }
    }
    return bestIdx
}

/** Hit information for a control handle. */
data class HandleHit(val nodeIndex: Int, val side: HandleSide)

/**
 * The closest visible handle within [maxDistMm], or null.
 * Only handles that are non-null on the node are considered.
 */
fun EditablePath.nearestHandle(p: Pt, maxDistMm: Double): HandleHit? {
    var bestHit: HandleHit? = null
    var bestDist = maxDistMm
    for ((i, node) in nodes.withIndex()) {
        node.handleIn?.let { h ->
            val d = dist(p, h)
            if (d < bestDist) { bestDist = d; bestHit = HandleHit(i, HandleSide.IN) }
        }
        node.handleOut?.let { h ->
            val d = dist(p, h)
            if (d < bestDist) { bestDist = d; bestHit = HandleHit(i, HandleSide.OUT) }
        }
    }
    return bestHit
}

/** Hit information for a path segment. */
data class SegmentHit(val segmentIndex: Int, val t: Double, val distMm: Double)

/**
 * The closest point on the path (across all segments) within [maxDistMm].
 *
 * Each segment is sampled at [sampleCount] uniformly-spaced t values; the nearest
 * sample is refined with a short binary search to get a good [t] estimate.  This is
 * intentionally kept simple and deterministic — no Newton's method.
 *
 * Returns null when no point on any segment falls within [maxDistMm].
 */
fun EditablePath.nearestSegment(p: Pt, maxDistMm: Double, sampleCount: Int = 64): SegmentHit? {
    val n = nodes.size
    if (n < 2) return null

    val segCount = if (closed) n else n - 1

    var bestHit: SegmentHit? = null
    var bestDist = maxDistMm

    for (si in 0 until segCount) {
        val fromIdx = si
        val toIdx = if (closed) (si + 1) % n else si + 1
        val from = nodes[fromIdx]
        val to   = nodes[toIdx]

        val p0 = from.anchor
        val p1 = from.handleOut ?: from.anchor
        val p2 = to.handleIn    ?: to.anchor
        val p3 = to.anchor

        // Coarse scan.
        var bestT = 0.0
        var bestSegDist = Double.MAX_VALUE
        for (k in 0..sampleCount) {
            val t = k.toDouble() / sampleCount
            val pt = cubicAt(p0, p1, p2, p3, t)
            val d = dist(p, pt)
            if (d < bestSegDist) { bestSegDist = d; bestT = t }
        }

        // Refine around bestT with binary search over a shrinking window.
        var lo = (bestT - 1.0 / sampleCount).coerceAtLeast(0.0)
        var hi = (bestT + 1.0 / sampleCount).coerceAtMost(1.0)
        repeat(16) {
            val m1 = lo + (hi - lo) / 3.0
            val m2 = lo + 2.0 * (hi - lo) / 3.0
            if (dist(p, cubicAt(p0, p1, p2, p3, m1)) < dist(p, cubicAt(p0, p1, p2, p3, m2))) {
                hi = m2
            } else {
                lo = m1
            }
        }
        val refinedT = (lo + hi) / 2.0
        val refinedDist = dist(p, cubicAt(p0, p1, p2, p3, refinedT))

        if (refinedDist < bestDist) {
            bestDist = refinedDist
            bestHit = SegmentHit(si, refinedT, refinedDist)
        }
    }
    return bestHit
}

// ---------------------------------------------------------------------------
// dragSegment — reshape by dragging a point on the curve
// ---------------------------------------------------------------------------

/**
 * Reshape a path by dragging the point at parameter [t] on segment [segmentIndex] to [to].
 *
 * Adjusts the segment's two control handles so the on-curve point at t follows the finger.
 * For a cubic B(t) = (1-t)^3 P0 + 3(1-t)^2 t C1 + 3(1-t) t^2 C2 + t^3 P3:
 *   delta = to - B(t)
 *   C1 += delta * (1-t)
 *   C2 += delta * t
 * The endpoints are unaffected; the mid-curve follows the drag.
 *
 * Straight segments (null handles) gain real handles at the anchor position before the delta
 * is applied. If an involved node is smooth, its opposite handle is mirrored to keep C1
 * continuity. Returns a new EditablePath; the original is not modified.
 *
 * Requires [segmentIndex] in [nodes.indices]. For open paths also requires
 * [segmentIndex] < lastIndex (there is no segment beyond the last node on an open path).
 * [t] is clamped to (0, 1) exclusive so the endpoints are never moved.
 */
fun EditablePath.dragSegment(segmentIndex: Int, t: Double, to: Pt): EditablePath {
    val n = nodes.size
    require(segmentIndex in nodes.indices) { "segmentIndex out of range" }
    val fromIdx = segmentIndex
    val toIdx = if (closed) (segmentIndex + 1) % n else segmentIndex + 1
    require(toIdx in nodes.indices) { "segmentIndex out of range for open path" }

    // Clamp t away from the endpoints so anchors are never displaced.
    val tc = t.coerceIn(1e-6, 1.0 - 1e-6)

    val fromNode = nodes[fromIdx]
    val toNode   = nodes[toIdx]

    // Resolve null handles to the anchor (straight segment).
    val p0 = fromNode.anchor
    val p1 = fromNode.handleOut ?: fromNode.anchor
    val p2 = toNode.handleIn    ?: toNode.anchor
    val p3 = toNode.anchor

    // Current on-curve position and the displacement to apply.
    val current = cubicAt(p0, p1, p2, p3, tc)
    val dx = to.xMm - current.xMm
    val dy = to.yMm - current.yMm

    // Distribute delta onto C1 and C2 so that B(tc) exactly reaches `to`.
    //
    // The on-curve point shift from adding alpha*(1-t) to C1 and alpha*t to C2 is:
    //   ΔB(t) = 3t(1-t)[(1-t)² + t²] * alpha
    // Solving for alpha = delta / (3t(1-t)[(1-t)²+t²]) gives exact placement.
    // C1 += alpha*(1-t),  C2 += alpha*t  (endpoints are unaffected because their
    // basis weights are t³ and (1-t)³, neither contains the handles).
    val influence = 3.0 * tc * (1.0 - tc) * ((1.0 - tc) * (1.0 - tc) + tc * tc)
    val scale = if (influence > 1e-12) 1.0 / influence else 0.0
    val w1 = (1.0 - tc) * scale
    val w2 = tc * scale
    val newP1 = Pt(p1.xMm + dx * w1, p1.yMm + dy * w1)
    val newP2 = Pt(p2.xMm + dx * w2, p2.yMm + dy * w2)

    // Build updated nodes; start as a mutable copy.
    val result = nodes.toMutableList()

    // Update fromNode's handleOut.
    val updatedFrom = fromNode.copy(handleOut = newP1)
    result[fromIdx] = updatedFrom

    // If fromNode is smooth, mirror the opposite (handleIn) to keep C1.
    if (updatedFrom.smooth) {
        val dox = newP1.xMm - fromNode.anchor.xMm
        val doy = newP1.yMm - fromNode.anchor.yMm
        val dist = hypot(dox, doy)
        val oldIn = fromNode.handleIn
        val newIn = if (oldIn != null && dist > 1e-12) {
            val oldInDist = hypot(oldIn.xMm - fromNode.anchor.xMm, oldIn.yMm - fromNode.anchor.yMm)
            Pt(fromNode.anchor.xMm - dox / dist * oldInDist,
               fromNode.anchor.yMm - doy / dist * oldInDist)
        } else if (dist > 1e-12) {
            Pt(fromNode.anchor.xMm - dox / dist * dist,
               fromNode.anchor.yMm - doy / dist * dist)
        } else {
            fromNode.handleIn
        }
        result[fromIdx] = result[fromIdx].copy(handleIn = newIn)
    }

    // Update toNode's handleIn.
    val updatedTo = toNode.copy(handleIn = newP2)
    result[toIdx] = updatedTo

    // If toNode is smooth, mirror the opposite (handleOut) to keep C1.
    if (updatedTo.smooth) {
        val dix = newP2.xMm - toNode.anchor.xMm
        val diy = newP2.yMm - toNode.anchor.yMm
        val dist = hypot(dix, diy)
        val oldOut = toNode.handleOut
        val newOut = if (oldOut != null && dist > 1e-12) {
            val oldOutDist = hypot(oldOut.xMm - toNode.anchor.xMm, oldOut.yMm - toNode.anchor.yMm)
            Pt(toNode.anchor.xMm - dix / dist * oldOutDist,
               toNode.anchor.yMm - diy / dist * oldOutDist)
        } else if (dist > 1e-12) {
            Pt(toNode.anchor.xMm - dix / dist * dist,
               toNode.anchor.yMm - diy / dist * dist)
        } else {
            toNode.handleOut
        }
        result[toIdx] = result[toIdx].copy(handleOut = newOut)
    }

    return EditablePath(result, closed)
}

// ---------------------------------------------------------------------------
// Internal helpers
// ---------------------------------------------------------------------------

private fun dist(a: Pt, b: Pt) = hypot(a.xMm - b.xMm, a.yMm - b.yMm)

/** Evaluate a cubic Bézier at parameter [t] using the four control points. */
private fun cubicAt(p0: Pt, p1: Pt, p2: Pt, p3: Pt, t: Double): Pt {
    val u = 1.0 - t
    val uu = u * u; val tt = t * t
    val uuu = uu * u; val ttt = tt * t
    return Pt(
        uuu * p0.xMm + 3.0 * uu * t * p1.xMm + 3.0 * u * tt * p2.xMm + ttt * p3.xMm,
        uuu * p0.yMm + 3.0 * uu * t * p1.yMm + 3.0 * u * tt * p2.yMm + ttt * p3.yMm,
    )
}
