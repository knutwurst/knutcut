package de.knutwurst.knutcut.data

import de.knutwurst.knutcut.svgcore.Bounds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** Unit tests for [bendDeformDefault] — pure JVM, no Robolectric needed. */
class BendDeformDefaultTest {

    private fun bounds(w: Double = 100.0, h: Double = 20.0) = Bounds(0.0, 0.0, w, h)

    // Returns the y-coordinate of the guide midpoint (the apex anchor of node[1]).
    private fun midY(spec: PathDeform): Double = spec.guide[1].anchor.yMm

    // Returns the baseline Y for a given bounds + baseline.
    private fun expectedBaselineY(b: Bounds, bl: DeformBaseline) = when (bl) {
        DeformBaseline.TOP    -> b.minY
        DeformBaseline.CENTER -> (b.minY + b.maxY) / 2.0
        DeformBaseline.BOTTOM -> b.maxY
    }

    @Test
    fun zeroCurvatureProducesHorizontalGuide() {
        val b = bounds()
        val spec = bendDeformDefault(b, curvatureMm = 0.0, baseline = DeformBaseline.CENTER)
        // With curvature 0 the mid anchor Y must equal the baseline Y (straight line).
        val baselineY = expectedBaselineY(b, DeformBaseline.CENTER)
        assertEquals("midpoint Y == baseline Y when curvature=0", baselineY, midY(spec), 1e-9)
    }

    @Test
    fun positiveCurvatureLiftsMidpoint() {
        val b = bounds()
        val bl = DeformBaseline.CENTER
        val baselineY = expectedBaselineY(b, bl)
        val spec = bendDeformDefault(b, curvatureMm = 30.0, bl)
        // Mid anchor must be above the baseline: midY < baselineY (Y increases downward).
        assertTrue("midY < baselineY for positive curvature", midY(spec) < baselineY)
        assertEquals("midpoint is exactly curvatureMm above baseline", baselineY - 30.0, midY(spec), 1e-9)
    }

    @Test
    fun negativeCurvatureLowersMidpoint() {
        val b = bounds()
        val bl = DeformBaseline.CENTER
        val baselineY = expectedBaselineY(b, bl)
        val spec = bendDeformDefault(b, curvatureMm = -20.0, bl)
        assertTrue("midY > baselineY for negative curvature", midY(spec) > baselineY)
        assertEquals("midpoint is exactly |curvature| below baseline", baselineY + 20.0, midY(spec), 1e-9)
    }

    @Test
    fun guideStartsAtLeftEdgeOfBounds() {
        val b = Bounds(10.0, 5.0, 90.0, 25.0)
        val spec = bendDeformDefault(b, curvatureMm = 0.0, DeformBaseline.CENTER)
        assertEquals("start anchor x == bounds.minX", b.minX, spec.guide.first().anchor.xMm, 1e-9)
    }

    @Test
    fun guideEndsAtRightEdgeOfBounds() {
        val b = Bounds(10.0, 5.0, 90.0, 25.0)
        val spec = bendDeformDefault(b, curvatureMm = 0.0, DeformBaseline.CENTER)
        assertEquals("end anchor x == bounds.maxX", b.maxX, spec.guide.last().anchor.xMm, 1e-9)
    }

    @Test
    fun midAnchorXIsAtHorizontalCenter() {
        val b = Bounds(0.0, 0.0, 100.0, 20.0)
        val spec = bendDeformDefault(b, curvatureMm = 40.0, DeformBaseline.CENTER)
        assertEquals("mid anchor x == center of bounds", 50.0, spec.guide[1].anchor.xMm, 1e-9)
    }

    @Test
    fun baselineTopIsHonouredForStartAndEndY() {
        val b = bounds()
        val spec = bendDeformDefault(b, curvatureMm = 0.0, DeformBaseline.TOP)
        val expected = b.minY
        assertEquals("start anchor Y == minY for TOP", expected, spec.guide.first().anchor.yMm, 1e-9)
        assertEquals("end anchor Y == minY for TOP",   expected, spec.guide.last().anchor.yMm, 1e-9)
    }

    @Test
    fun baselineBottomIsHonouredForStartAndEndY() {
        val b = bounds()
        val spec = bendDeformDefault(b, curvatureMm = 0.0, DeformBaseline.BOTTOM)
        val expected = b.maxY
        assertEquals("start anchor Y == maxY for BOTTOM", expected, spec.guide.first().anchor.yMm, 1e-9)
        assertEquals("end anchor Y == maxY for BOTTOM",   expected, spec.guide.last().anchor.yMm, 1e-9)
    }

    @Test
    fun guideHasThreeNodes() {
        val spec = bendDeformDefault(bounds(), curvatureMm = 10.0, DeformBaseline.CENTER)
        assertEquals("guide always has 3 nodes (start, mid, end)", 3, spec.guide.size)
    }

    @Test
    fun handlesAreSymmetric() {
        val b = Bounds(0.0, 0.0, 100.0, 20.0)
        val spec = bendDeformDefault(b, curvatureMm = 25.0, DeformBaseline.CENTER)
        val midNode = spec.guide[1]
        val hIn  = midNode.handleIn!!
        val hOut = midNode.handleOut!!
        // In and out handles must be equidistant from the mid anchor in x.
        val dIn  = abs(midNode.anchor.xMm - hIn.xMm)
        val dOut = abs(hOut.xMm - midNode.anchor.xMm)
        assertEquals("handles equidistant from mid anchor", dIn, dOut, 1e-9)
    }

    @Test
    fun zeroCurvatureGuideIsFlattenable() {
        // Sanity: the guide produced for curvature=0 must flatten to a polyline with >=2 points.
        val bounds = Bounds(0.0, 0.0, 100.0, 20.0)
        val spec = bendDeformDefault(bounds, curvatureMm = 0.0, DeformBaseline.CENTER)
        val poly = de.knutwurst.knutcut.svgcore.EditablePath(spec.guide, spec.closed).toPolyline()
        assertTrue("guide flattens to at least 2 pts", poly.points.size >= 2)
    }

    @Test
    fun zeroCurvatureBaselinePointsStayOnGuide() {
        // With zero curvature the guide is horizontal at baselineY.
        // Points placed exactly on the baseline (v=0) must map back to the guide y.
        val bounds = Bounds(0.0, 0.0, 100.0, 20.0)
        val baselineY = 10.0  // CENTER of bounds
        val spec = bendDeformDefault(bounds, curvatureMm = 0.0, DeformBaseline.CENTER)
        val source = listOf(
            de.knutwurst.knutcut.svgcore.Polyline(
                listOf(
                    de.knutwurst.knutcut.svgcore.Pt(0.0, baselineY),
                    de.knutwurst.knutcut.svgcore.Pt(50.0, baselineY),
                    de.knutwurst.knutcut.svgcore.Pt(100.0, baselineY),
                ),
                closed = false,
            )
        )
        val result = DeformEngine.apply(spec, source)
        for (p in result.flatMap { it.points }) {
            assertEquals("baseline point maps to guide y", baselineY, p.yMm, 0.5)
        }
    }
}
