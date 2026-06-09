package de.knutwurst.knutcut.svgcore

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

// ---------------------------------------------------------------------------
// EditablePath
// ---------------------------------------------------------------------------

/**
 * A single node in an editable Bézier path.
 * Null handles mean the segment on that side is a straight line.
 */
data class PathNode(
    val anchor: Pt,
    val handleIn: Pt? = null,
    val handleOut: Pt? = null,
)

/**
 * A Bézier path made of [PathNode]s. Each consecutive pair of nodes forms a cubic segment
 * using [PathNode.handleOut] of the first node and [PathNode.handleIn] of the second.
 * When a handle is null the segment degenerates to a straight line on that side.
 */
class EditablePath(val nodes: List<PathNode>, val closed: Boolean = false) {

    /** Flatten to a [Polyline] within [toleranceMm] of the true curves. */
    fun toPolyline(toleranceMm: Double = PathFlattener.DEFAULT_TOLERANCE_MM): Polyline {
        if (nodes.isEmpty()) return Polyline(emptyList(), closed)

        val pts = mutableListOf<Pt>()
        pts.add(nodes.first().anchor)

        fun addSegment(from: PathNode, to: PathNode) {
            val c1 = from.handleOut
            val c2 = to.handleIn
            if (c1 == null && c2 == null) {
                pts.add(to.anchor)
            } else {
                val h1 = c1 ?: from.anchor
                val h2 = c2 ?: to.anchor
                PathFlattener.cubic(from.anchor, h1, h2, to.anchor, toleranceMm, pts)
            }
        }

        for (i in 0 until nodes.size - 1) {
            addSegment(nodes[i], nodes[i + 1])
        }

        if (closed && nodes.size >= 2) {
            addSegment(nodes.last(), nodes.first())
        }

        return Polyline(pts, closed)
    }
}

// ---------------------------------------------------------------------------
// ArcLengthPath
// ---------------------------------------------------------------------------

/**
 * Arc-length parameterisation of an already-flattened guide polyline.
 * [pointAt], [tangentAt], and [normalAt] all accept a distance [s] measured along the path.
 *
 * Open guides clamp [s] to [0, length]; closed guides wrap modulo [length].
 *
 * Normal convention: tangent rotated +90° (counter-clockwise in standard math coordinates).
 * For a rightward horizontal guide, tangent = (1,0) and normal = (0,+1).
 * A positive offset along the normal therefore moves upward — content placed ABOVE the
 * baseline (negative v in [PathWarp]) ends on the outer (positive-normal) side of a curve.
 */
class ArcLengthPath(private val guide: Polyline) {

    private val pts: List<Pt> = guide.points
    /** Cumulative arc lengths: cumLen[i] = distance from pts[0] to pts[i]. */
    private val cumLen: DoubleArray

    val length: Double

    init {
        require(pts.size >= 2) { "guide must have at least 2 points" }
        cumLen = DoubleArray(pts.size)
        cumLen[0] = 0.0
        for (i in 1 until pts.size) {
            val dx = pts[i].xMm - pts[i - 1].xMm
            val dy = pts[i].yMm - pts[i - 1].yMm
            cumLen[i] = cumLen[i - 1] + hypot(dx, dy)
        }
        length = cumLen.last()
    }

    /** Point at arc-length distance [s] along the guide. */
    fun pointAt(s: Double): Pt {
        val sc = resolveS(s)
        val (i, t) = segmentAndT(sc)
        return lerp(pts[i], pts[i + 1], t)
    }

    /** Unit tangent at arc-length distance [s]. */
    fun tangentAt(s: Double): Pt {
        val sc = resolveS(s)
        val (i, _) = segmentAndT(sc)
        val dx = pts[i + 1].xMm - pts[i].xMm
        val dy = pts[i + 1].yMm - pts[i].yMm
        val len = hypot(dx, dy)
        return if (len < 1e-12) Pt(1.0, 0.0) else Pt(dx / len, dy / len)
    }

    /** Unit normal at arc-length distance [s] (tangent rotated +90°). */
    fun normalAt(s: Double): Pt {
        val t = tangentAt(s)
        return Pt(-t.yMm, t.xMm)
    }

    // ------------------------------------------------------------------

    private fun resolveS(s: Double): Double = when {
        guide.closed -> {
            if (length < 1e-12) 0.0
            else {
                val wrapped = s % length
                if (wrapped < 0) wrapped + length else wrapped
            }
        }
        else -> s.coerceIn(0.0, length)
    }

