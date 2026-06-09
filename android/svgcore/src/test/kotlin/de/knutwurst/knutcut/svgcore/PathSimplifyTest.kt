package de.knutwurst.knutcut.svgcore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class PathSimplifyTest {

    // -----------------------------------------------------------------------
    // simplifyRdp
    // -----------------------------------------------------------------------

    @Test
    fun rdpKeepsEndpointsAlways() {
        val pts = listOf(Pt(0.0, 0.0), Pt(5.0, 0.1), Pt(10.0, 0.0))
        val result = simplifyRdp(pts, 1.0)
        assertEquals(Pt(0.0, 0.0), result.first())
        assertEquals(Pt(10.0, 0.0), result.last())
    }

    @Test
    fun rdpCollinearPointsCollapseToEndpoints() {
        // Five points on a straight horizontal line: only the endpoints survive.
        val pts = (0..4).map { Pt(it * 10.0, 0.0) }
        val result = simplifyRdp(pts, 0.5)
        assertEquals(2, result.size)
        assertEquals(pts.first(), result.first())
        assertEquals(pts.last(), result.last())
    }

    @Test
    fun rdpCollinearWithTinyJitterCollapsesBelowTolerance() {
        // Points nearly collinear (max deviation 0.1): should collapse below tol=0.5.
        val pts = listOf(Pt(0.0, 0.0), Pt(5.0, 0.1), Pt(10.0, 0.0))
        val result = simplifyRdp(pts, 0.5)
        assertEquals(2, result.size)
    }

    @Test
    fun rdpRespectsToleranceKeepsSignificantPeak() {
        // A peak point that is 5 mm off the chord must survive with tol=1.0.
        val pts = listOf(Pt(0.0, 0.0), Pt(50.0, 5.0), Pt(100.0, 0.0))
        val result = simplifyRdp(pts, 1.0)
        assertEquals(3, result.size)
        assertTrue(result.any { it.xMm == 50.0 && it.yMm == 5.0 })
    }

    @Test
    fun rdpTwoPointsReturnedUnchanged() {
        val pts = listOf(Pt(0.0, 0.0), Pt(10.0, 10.0))
        assertEquals(pts, simplifyRdp(pts, 1.0))
    }

    @Test
    fun rdpSinglePointReturnedUnchanged() {
        val pts = listOf(Pt(3.0, 4.0))
        assertEquals(pts, simplifyRdp(pts, 1.0))
    }

    @Test
    fun rdpEmptyListReturnedUnchanged() {
        val pts = emptyList<Pt>()
        assertEquals(pts, simplifyRdp(pts, 1.0))
    }

    @Test
    fun rdpPreservesOrderOfKeptPoints() {
        // An L-shaped path: corner must be kept, inner collinear points on each arm dropped.
        val pts = listOf(
            Pt(0.0, 0.0), Pt(10.0, 0.0), Pt(20.0, 0.0),   // horizontal arm
            Pt(20.0, 10.0), Pt(20.0, 20.0),                 // vertical arm
        )
        val result = simplifyRdp(pts, 0.5)
        // Must keep (0,0), (20,0) corner, (20,20); not necessarily the intermediate points.
        assertTrue(result.first() == Pt(0.0, 0.0))
        assertTrue(result.last()  == Pt(20.0, 20.0))
        assertTrue(result.any { it.xMm == 20.0 && it.yMm == 0.0 }) // the corner
    }

    // -----------------------------------------------------------------------
    // smoothToPath
    // -----------------------------------------------------------------------

    @Test
    fun smoothToPathNodeCountEqualsInputCountOpenPath() {
        val pts = listOf(Pt(0.0, 0.0), Pt(10.0, 5.0), Pt(20.0, 0.0), Pt(30.0, 5.0))
        val path = smoothToPath(pts, closed = false)
        assertEquals(pts.size, path.nodes.size)
    }

    @Test
    fun smoothToPathFlattenedPolylineStaysWithinToleranceOfInputPoints() {
        // A four-point stroke; every input point should have a flattened polyline point nearby.
        val pts = listOf(Pt(0.0, 0.0), Pt(20.0, 30.0), Pt(40.0, 10.0), Pt(60.0, 20.0))
        val path = smoothToPath(pts, closed = false)
        val poly = path.toPolyline(0.1)
        val maxGap = 3.0   // mm — generous but meaningful; Catmull-Rom passes through control points
        for (input in pts) {
            val closest = poly.points.minByOrNull { hypot(it.xMm - input.xMm, it.yMm - input.yMm) }!!
            assertTrue(
                "Input point $input not represented in flattened polyline (gap=${hypot(closest.xMm - input.xMm, closest.yMm - input.yMm)})",
                hypot(closest.xMm - input.xMm, closest.yMm - input.yMm) < maxGap
            )
        }
    }

    @Test
    fun smoothToPathNearStraightStrokeStaysNearStraight() {
        // A nearly-horizontal stroke: the flattened path should stay within 1 mm of y=0.
        val pts = (0..5).map { Pt(it * 10.0, 0.0) }
        val path = smoothToPath(pts, closed = false)
        val poly = path.toPolyline(0.1)
        for (p in poly.points) {
            assertTrue("Point off straight line: $p", kotlin.math.abs(p.yMm) < 1.0)
        }
    }

    @Test
    fun smoothToPathClosedFlagPropagated() {
        val pts = listOf(Pt(0.0, 0.0), Pt(10.0, 0.0), Pt(5.0, 8.0))
        val open   = smoothToPath(pts, closed = false)
        val closed = smoothToPath(pts, closed = true)
        assertEquals(false, open.closed)
        assertEquals(true,  closed.closed)
    }

    @Test
    fun smoothToPathTwoPointsReturnsStraightLine() {
        val pts = listOf(Pt(0.0, 0.0), Pt(10.0, 0.0))
        val path = smoothToPath(pts, closed = false)
        assertEquals(2, path.nodes.size)
        // Straight line: both handles null.
        assertTrue(path.nodes.first().handleIn  == null)
        assertTrue(path.nodes.last().handleOut  == null)
    }

    @Test
    fun smoothToPathSinglePointReturnsOneNode() {
        val path = smoothToPath(listOf(Pt(5.0, 5.0)), closed = false)
        assertEquals(1, path.nodes.size)
        assertEquals(Pt(5.0, 5.0), path.nodes.first().anchor)
    }

    @Test
    fun smoothToPathEmptyReturnsEmpty() {
        val path = smoothToPath(emptyList(), closed = false)
        assertTrue(path.nodes.isEmpty())
    }
}
