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

    // -----------------------------------------------------------------------
    // Bug #6: smoothToPath 2-point closed path — tangent fallback in the
    //         general Catmull-Rom loop (triggered for >=3 points when tangent
    //         length is near zero because prev == next).
    // -----------------------------------------------------------------------

    @Test
    fun smoothToPathThreePointClosedVerticalDoesNotProduceFigureEight() {
        // Three nearly-vertical points in a closed path so that for the middle node
        // prev and next are the same (both neighbours are the endpoints, which are
        // the same y distance away). The Catmull-Rom tangent (next - prev)/2 could
        // approach zero if next ≈ prev, giving the (1,0) fallback.
        //
        // Here: (0,0), (0,10), (0,20) closed.  For node 0:
        //   prev = pts[2] = (0,20), next = pts[1] = (0,10)
        //   tx = (0-0)/2 = 0, ty = (10-20)/2 = -5  → tLen > 0, tangent = (0,-1)
        // For node 2: prev = (0,10), next = (0,0) → tx=0, ty=-5 → tangent = (0,-1)
        // No fallback needed in this case, but with symmetry the x handles should be 0.
        // We just verify no large x deviation.
        val pts = listOf(Pt(0.0, 0.0), Pt(0.0, 10.0), Pt(0.0, 20.0))
        val path = smoothToPath(pts, closed = true)
        val poly = path.toPolyline(0.05)
        val maxX = poly.points.maxOf { kotlin.math.abs(it.xMm) }
        assertTrue("vertical closed path has large x deviation ($maxX mm)", maxX < 2.0)
    }

    @Test
    fun smoothToPathTwoCollinearPointsClosedZeroTangentFallback() {
        // Force the zero-tangent fallback: 3 collinear points where for node 0,
        // prev == next → tangent = (0,0). Should fall back to the segment direction
        // (towards the next neighbour) rather than (1,0).
        //
        // Arrange: points at (0,0), (0,5), (0,0) — the first and third are coincident,
        // making prev == curr == next for node 1 also. For node 0:
        //   prev = (0,0), next = (0,5) → tangent non-zero → ok
        // For a really zero-tangent case use three points in a right-angle where the
        // middle node has equidistant prev and next in opposite direction — symmetry
        // makes their average zero:
        //   pts = (0,0), (10,0), (0,0)   closed
        // Node 1 (10,0): prev=(0,0), next=(0,0) → tx=(0-0)/2=0, ty=0 → zero tangent
        // Fallback (1,0) would produce handleOut at (10+dist,0) → handle in +x direction.
        // Correct fallback: unit vec from node to next neighbour = (0-10,0-0) = (-10,0) → (-1,0)
        // OR unit vec towards any neighbour that is not the anchor itself.
        // Either way, a handle pointing left (-x) avoids the figure-8 produced by (1,0).
        val pts = listOf(Pt(0.0, 0.0), Pt(10.0, 0.0), Pt(0.0, 0.0))
        val path = smoothToPath(pts, closed = true)
        // The middle node (10,0) with zero Catmull-Rom tangent must get a handle that
        // does NOT place the handleOut at a position with xMm > 10 (which would be the
        // (1,0) fallback direction pointing to the right — away from both neighbours).
        val midNode = path.nodes[1]
        if (midNode.handleOut != null) {
            assertTrue(
                "handleOut of zero-tangent middle node should point toward neighbours (x <= anchor.x), got ${midNode.handleOut}",
                midNode.handleOut!!.xMm <= midNode.anchor.xMm + 1e-6
            )
        }
    }
}
