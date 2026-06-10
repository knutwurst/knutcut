package de.knutwurst.knutcut.svgcore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

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
    fun smoothToPathInteriorNodesAreSmoothWithEqualHandles() {
        // Uneven spacing (near neighbour on the left, far on the right): the two handles of the
        // interior node must still come out equal length, and the node must be smooth. Open-path
        // endpoints stay corners with a single handle.
        val pts = listOf(Pt(0.0, 0.0), Pt(10.0, 0.0), Pt(40.0, 0.0))
        val path = smoothToPath(pts, closed = false)
        val mid = path.nodes[1]
        assertTrue("interior node must be smooth", mid.smooth)
        val dIn  = hypot(mid.handleIn!!.xMm - mid.anchor.xMm,  mid.handleIn!!.yMm - mid.anchor.yMm)
        val dOut = hypot(mid.handleOut!!.xMm - mid.anchor.xMm, mid.handleOut!!.yMm - mid.anchor.yMm)
        assertEquals("interior handles must be equal length", dIn, dOut, 1e-9)
        assertEquals("open-path endpoint stays a corner", false, path.nodes.first().smooth)
    }

    @Test
    fun smoothToPathKeepsSharpCornersCrisp() {
        // A square: every 90° turn must stay a crisp corner (no handles), not be rounded off.
        val square = listOf(Pt(0.0, 0.0), Pt(30.0, 0.0), Pt(30.0, 30.0), Pt(0.0, 30.0))
        val path = smoothToPath(square, closed = true)
        for (node in path.nodes) {
            assertEquals("square corner must stay sharp", false, node.smooth)
            assertTrue("sharp corner has no in-handle", node.handleIn == null)
            assertTrue("sharp corner has no out-handle", node.handleOut == null)
        }
    }

    @Test
    fun smoothToPathKeepsGentleTurnsSmooth() {
        // A regular octagon: 45° turns are gentle, so every node stays smooth (rounded), not a corner.
        val n = 8
        val octagon = (0 until n).map { val a = 2 * PI * it / n; Pt(20.0 * cos(a), 20.0 * sin(a)) }
        val path = smoothToPath(octagon, closed = true)
        assertTrue("gentle turns must stay smooth", path.nodes.all { it.smooth })
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

    // -----------------------------------------------------------------------
    // simplifyToBudget
    // -----------------------------------------------------------------------

    /** 500-point sampled circle at radius 40 mm. */
    private fun sampleCircle(radius: Double = 40.0, n: Int = 500): List<Pt> =
        (0 until n).map { i ->
            val a = 2 * PI * i / n
            Pt(radius * cos(a), radius * sin(a))
        }

    @Test
    fun budgetCircle500ptsReducesToAtMost40Nodes() {
        val pts = sampleCircle()
        val result = simplifyToBudget(pts, closed = true, targetNodes = 40)
        assertTrue(
            "Expected ≤ 40 nodes for a 500-pt circle, got ${result.nodes.size}",
            result.nodes.size <= 40,
        )
    }

    @Test
    fun budgetCircleNodesStayNearRadius() {
        val radius = 40.0
        val pts = sampleCircle(radius)
        val result = simplifyToBudget(pts, closed = true, targetNodes = 40)
        // Every anchor node should be within ~2 mm of the circle radius.
        for (node in result.nodes) {
            val r = hypot(node.anchor.xMm, node.anchor.yMm)
            assertTrue(
                "Node at (${node.anchor.xMm}, ${node.anchor.yMm}) is ${kotlin.math.abs(r - radius)} mm off the circle",
                kotlin.math.abs(r - radius) < 2.0,
            )
        }
    }

    @Test
    fun budgetSimple4PointSquarePreservesShape() {
        // A 4-point square is well under the default budget; it must come back smoothed
        // with the same 4 anchors and with all anchors within tolerance of the original corners.
        val side = 30.0
        val pts = listOf(Pt(0.0, 0.0), Pt(side, 0.0), Pt(side, side), Pt(0.0, side))
        val result = simplifyToBudget(pts, closed = true, targetNodes = 40)
        assertTrue("Square: expected ≤ 40 nodes, got ${result.nodes.size}", result.nodes.size <= 40)
        // Each original corner should be represented by an anchor within 0.5 mm.
        for (corner in pts) {
            val closest = result.nodes.minByOrNull { hypot(it.anchor.xMm - corner.xMm, it.anchor.yMm - corner.yMm) }!!
            val dist = hypot(closest.anchor.xMm - corner.xMm, closest.anchor.yMm - corner.yMm)
            assertTrue("Corner $corner not preserved (closest anchor $dist mm away)", dist < 0.5)
        }
    }

    @Test
    fun budgetAlreadyUnderBudgetSmoothedWithoutOverSimplifying() {
        // A small zig-zag with fewer points than the budget.
        // The result must have the same number of nodes as input points (no extra dropping).
        val pts = listOf(
            Pt(0.0, 0.0), Pt(5.0, 3.0), Pt(10.0, 0.0),
            Pt(15.0, 3.0), Pt(20.0, 0.0),
        )
        val result = simplifyToBudget(pts, closed = false, targetNodes = 40)
        assertEquals(
            "Small zig-zag under budget: node count should equal input size",
            pts.size, result.nodes.size,
        )
    }

    @Test
    fun budgetClosedFlagCarriedThrough() {
        val pts = sampleCircle(n = 50)
        val openResult   = simplifyToBudget(pts, closed = false,  targetNodes = 40)
        val closedResult = simplifyToBudget(pts, closed = true,   targetNodes = 40)
        assertEquals(false, openResult.closed)
        assertEquals(true,  closedResult.closed)
    }

    @Test
    fun budgetEmptyInputDoesNotCrash() {
        val result = simplifyToBudget(emptyList(), closed = false)
        assertTrue(result.nodes.isEmpty())
    }

    @Test
    fun budgetSinglePointDoesNotCrash() {
        val result = simplifyToBudget(listOf(Pt(1.0, 2.0)), closed = false)
        assertEquals(1, result.nodes.size)
        assertEquals(Pt(1.0, 2.0), result.nodes.first().anchor)
    }

    @Test
    fun budgetDefaultKeepsNodeCountSmall() {
        // The default budget is deliberately low so a freehand shape is editable. A 500-pt circle
        // should land at a single-digit / low-teens node count, not dozens.
        val pts = sampleCircle()
        val result = simplifyToBudget(pts, closed = true)
        assertTrue(
            "Default budget should give few nodes for a circle, got ${result.nodes.size}",
            result.nodes.size <= 14,
        )
    }

    // -----------------------------------------------------------------------
    // looksClosed
    // -----------------------------------------------------------------------

    @Test
    fun looksClosedTrueWhenEndsMeetRelativeToSize() {
        // A loop drawn back to near the start: the end-to-end gap (≈25 mm) is past the absolute
        // tolerance but well within the relative fraction of the ~70 mm bbox diagonal, so the
        // relative branch closes it.
        val loop = listOf(
            Pt(0.0, 0.0), Pt(50.0, 0.0), Pt(50.0, 50.0), Pt(0.0, 50.0), Pt(18.0, 17.0),
        )
        assertTrue("near-closed loop should read as closed", looksClosed(loop))
    }

    @Test
    fun looksClosedFalseForOpenLineOrArc() {
        // An open zig-zag: ends sit at opposite extremes (gap ≈ full extent).
        val open = listOf(Pt(0.0, 0.0), Pt(20.0, 15.0), Pt(40.0, 0.0), Pt(60.0, 15.0))
        assertEquals(false, looksClosed(open))
    }

    @Test
    fun looksClosedFalseForTooFewPoints() {
        assertEquals(false, looksClosed(listOf(Pt(0.0, 0.0), Pt(1.0, 1.0))))
        assertEquals(false, looksClosed(emptyList()))
    }

    @Test
    fun looksClosedTrueForTinyGapRegardlessOfSize() {
        // A long, narrow shape whose ends are within the absolute tolerance still counts as closed.
        val pts = listOf(Pt(0.0, 0.0), Pt(200.0, 5.0), Pt(200.0, 10.0), Pt(2.0, 6.0))
        assertTrue("ends within the absolute tolerance should read as closed", looksClosed(pts))
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
