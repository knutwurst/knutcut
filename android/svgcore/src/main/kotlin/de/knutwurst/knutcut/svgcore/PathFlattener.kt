package de.knutwurst.knutcut.svgcore

import kotlin.math.abs
import kotlin.math.hypot

/** Flattens cubic/quadratic beziers into line segments within a millimetre tolerance. */
object PathFlattener {
    const val DEFAULT_TOLERANCE_MM = 0.1

    /** Append the flattened cubic to [out]. The caller has already placed [p0]; this adds up to and including [p3]. */
    fun cubic(p0: Pt, p1: Pt, p2: Pt, p3: Pt, tol: Double, out: MutableList<Pt>) {
        subdivide(p0, p1, p2, p3, tol, 0, out)
        out.add(p3)
    }

    /** Append the flattened quadratic to [out] (elevated to a cubic). */
    fun quad(p0: Pt, ctrl: Pt, p2: Pt, tol: Double, out: MutableList<Pt>) {
        val c1 = Pt(p0.xMm + 2.0 / 3.0 * (ctrl.xMm - p0.xMm), p0.yMm + 2.0 / 3.0 * (ctrl.yMm - p0.yMm))
        val c2 = Pt(p2.xMm + 2.0 / 3.0 * (ctrl.xMm - p2.xMm), p2.yMm + 2.0 / 3.0 * (ctrl.yMm - p2.yMm))
        cubic(p0, c1, c2, p2, tol, out)
    }

    private fun subdivide(p0: Pt, p1: Pt, p2: Pt, p3: Pt, tol: Double, depth: Int, out: MutableList<Pt>) {
        if (depth >= 18 || flatEnough(p0, p1, p2, p3, tol)) return
        val p01 = mid(p0, p1); val p12 = mid(p1, p2); val p23 = mid(p2, p3)
        val p012 = mid(p01, p12); val p123 = mid(p12, p23); val p0123 = mid(p012, p123)
        subdivide(p0, p01, p012, p0123, tol, depth + 1, out)
        out.add(p0123)
        subdivide(p0123, p123, p23, p3, tol, depth + 1, out)
    }

    private fun mid(a: Pt, b: Pt) = Pt((a.xMm + b.xMm) / 2, (a.yMm + b.yMm) / 2)

    private fun flatEnough(p0: Pt, p1: Pt, p2: Pt, p3: Pt, tol: Double): Boolean =
        distToLine(p1, p0, p3) <= tol && distToLine(p2, p0, p3) <= tol

    private fun distToLine(p: Pt, a: Pt, b: Pt): Double {
        val dx = b.xMm - a.xMm; val dy = b.yMm - a.yMm
        val len = hypot(dx, dy)
        if (len < 1e-12) return hypot(p.xMm - a.xMm, p.yMm - a.yMm)
        return abs((p.xMm - a.xMm) * dy - (p.yMm - a.yMm) * dx) / len
    }
}
