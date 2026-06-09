package de.knutwurst.knutcut.data

import de.knutwurst.knutcut.svgcore.Bounds
import de.knutwurst.knutcut.svgcore.Polyline
import de.knutwurst.knutcut.svgcore.Pt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.hypot

/** Unit tests for circleDeformDefault — pure JVM, no Robolectric. */
class CircleDeformDefaultTest {

    private fun bounds(minX: Double, minY: Double, maxX: Double, maxY: Double) =
        Bounds(minX, minY, maxX, maxY)

    @Test
    fun centerIsAtBoundsCenter() {
        val b = bounds(10.0, 20.0, 90.0, 60.0)
        val spec = circleDeformDefault(b)
        assertEquals("centerX at bounds center", 50.0, spec.centerXMm, 1e-9)
        assertEquals("centerY at bounds center", 40.0, spec.centerYMm, 1e-9)
    }

    @Test
    fun radiusWrapsSourceWidthOnce() {
        val b = bounds(0.0, 0.0, 100.0, 20.0)
        val spec = circleDeformDefault(b)
        // radius = widthMm / (2*PI)
        val expected = 100.0 / (2.0 * PI)
        assertEquals("radius = widthMm / 2pi", expected, spec.radiusMm, 1e-9)
    }

    @Test
    fun startAngleIs90() {
        val b = bounds(0.0, 0.0, 50.0, 10.0)
        val spec = circleDeformDefault(b)
        assertEquals("startAngleDeg = 90", 90.0, spec.startAngleDeg, 1e-9)
    }

    @Test
    fun defaultBaselineIsBottom() {
        val b = bounds(0.0, 0.0, 40.0, 10.0)
        val spec = circleDeformDefault(b)
        assertEquals("default baseline is BOTTOM", DeformBaseline.BOTTOM, spec.baseline)
    }

    @Test
    fun customBaselineIsHonoured() {
        val b = bounds(0.0, 0.0, 40.0, 10.0)
        val spec = circleDeformDefault(b, DeformBaseline.CENTER)
        assertEquals("baseline passed through", DeformBaseline.CENTER, spec.baseline)
    }

    @Test
    fun radiusClampedToMinimumFive() {
        // Very narrow bounds produce a tiny radius — must be clamped to 5 mm.
        val b = bounds(0.0, 0.0, 1.0, 1.0)
        val spec = circleDeformDefault(b)
        assertTrue("radius >= 5 mm", spec.radiusMm >= 5.0)
    }

    /**
     * Verify the default (clockwise=true, baseline=BOTTOM, startAngle=90) puts content on the
     * OUTER side of the circle: points above the baseline (y < maxY) are farther from center
     * than the baseline points (y = maxY), which sit exactly on the circle.
     *
     * We use a two-row strip so the PathWarp bounds calculation sees the correct y-range [0..10].
     * The midpoint of the strip (x = circum/2, y = 5) sits above the baseline (y = 10) and
     * should end up outside the radius when the default spec is applied.
     */
    @Test
    fun defaultPutsContentOnOuterTopArc() {
        val radius = 40.0
        val circum = 2.0 * PI * radius
        val cx = circum / 2.0  // x position of the point we want to inspect (middle of strip)

        // Two-row strip spanning y=[0..10] so DeformEngine sees the full y-range.
        // The top row (y=0) and the baseline row (y=10) anchor the bounds.
        // We also include the mid-row point (y=5) and the baseline point (y=10) at x=cx.
        val source = listOf(
            Polyline(
                listOf(
                    Pt(0.0, 0.0), Pt(circum, 0.0),   // y = 0 (top edge, above baseline)
                    Pt(cx, 5.0),                       // mid-row point under inspection
                    Pt(cx, 10.0),                      // baseline point under inspection
                    Pt(0.0, 10.0), Pt(circum, 10.0),  // y = 10 (baseline edge)
                ),
                closed = false,
            )
        )

        val b = Bounds(0.0, 0.0, circum, 10.0)
        val spec = circleDeformDefault(b)  // clockwise=false, startAngleDeg=90, baseline=BOTTOM

        val result = DeformEngine.apply(spec, source)
        val pts = result.first().points

        // pts[2] was at y=5 (mid-row), pts[3] was at y=10 (baseline)
        val pMid = pts[2]; val pBase = pts[3]
        val cxs = spec.centerXMm; val cys = spec.centerYMm

        val distBase = hypot(pBase.xMm - cxs, pBase.yMm - cys)
        val distMid  = hypot(pMid.xMm - cxs, pMid.yMm - cys)

        // The baseline (y = maxY) lands on the circle; points above the baseline are pushed outward.
        assertEquals("baseline point lands at radius", radius, distBase, 1.5)
        assertTrue("above-baseline point is farther from center (outer side)", distMid > distBase)
    }
}
