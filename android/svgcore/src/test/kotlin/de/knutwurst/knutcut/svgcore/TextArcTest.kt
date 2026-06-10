package de.knutwurst.knutcut.svgcore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * Tests for [TextArc.layoutOnArc].
 *
 * Synthetic glyph: a 10×10 square whose corners are (0,0), (10,0), (10,-10), (0,-10)
 * (pen origin at bottom-left, baseline at y=0, ink above baseline has negative y).
 * Advance width = 10 mm.
 */
class TextArcTest {

    private val EPS = 1e-9

    // A unit glyph: 10×10 square, advance 10.
    private fun square(x0: Double = 0.0, y0: Double = 0.0): GlyphRun {
        val pts = listOf(Pt(x0, y0), Pt(x0 + 10.0, y0), Pt(x0 + 10.0, y0 - 10.0), Pt(x0, y0 - 10.0))
        return GlyphRun(listOf(Polyline(pts, closed = true)), advanceMm = 10.0)
    }

    // Centroid of the points in the first (and only) polyline of the first result polyline that
    // corresponds to glyph [index]. Since each glyph contributes exactly one polyline, the index
    // maps 1:1.
    private fun centroid(polys: List<Polyline>, index: Int): Pt {
        val pts = polys[index].points
        return Pt(pts.sumOf { it.xMm } / pts.size, pts.sumOf { it.yMm } / pts.size)
    }

    // Pairwise distances between all points in a polyline.
    private fun pairwiseDistances(poly: Polyline): List<Double> {
        val pts = poly.points
        val dists = mutableListOf<Double>()
        for (i in pts.indices) {
            for (j in i + 1 until pts.size) {
                dists.add(hypot(pts[i].xMm - pts[j].xMm, pts[i].yMm - pts[j].yMm))
            }
        }
        return dists
    }

    // ---------------------------------------------------------------------------
    // Straight layout (curve = 0)
    // ---------------------------------------------------------------------------

    @Test
    fun straightLayoutPlacesGlyphsAtCumulativeAdvance() {
        // Three glyphs each with advance 10: origins at x=0, 10, 20.
        val glyphs = listOf(square(), square(), square())
        val result = TextArc.layoutOnArc(glyphs, curve = 0.0)

        assertEquals(3, result.size)

        // First glyph: no shift — corners at (0,0), (10,0), (10,-10), (0,-10).
        val g0 = result[0].points
        assertEquals(0.0, g0[0].xMm, EPS)
        assertEquals(0.0, g0[0].yMm, EPS)
        assertEquals(10.0, g0[1].xMm, EPS)

        // Second glyph shifted by +10 in x.
        val g1 = result[1].points
        assertEquals(10.0, g1[0].xMm, EPS)
        assertEquals(0.0, g1[0].yMm, EPS)

        // Third glyph shifted by +20 in x.
        val g2 = result[2].points
        assertEquals(20.0, g2[0].xMm, EPS)
        assertEquals(0.0, g2[0].yMm, EPS)
    }

    @Test
    fun straightLayoutTotalSpanEqualsWidth() {
        val glyphs = listOf(square(), square(), square())
        val result = TextArc.layoutOnArc(glyphs, curve = 0.0)
        val allX = result.flatMap { it.points }.map { it.xMm }
        assertEquals(0.0, allX.min(), EPS)
        assertEquals(30.0, allX.max(), EPS)
    }

    @Test
    fun straightLayoutYIsUnchanged() {
        val glyphs = listOf(square(), square(), square())
        val result = TextArc.layoutOnArc(glyphs, curve = 0.0)
        // Original y values are 0 and -10; those must be preserved.
        for (poly in result) {
            for (pt in poly.points) {
                assertTrue("y should be 0 or -10, got ${pt.yMm}", abs(pt.yMm) < EPS || abs(pt.yMm + 10.0) < EPS)
            }
        }
    }

    // ---------------------------------------------------------------------------
    // Rigid body check (curve = 0.5)
    // ---------------------------------------------------------------------------

    @Test
    fun arcLayoutPreservesIntraGlyphDistances() {
        // For any non-zero curve each glyph is rigidly transformed: all pairwise distances
        // between its points must equal those in the original (straight-layout) glyph.
        val glyphs = listOf(square(), square(), square())

        val straight = TextArc.layoutOnArc(glyphs, curve = 0.0)
        val arced    = TextArc.layoutOnArc(glyphs, curve = 0.5)

        assertEquals(straight.size, arced.size)

        for (i in straight.indices) {
            val straightDists = pairwiseDistances(straight[i])
            val arcedDists    = pairwiseDistances(arced[i])
            assertEquals("glyph $i: distance count", straightDists.size, arcedDists.size)
            for (k in straightDists.indices) {
                assertEquals(
                    "glyph $i pair $k: distance not preserved",
                    straightDists[k], arcedDists[k], 1e-6
                )
            }
        }
    }

    // ---------------------------------------------------------------------------
    // Upward arc (curve = 0.5): middle glyph is higher (smaller y)
    // ---------------------------------------------------------------------------

