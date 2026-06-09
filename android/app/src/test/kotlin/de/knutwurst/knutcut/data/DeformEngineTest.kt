package de.knutwurst.knutcut.data

import de.knutwurst.knutcut.svgcore.Bounds
import de.knutwurst.knutcut.svgcore.Polyline
import de.knutwurst.knutcut.svgcore.Pt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.hypot

/** Unit tests for DeformEngine — pure JVM, no Robolectric needed. */
class DeformEngineTest {

    // A horizontal strip spanning one full circle circumference at a given radius.
    private fun stripForCircle(radiusMm: Double): List<Polyline> {
        val circum = 2 * PI * radiusMm
        val n = 20
        return listOf(
            Polyline(
                (0..n).map { i -> Pt(i.toDouble() / n * circum, 5.0) },
                closed = false,
            )
        )
    }

    @Test
    fun circleDeformPlacesPointsNearRadius() {
        val radius = 40.0
        val centerX = 50.0
        val centerY = 50.0
        val spec = CircleDeform(
            centerXMm = centerX,
            centerYMm = centerY,
            radiusMm = radius,
            startAngleDeg = 0.0,
            clockwise = false,
            baseline = DeformBaseline.BOTTOM,   // source y == maxY → v = 0 → lands at radius
        )
        // Source: y-value equals maxY (the strip's only y), so v = 0, result should be at radius.
        val source = stripForCircle(radius)
        val result = DeformEngine.apply(spec, source)

        for (p in result.flatMap { it.points }) {
            val dist = hypot(p.xMm - centerX, p.yMm - centerY)
            assertEquals("point should be ~radius from center", radius, dist, 2.0)
        }
    }

    @Test
    fun emptySourceReturnedUnchanged() {
        val spec = CircleDeform(0.0, 0.0, 30.0, 0.0, clockwise = false, baseline = DeformBaseline.BOTTOM)
        val empty = emptyList<Polyline>()
        assertSame(empty, DeformEngine.apply(spec, empty))
    }

    @Test
    fun sourceWithNoPointsReturnedUnchanged() {
        val spec = CircleDeform(0.0, 0.0, 30.0, 0.0, clockwise = false, baseline = DeformBaseline.BOTTOM)
        // A polyline with empty points list has no points, so no warp should occur.
        val source = listOf(Polyline(emptyList(), closed = false))
        assertSame(source, DeformEngine.apply(spec, source))
    }

    // ---------------------------------------------------------------------------
    // PathDeform tests
    // ---------------------------------------------------------------------------

    @Test
    fun pathDeformZeroCurvatureBaselinePointsStayOnGuide() {
        // With zero curvature the guide is a horizontal line at baselineY.
        // Points exactly on the baseline (v=0) map back to the guide y — no displacement.
        val bounds = Bounds(0.0, 0.0, 100.0, 20.0)
        val baselineY = 10.0  // CENTER of bounds
        val spec = bendDeformDefault(bounds, curvatureMm = 0.0, DeformBaseline.CENTER)
        val source = listOf(
            Polyline(listOf(Pt(0.0, baselineY), Pt(50.0, baselineY), Pt(100.0, baselineY)), closed = false),
        )
        val result = DeformEngine.apply(spec, source)
        // Baseline points must land at the guide y (within reparameterisation tolerance).
        for (p in result[0].points) {
            assertEquals("baseline point stays on guide y", baselineY, p.yMm, 0.5)
        }
    }

    @Test
    fun pathDeformOffsetIsProportionalToV() {
        // A point offset from the baseline by delta should be offset from the guide by ~delta.
        // For a horizontal guide the normal is (0,+1) in Pt-space (Y-down), so above-baseline
        // content (v < 0) is pushed DOWN, and below-baseline (v > 0) is pushed UP.
        val bounds = Bounds(0.0, 0.0, 100.0, 20.0)
        val baselineY = 10.0
        val spec = bendDeformDefault(bounds, curvatureMm = 0.0, DeformBaseline.CENTER)
        val delta = 4.0
        val source = listOf(
            // One point above baseline (y = baselineY - delta, v = -delta)
            Polyline(listOf(Pt(50.0, baselineY - delta), Pt(50.0, baselineY - delta)), closed = false),
            // One point below baseline (y = baselineY + delta, v = +delta)
            Polyline(listOf(Pt(50.0, baselineY + delta), Pt(50.0, baselineY + delta)), closed = false),
        )
        val result = DeformEngine.apply(spec, source)
        // Above-baseline (v = -delta) maps to guideY - normal * (-v) => guideY + delta => y = 14
        val expectedAbove = baselineY + delta
        // Below-baseline (v = +delta) maps to guideY - normal * (-v) => guideY - delta => y = 6
        val expectedBelow = baselineY - delta
        for (p in result[0].points) assertEquals("above-baseline point offset", expectedAbove, p.yMm, 0.5)
        for (p in result[1].points) assertEquals("below-baseline point offset", expectedBelow, p.yMm, 0.5)
    }

