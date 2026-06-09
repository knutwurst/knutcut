package de.knutwurst.knutcut.svgcore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** Unit tests for [EnvelopeWarp.bilinear]. */
class EnvelopeWarpTest {

    private val EPS = 1e-9

    // Helpers to build a 1x1 unit-bounds source.
    private fun bounds(x0: Double = 0.0, y0: Double = 0.0, x1: Double = 100.0, y1: Double = 50.0) =
        Bounds(x0, y0, x1, y1)

    private fun pt(x: Double, y: Double) = Pt(x, y)

    // ---------------------------------------------------------------------------
    // Identity: corners equal to the source bounding box reproduce the source.
    // ---------------------------------------------------------------------------

    @Test
    fun identityWarpLeavesSourceUnchanged() {
        val b = bounds(0.0, 0.0, 100.0, 50.0)
        // Corners matching the bounding box exactly.
        val tl = pt(0.0, 0.0)
        val tr = pt(100.0, 0.0)
        val br = pt(100.0, 50.0)
        val bl = pt(0.0, 50.0)

        val source = listOf(
            Polyline(listOf(pt(0.0, 0.0), pt(50.0, 25.0), pt(100.0, 50.0)), closed = false),
            Polyline(listOf(pt(10.0, 10.0), pt(90.0, 40.0)), closed = true),
        )

        val result = EnvelopeWarp.bilinear(source, b, tl, tr, br, bl)

        assertEquals(source.size, result.size)
        for (pi in source.indices) {
            val srcPoly = source[pi]
            val resPoly = result[pi]
            assertEquals("closed flag preserved poly $pi", srcPoly.closed, resPoly.closed)
            for (k in srcPoly.points.indices) {
                assertEquals("x poly $pi pt $k", srcPoly.points[k].xMm, resPoly.points[k].xMm, EPS)
                assertEquals("y poly $pi pt $k", srcPoly.points[k].yMm, resPoly.points[k].yMm, EPS)
            }
        }
    }

    // ---------------------------------------------------------------------------
    // Corner hit test: source corners map exactly to envelope corners.
    // ---------------------------------------------------------------------------

    @Test
    fun sourceCornersMapToEnvelopeCorners() {
        val b = bounds(0.0, 0.0, 100.0, 50.0)
        val tl = pt(10.0, 5.0)
        val tr = pt(120.0, 8.0)
        val br = pt(115.0, 60.0)
        val bl = pt(5.0, 55.0)

        val source = listOf(
            Polyline(
                listOf(pt(0.0, 0.0), pt(100.0, 0.0), pt(100.0, 50.0), pt(0.0, 50.0)),
                closed = false,
            )
        )

        val result = EnvelopeWarp.bilinear(source, b, tl, tr, br, bl).first().points

        assertEquals(tl.xMm, result[0].xMm, EPS)
        assertEquals(tl.yMm, result[0].yMm, EPS)
        assertEquals(tr.xMm, result[1].xMm, EPS)
        assertEquals(tr.yMm, result[1].yMm, EPS)
        assertEquals(br.xMm, result[2].xMm, EPS)
        assertEquals(br.yMm, result[2].yMm, EPS)
        assertEquals(bl.xMm, result[3].xMm, EPS)
        assertEquals(bl.yMm, result[3].yMm, EPS)
    }

    // ---------------------------------------------------------------------------
    // Centre of the source maps to the average of the four corners.
    // ---------------------------------------------------------------------------

    @Test
    fun centreMapsToAverageOfFourCorners() {
        val b = bounds(0.0, 0.0, 100.0, 100.0)
        val tl = pt(0.0, 0.0)
        val tr = pt(80.0, 20.0)
        val br = pt(90.0, 90.0)
        val bl = pt(10.0, 70.0)

        // Centre of the bounding box: u=0.5, v=0.5
        val source = listOf(Polyline(listOf(pt(50.0, 50.0)), closed = false))
        val result = EnvelopeWarp.bilinear(source, b, tl, tr, br, bl).first().points.first()

        val expectedX = (tl.xMm + tr.xMm + br.xMm + bl.xMm) / 4.0
        val expectedY = (tl.yMm + tr.yMm + br.yMm + bl.yMm) / 4.0
        assertEquals(expectedX, result.xMm, EPS)
        assertEquals(expectedY, result.yMm, EPS)
    }