    @Test
    fun positiveCurveMiddleGlyphHigherThanEnds() {
        // With 3 glyphs the middle one (index 1) should have the smallest centroid y
        // (highest on screen) because curve > 0 bends the baseline upward.
        val glyphs = listOf(square(), square(), square())
        val result = TextArc.layoutOnArc(glyphs, curve = 0.5)

        val yFirst  = centroid(result, 0).yMm
        val yMiddle = centroid(result, 1).yMm
        val yLast   = centroid(result, 2).yMm

        assertTrue("middle glyph y ($yMiddle) must be less than first ($yFirst)", yMiddle < yFirst)
        assertTrue("middle glyph y ($yMiddle) must be less than last ($yLast)",  yMiddle < yLast)
    }

    @Test
    fun positiveCurveOuterGlyphsAreTilted() {
        // The first and last glyphs must be rotated; the middle glyph (at the apex) stays
        // upright (φ = 0 at the midpoint).  We check by comparing the orientation vectors of
        // the first and last polylines: their horizontal edge (corner 0→1) should no longer be
        // purely horizontal.
        val glyphs = listOf(square(), square(), square())
        val result = TextArc.layoutOnArc(glyphs, curve = 0.5)

        fun horizontalEdgeAngle(poly: Polyline): Double {
            val p0 = poly.points[0]; val p1 = poly.points[1]
            return atan2(p1.yMm - p0.yMm, p1.xMm - p0.xMm)
        }

        val angleFirst  = horizontalEdgeAngle(result[0])
        val angleMiddle = horizontalEdgeAngle(result[1])
        val angleLast   = horizontalEdgeAngle(result[2])

        // Middle glyph at the apex has φ = 0 → edge stays horizontal.
        assertEquals(0.0, angleMiddle, 1e-6)

        // First glyph (left of apex) has negative φ → edge tilts counter-clockwise (negative angle).
        assertTrue("first glyph edge angle ($angleFirst) must be negative", angleFirst < -1e-6)

        // Last glyph (right of apex) has positive φ → edge tilts clockwise (positive angle).
        assertTrue("last glyph edge angle ($angleLast) must be positive", angleLast > 1e-6)
    }

    // ---------------------------------------------------------------------------
    // Full circle (curve = 1.0)
    // ---------------------------------------------------------------------------

    @Test
    fun fullCircleFirstAndLastGlyphCentroidsAreClose() {
        // With 4 glyphs (W=40), θ=2π, the angular gap between adjacent glyph centres is
        // 10/R = 10·2π/40 = π/2.  The first centre is at angle −3π/4 from the apex
        // (φ = (5−20)/R = −15/R = −3π/4) and the last at +3π/4.  They are closer to the
        // apex than glyph 1 (φ = −π/4) is to glyph 2 (φ = +π/4) … no, easier:
        //
        // Use a property that holds unambiguously: the angular separation between glyph 0 and
        // glyph 3 as seen from the circle centre must equal 2π * 30/40 = 3π/2 ≈ 4.712 rad.
        // We verify that the angular distance is more than π (i.e. they are not on the same
        // "short arc") and less than 2π (they haven't wrapped more than once).
        val glyphs = listOf(square(), square(), square(), square())
        val result = TextArc.layoutOnArc(glyphs, curve = 1.0)

        val W  = 40.0
        val R  = W / (2.0 * PI)
        val cx = W / 2.0
        val cy = R

        fun angle(g: Int): Double {
            val c = centroid(result, g)
            return atan2(c.yMm - cy, c.xMm - cx)
        }

        val a0 = angle(0)
        val a3 = angle(3)
        var angularSep = a3 - a0
        while (angularSep < 0.0) angularSep += 2.0 * PI

        // Expected: 3π/2 (the span from first to last, going clockwise / positive angle).
        val expected = 2.0 * PI * (W - 10.0) / W  // 3π/2
        assertEquals("angular separation between glyph 0 and 3", expected, angularSep, 0.01)
    }

    @Test
    fun fullCircleAngularSpanMatchesFormula() {
        // W=40, advance=10; formula gives span = 2π * (W - advance) / W = 2π * 0.75 ≈ 4.712 rad.
        val glyphs = listOf(square(), square(), square(), square())
        val result = TextArc.layoutOnArc(glyphs, curve = 1.0)

        val W  = 40.0
        val R  = W / (2.0 * PI)
        val cx = W / 2.0
        val cy = R  // circle center for positive curve

        // Centre of each glyph on the arc, measured as angle from circle center.
        fun angle(g: Int): Double {
            val c = centroid(result, g)
            return atan2(c.yMm - cy, c.xMm - cx)
        }

        val a0 = angle(0)
        val a3 = angle(3)
        var span = a3 - a0
        // Normalise to [0, 2π)
        while (span < 0) span += 2.0 * PI
        while (span >= 2.0 * PI) span -= 2.0 * PI

        val expected = 2.0 * PI * (W - 10.0) / W  // ≈ 4.712
        assertEquals(expected, span, 0.01)
    }

    // ---------------------------------------------------------------------------
    // Symmetry: curve = -0.5 mirrors curve = +0.5 across the baseline
    // ---------------------------------------------------------------------------