    @Test
    fun pathDeformPositiveCurvatureLiftsCentre() {
        // With positive curvature the guide bows upward, so a point at the horizontal centre
        // of the source should land above its original y position.
        val bounds = Bounds(0.0, 0.0, 100.0, 20.0)
        val curvature = 30.0
        val spec = bendDeformDefault(bounds, curvatureMm = curvature, DeformBaseline.CENTER)
        val baselineY = 10.0
        // A single point right at the centre of the source, on the baseline.
        val source = listOf(Polyline(listOf(Pt(0.0, baselineY), Pt(50.0, baselineY), Pt(100.0, baselineY)), closed = false))
        val result = DeformEngine.apply(spec, source)
        val pts = result[0].points
        // The centre point (index 1) should be noticeably above (smaller y) the end points.
        val yEnds = (pts.first().yMm + pts.last().yMm) / 2.0
        val yCentre = pts[1].yMm
        assertTrue("centre is above ends with positive curvature", yCentre < yEnds - 1.0)
    }

    @Test
    fun pathDeformEmptySourceReturnedUnchanged() {
        val bounds = Bounds(0.0, 0.0, 50.0, 10.0)
        val spec = bendDeformDefault(bounds, curvatureMm = 10.0, DeformBaseline.CENTER)
        val empty = emptyList<Polyline>()
        assertSame(empty, DeformEngine.apply(spec, empty))
    }

    // ---------------------------------------------------------------------------
    // EnvelopeDeform tests
    // ---------------------------------------------------------------------------

    private val EPS = 1e-9

    @Test
    fun envelopeDeformIdentityReproducesSource() {
        val source = listOf(
            Polyline(listOf(Pt(10.0, 5.0), Pt(90.0, 45.0)), closed = false),
        )
        // The identity spec must use the ACTUAL source bounds (same as DeformEngine computes internally).
        val bounds = Bounds.of(source.flatMap { it.points })
        val spec = envelopeDeformDefault(bounds)
        val result = DeformEngine.apply(spec, source)
        val srcPts = source.first().points
        val resPts = result.first().points
        for (i in srcPts.indices) {
            assertEquals("x[$i]", srcPts[i].xMm, resPts[i].xMm, EPS)
            assertEquals("y[$i]", srcPts[i].yMm, resPts[i].yMm, EPS)
        }
    }

    @Test
    fun envelopeDeformSkewMovesCornerPoints() {
        // Shift bottom corners rightward by 20mm; the top-right source corner must land on TR.
        val source = listOf(Polyline(listOf(Pt(0.0, 0.0), Pt(100.0, 0.0)), closed = false))
        val bounds = Bounds(0.0, 0.0, 100.0, 50.0)
        val spec = EnvelopeDeform(
            tl = Pt(0.0, 0.0),
            tr = Pt(100.0, 0.0),
            br = Pt(120.0, 50.0),
            bl = Pt(20.0, 50.0),
        )
        val result = DeformEngine.apply(spec, source)
        val pts = result.first().points
        // Source (0,0) = TL → maps to TL (0,0)
        assertEquals(0.0, pts[0].xMm, EPS)
        assertEquals(0.0, pts[0].yMm, EPS)
        // Source (100,0) = TR → maps to TR (100,0)
        assertEquals(100.0, pts[1].xMm, EPS)
        assertEquals(0.0, pts[1].yMm, EPS)
    }

    @Test
    fun envelopeDeformEmptySourceReturnedUnchanged() {
        val spec = envelopeDeformDefault(Bounds(0.0, 0.0, 10.0, 10.0))
        val empty = emptyList<Polyline>()
        assertSame(empty, DeformEngine.apply(spec, empty))
    }
}