    // ---------------------------------------------------------------------------
    // Skew / trapezoid: known-point mapping.
    // ---------------------------------------------------------------------------

    @Test
    fun trapezoidMapsKnownPointsCorrectly() {
        // Envelope: TL and TR at y=0, but BL slides right by 30mm (trapezoid).
        // Source bounds: 100x50.
        val b = bounds(0.0, 0.0, 100.0, 50.0)
        val tl = pt(0.0, 0.0)
        val tr = pt(100.0, 0.0)
        val br = pt(130.0, 50.0)
        val bl = pt(30.0, 50.0)

        // Mid-top: u=0.5, v=0 → (1-u)(1-v)*TL + u(1-v)*TR = 0.5*TL + 0.5*TR = (50,0)
        val midTop = Polyline(listOf(pt(50.0, 0.0)), closed = false)
        val resMidTop = EnvelopeWarp.bilinear(listOf(midTop), b, tl, tr, br, bl).first().points.first()
        assertEquals(50.0, resMidTop.xMm, EPS)
        assertEquals(0.0, resMidTop.yMm, EPS)

        // Mid-bottom: u=0.5, v=1 → 0.5*BR + 0.5*BL = (130+30)/2=80, y=50
        val midBot = Polyline(listOf(pt(50.0, 50.0)), closed = false)
        val resMidBot = EnvelopeWarp.bilinear(listOf(midBot), b, tl, tr, br, bl).first().points.first()
        assertEquals(80.0, resMidBot.xMm, EPS)
        assertEquals(50.0, resMidBot.yMm, EPS)
    }

    // ---------------------------------------------------------------------------
    // Closed flag preserved.
    // ---------------------------------------------------------------------------

    @Test
    fun closedFlagIsPreservedForAllPolylines() {
        val b = bounds(0.0, 0.0, 10.0, 10.0)
        val tl = pt(0.0, 0.0); val tr = pt(10.0, 0.0)
        val br = pt(10.0, 10.0); val bl = pt(0.0, 10.0)
        val source = listOf(
            Polyline(listOf(pt(1.0, 1.0), pt(9.0, 1.0)), closed = true),
            Polyline(listOf(pt(1.0, 5.0), pt(9.0, 5.0)), closed = false),
        )
        val result = EnvelopeWarp.bilinear(source, b, tl, tr, br, bl)
        assertEquals(true, result[0].closed)
        assertEquals(false, result[1].closed)
    }

    // ---------------------------------------------------------------------------
    // Degenerate bounds (zero width/height): no crash, u/v clamped to 0.
    // ---------------------------------------------------------------------------

    @Test
    fun zeroWidthBoundsDoesNotCrash() {
        val b = Bounds(5.0, 0.0, 5.0, 10.0)  // width = 0
        val tl = pt(0.0, 0.0); val tr = pt(20.0, 0.0)
        val br = pt(20.0, 10.0); val bl = pt(0.0, 10.0)
        val source = listOf(Polyline(listOf(pt(5.0, 5.0)), closed = false))
        // Should not throw; u=0 so mapped x = TL.x weighted.
        val result = EnvelopeWarp.bilinear(source, b, tl, tr, br, bl)
        assertEquals(1, result.first().points.size)
    }

    @Test
    fun zeroHeightBoundsDoesNotCrash() {
        val b = Bounds(0.0, 3.0, 10.0, 3.0)  // height = 0
        val tl = pt(0.0, 0.0); val tr = pt(10.0, 0.0)
        val br = pt(10.0, 10.0); val bl = pt(0.0, 10.0)
        val source = listOf(Polyline(listOf(pt(5.0, 3.0)), closed = false))
        val result = EnvelopeWarp.bilinear(source, b, tl, tr, br, bl)
        assertEquals(1, result.first().points.size)
    }
}