    /**
     * Binary-search the cumLen table and return the segment index and interpolation parameter t
     * such that the queried position lies between pts[index] and pts[index+1].
     */
    private fun segmentAndT(s: Double): Pair<Int, Double> {
        if (s <= 0.0) return Pair(0, 0.0)
        if (s >= length) return Pair(pts.size - 2, 1.0)

        var lo = 0; var hi = pts.size - 1
        while (lo + 1 < hi) {
            val mid = (lo + hi) ushr 1
            if (cumLen[mid] <= s) lo = mid else hi = mid
        }
        val segLen = cumLen[hi] - cumLen[lo]
        val t = if (segLen < 1e-12) 0.0 else (s - cumLen[lo]) / segLen
        return Pair(lo, t)
    }

    private fun lerp(a: Pt, b: Pt, t: Double) =
        Pt(a.xMm + t * (b.xMm - a.xMm), a.yMm + t * (b.yMm - a.yMm))
}

// ---------------------------------------------------------------------------
// PathWarp
// ---------------------------------------------------------------------------

/** Warps source polylines onto a guide curve using arc-length parameterisation. */
object PathWarp {

    enum class Baseline { TOP, CENTER, BOTTOM }

    /**
     * Map each point of each polyline in [source] onto [guide].
     *
     * The source x-range is normalised to [0, guide.length] based on [sourceBounds].
     * The source y-offset from [baseline] is translated into a perpendicular offset along
     * the guide's normal.  A point ABOVE the baseline (y < baselineY when baseline=BOTTOM)
     * gives a negative v and therefore a positive normal displacement — it ends on the
     * outer side of a curve bending away from the normal direction.
     */
    fun alongPath(
        source: List<Polyline>,
        sourceBounds: Bounds,
        guide: ArcLengthPath,
        baseline: Baseline = Baseline.BOTTOM,
    ): List<Polyline> {
        val baselineY = baselineY(sourceBounds, baseline)
        val width = sourceBounds.widthMm.takeIf { it > 1e-12 } ?: 1.0

        return source.map { poly ->
            val warped = poly.points.map { p ->
                val u = (p.xMm - sourceBounds.minX) / width
                val s = u * guide.length
                val v = p.yMm - baselineY
                val origin = guide.pointAt(s)
                val normal = guide.normalAt(s)
                // v > 0: below baseline → inner side (negative normal direction)
                // v < 0: above baseline → outer side (positive normal direction)
                Pt(origin.xMm + normal.xMm * (-v), origin.yMm + normal.yMm * (-v))
            }
            Polyline(warped, poly.closed)
        }
    }

    /**
     * Map [source] onto a circular arc starting at [startAngleDeg] (degrees, measured from the
     * positive x-axis, counter-clockwise).  The source's width spans the full circumference of
     * the circle.  Uses [alongPath] internally after building a densely-sampled guide circle.
     */
    fun onCircle(
        source: List<Polyline>,
        sourceBounds: Bounds,
        center: Pt,
        radiusMm: Double,
        startAngleDeg: Double,
        clockwise: Boolean = false,
        baseline: Baseline = Baseline.BOTTOM,
    ): List<Polyline> {
        val circlePoints = buildCircleGuide(center, radiusMm, startAngleDeg, clockwise)
        val guide = ArcLengthPath(circlePoints)
        return alongPath(source, sourceBounds, guide, baseline)
    }

    // ------------------------------------------------------------------

    private fun baselineY(bounds: Bounds, baseline: Baseline): Double = when (baseline) {
        Baseline.TOP -> bounds.minY
        Baseline.CENTER -> (bounds.minY + bounds.maxY) / 2.0
        Baseline.BOTTOM -> bounds.maxY
    }

    private fun buildCircleGuide(
        center: Pt,
        radiusMm: Double,
        startAngleDeg: Double,
        clockwise: Boolean,
        segments: Int = 256,
    ): Polyline {
        val startRad = Math.toRadians(startAngleDeg)
        val direction = if (clockwise) -1.0 else 1.0
        val pts = (0..segments).map { i ->
            val a = startRad + direction * (i.toDouble() / segments) * 2 * PI
            Pt(center.xMm + radiusMm * cos(a), center.yMm + radiusMm * sin(a))
        }
        return Polyline(pts, closed = true)
    }
}