    @Test
    fun negativeCurveArcsInOppositeDirection() {
        // For +curve the BASELINE ORIGINS of the end glyphs are below the middle (positive y
        // offset), while for −curve they are above the middle (negative y offset).
        // We use a single-point probe glyph at the local origin so the result point IS
        // the baseline origin directly.
        val probe = GlyphRun(listOf(Polyline(listOf(Pt(0.0, 0.0)), closed = false)), advanceMm = 10.0)

        val posOrigins = TextArc.layoutOnArc(listOf(probe, probe, probe), curve =  0.5)
        val negOrigins = TextArc.layoutOnArc(listOf(probe, probe, probe), curve = -0.5)

        // +curve: middle origin is at y = 0 (apex), end origins have y > 0 (below baseline).
        val posYMiddle = posOrigins[1].points[0].yMm
        val posYFirst  = posOrigins[0].points[0].yMm
        assertTrue("+curve end origin below middle: first y ($posYFirst) > middle y ($posYMiddle)",
            posYFirst > posYMiddle)

        // −curve: middle origin is at y = 0 (apex), end origins have y < 0 (above baseline).
        val negYMiddle = negOrigins[1].points[0].yMm
        val negYFirst  = negOrigins[0].points[0].yMm
        assertTrue("-curve end origin above middle: first y ($negYFirst) < middle y ($negYMiddle)",
            negYFirst < negYMiddle)
    }

    @Test
    fun negativeCurveBaselineOriginsAreYNegatedVsPositive() {
        // The glyph BASELINE origin (the point that sits on the arc) for −curve is the exact
        // y-negation of the origin for +curve, and the x positions are the same.  We recover
        // the origin by noting that the glyph-local centroid of the 4-point square is (5, −5),
        // and after rigid rotation by φ we get the rotated local offset.  For |curve| = 0.5
        // and a 3-glyph run we compare origins by using a glyph whose local centroid is at (0,0),
        // so that the result centroid IS the origin directly.
        //
        // Use a degenerate single-point glyph at (0, 0) as a baseline-origin probe.
        val probe = GlyphRun(listOf(Polyline(listOf(Pt(0.0, 0.0)), closed = false)), advanceMm = 10.0)
        val glyphs = listOf(probe, probe, probe)

        val pos = TextArc.layoutOnArc(glyphs, curve =  0.5)
        val neg = TextArc.layoutOnArc(glyphs, curve = -0.5)

        for (i in 0 until 3) {
            val px = pos[i].points[0].xMm
            val py = pos[i].points[0].yMm
            val nx = neg[i].points[0].xMm
            val ny = neg[i].points[0].yMm
            assertEquals("glyph $i baseline origin x", px, nx,  1e-6)
            assertEquals("glyph $i baseline origin y", -py, ny, 1e-6)
        }
    }

    @Test
    fun negativeCurveMiddleBaselineOriginLowerThanEnds() {
        // For negative curve (downward arc) the MIDDLE glyph's baseline origin is below
        // (larger y) the end glyphs' origins, because the arc bends downward.
        val probe = GlyphRun(listOf(Polyline(listOf(Pt(0.0, 0.0)), closed = false)), advanceMm = 10.0)
        val result = TextArc.layoutOnArc(listOf(probe, probe, probe), curve = -0.5)

        val yFirst  = result[0].points[0].yMm
        val yMiddle = result[1].points[0].yMm
        val yLast   = result[2].points[0].yMm

        assertTrue("−curve: middle origin y ($yMiddle) must be > first ($yFirst)",  yMiddle > yFirst)
        assertTrue("−curve: middle origin y ($yMiddle) must be > last ($yLast)",    yMiddle > yLast)
    }

    // ---------------------------------------------------------------------------
    // Edge cases
    // ---------------------------------------------------------------------------

    @Test
    fun emptyGlyphListReturnsEmpty() {
        val result = TextArc.layoutOnArc(emptyList(), curve = 0.5)
        assertTrue(result.isEmpty())
    }

    @Test
    fun singleGlyphAtNonZeroCurveDoesNotCrash() {
        // A single glyph must not crash; its centroid must be finite.
        val result = TextArc.layoutOnArc(listOf(square()), curve = 0.7)
        assertEquals(1, result.size)
        val c = centroid(result, 0)
        assertTrue("x must be finite", c.xMm.isFinite())
        assertTrue("y must be finite", c.yMm.isFinite())
    }

    @Test
    fun closedFlagIsPreservedAfterArcLayout() {
        val openGlyph   = GlyphRun(listOf(Polyline(listOf(Pt(0.0, 0.0), Pt(5.0, 0.0)), closed = false)), 10.0)
        val closedGlyph = GlyphRun(listOf(Polyline(listOf(Pt(0.0, 0.0), Pt(5.0, 0.0), Pt(2.5, -5.0)), closed = true)), 10.0)

        val result = TextArc.layoutOnArc(listOf(openGlyph, closedGlyph), curve = 0.3)

        assertEquals(false, result[0].closed)
        assertEquals(true,  result[1].closed)
    }
}
