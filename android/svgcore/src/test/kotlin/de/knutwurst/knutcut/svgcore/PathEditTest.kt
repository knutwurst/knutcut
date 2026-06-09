package de.knutwurst.knutcut.svgcore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.PI

class PathEditTest {

    // -----------------------------------------------------------------------
    // Matrix.inverse
    // -----------------------------------------------------------------------

    private fun assertPtEq(expected: Pt, actual: Pt, eps: Double = 1e-9) {
        assertEquals("x", expected.xMm, actual.xMm, eps)
        assertEquals("y", expected.yMm, actual.yMm, eps)
    }

    @Test
    fun matrixInverseTranslate() {
        val m = Matrix.translate(5.0, -3.0)
        val p = Pt(2.0, 7.0)
        assertPtEq(p, m.inverse()!!.apply(m.apply(p)))
    }

    @Test
    fun matrixInverseScale() {
        val m = Matrix.scale(2.0, 0.5)
        val p = Pt(3.0, 4.0)
        assertPtEq(p, m.inverse()!!.apply(m.apply(p)))
    }

    @Test
    fun matrixInverseRotate() {
        val m = Matrix.rotate(37.0)
        val p = Pt(1.0, 2.0)
        assertPtEq(p, m.inverse()!!.apply(m.apply(p)), 1e-9)
    }

    @Test
    fun matrixInverseComposed() {
        val m = Matrix.translate(10.0, -5.0) * Matrix.scale(3.0, 2.0) * Matrix.rotate(45.0)
        val p = Pt(-4.0, 6.0)
        assertPtEq(p, m.inverse()!!.apply(m.apply(p)), 1e-9)
    }

    @Test
    fun matrixInverseIdentity() {
        val m = Matrix.IDENTITY
        val inv = m.inverse()
        assertNotNull(inv)
        assertPtEq(Pt(1.0, 2.0), inv!!.apply(Pt(1.0, 2.0)))
    }

    @Test
    fun matrixInverseDegenerateReturnsNull() {
        // Zero-scale matrix has determinant 0.
        val m = Matrix.scale(0.0, 1.0)
        assertNull(m.inverse())
    }

    @Test
    fun matrixInverseInverseIsOriginal() {
        val m = Matrix.translate(3.0, 1.0) * Matrix.rotate(30.0)
        val inv = m.inverse()!!
        val p = Pt(5.0, -2.0)
        assertPtEq(p, inv.inverse()!!.apply(inv.apply(p)), 1e-9)
    }

    // -----------------------------------------------------------------------
    // PathNode smooth field — backward compatibility
    // -----------------------------------------------------------------------

    @Test
    fun pathNodeDefaultSmoothIsFalse() {
        val n = PathNode(Pt(0.0, 0.0))
        assertEquals(false, n.smooth)
    }

    @Test
    fun pathNodeSmoothFieldPreservedInCopy() {
        val n = PathNode(Pt(0.0, 0.0), smooth = true)
        assertEquals(true, n.copy().smooth)
        assertEquals(false, n.copy(smooth = false).smooth)
    }

    // -----------------------------------------------------------------------
    // moveAnchor
    // -----------------------------------------------------------------------

    @Test
    fun moveAnchorMovesHandlesByDelta() {
        val node = PathNode(
            anchor    = Pt(5.0, 5.0),
            handleIn  = Pt(3.0, 5.0),
            handleOut = Pt(7.0, 5.0),
        )
        val path = EditablePath(listOf(node, PathNode(Pt(20.0, 5.0))))
        val moved = path.moveAnchor(0, Pt(10.0, 10.0))
        val n = moved.nodes[0]
        // Anchor moved by (+5, +5); handles follow.
        assertEquals(10.0, n.anchor.xMm, 1e-9)
        assertEquals(10.0, n.anchor.yMm, 1e-9)
        assertEquals(8.0,  n.handleIn!!.xMm,  1e-9)   // 3 + 5
        assertEquals(10.0, n.handleIn!!.yMm,  1e-9)   // 5 + 5
        assertEquals(12.0, n.handleOut!!.xMm, 1e-9)   // 7 + 5
        assertEquals(10.0, n.handleOut!!.yMm, 1e-9)   // 5 + 5
    }

    @Test
    fun moveAnchorWithNullHandlesDoesNotCrash() {
        val path = EditablePath(listOf(PathNode(Pt(0.0, 0.0)), PathNode(Pt(10.0, 0.0))))
        val moved = path.moveAnchor(0, Pt(1.0, 2.0))
        assertNull(moved.nodes[0].handleIn)
        assertNull(moved.nodes[0].handleOut)
    }

    @Test
    fun moveAnchorDoesNotAffectOtherNodes() {
        val p1 = PathNode(Pt(0.0, 0.0))
        val p2 = PathNode(Pt(10.0, 0.0))
        val p3 = PathNode(Pt(20.0, 0.0))
        val path = EditablePath(listOf(p1, p2, p3))
        val moved = path.moveAnchor(1, Pt(10.0, 5.0))
        assertEquals(Pt(0.0, 0.0),  moved.nodes[0].anchor)
        assertEquals(Pt(20.0, 0.0), moved.nodes[2].anchor)
    }

    // -----------------------------------------------------------------------
    // moveHandle — corner node (no mirroring)
    // -----------------------------------------------------------------------

    @Test
    fun moveHandleCornerOutDoesNotAffectIn() {
        val node = PathNode(Pt(5.0, 5.0), handleIn = Pt(2.0, 5.0), handleOut = Pt(8.0, 5.0))
        val path = EditablePath(listOf(node, PathNode(Pt(20.0, 5.0))))
        val moved = path.moveHandle(0, HandleSide.OUT, Pt(10.0, 5.0))
        // handleOut moved to (10, 5), handleIn unchanged
        assertEquals(10.0, moved.nodes[0].handleOut!!.xMm, 1e-9)
        assertEquals(2.0,  moved.nodes[0].handleIn!!.xMm,  1e-9)
    }

    @Test
    fun moveHandleCornerInDoesNotAffectOut() {
        val node = PathNode(Pt(5.0, 5.0), handleIn = Pt(2.0, 5.0), handleOut = Pt(8.0, 5.0))
        val path = EditablePath(listOf(node, PathNode(Pt(20.0, 5.0))))
        val moved = path.moveHandle(0, HandleSide.IN, Pt(0.0, 5.0))
        assertEquals(0.0, moved.nodes[0].handleIn!!.xMm,  1e-9)
        assertEquals(8.0, moved.nodes[0].handleOut!!.xMm, 1e-9)
    }

    // -----------------------------------------------------------------------
    // moveHandle — smooth node (handles mirror)
    // -----------------------------------------------------------------------

    @Test
    fun moveHandleSmoothOutMirrorsIn() {
        // Smooth node at origin; in-handle at (-3,0), out at (3,0).
        // Move out to (6,0); in should mirror to (-6,0) direction, keeping its original distance.
        val anchor = Pt(0.0, 0.0)
        val node = PathNode(anchor, handleIn = Pt(-3.0, 0.0), handleOut = Pt(3.0, 0.0), smooth = true)
        val path = EditablePath(listOf(PathNode(Pt(-10.0, 0.0)), node, PathNode(Pt(10.0, 0.0))))
        val moved = path.moveHandle(1, HandleSide.OUT, Pt(6.0, 0.0))
        val n = moved.nodes[1]
        // Out moved to (6, 0). In should be collinear: direction (-1,0), same distance as before (3 mm).
        assertEquals(6.0,  n.handleOut!!.xMm, 1e-9)
        assertEquals(0.0,  n.handleOut!!.yMm, 1e-9)
        assertEquals(-3.0, n.handleIn!!.xMm,  1e-9)  // distance preserved at 3
        assertEquals(0.0,  n.handleIn!!.yMm,  1e-9)
    }

    @Test
    fun moveHandleSmoothInMirrorsOut() {
        val anchor = Pt(0.0, 0.0)
        val node = PathNode(anchor, handleIn = Pt(-4.0, 0.0), handleOut = Pt(4.0, 0.0), smooth = true)
        val path = EditablePath(listOf(PathNode(Pt(-10.0, 0.0)), node, PathNode(Pt(10.0, 0.0))))
        val moved = path.moveHandle(1, HandleSide.IN, Pt(-8.0, 0.0))
        val n = moved.nodes[1]
        // In moved to (-8, 0). Out should be collinear at (+1,0) direction, original distance 4 mm.
        assertEquals(-8.0, n.handleIn!!.xMm,  1e-9)
        assertEquals(0.0,  n.handleIn!!.yMm,  1e-9)
        assertEquals(4.0,  n.handleOut!!.xMm, 1e-9)
        assertEquals(0.0,  n.handleOut!!.yMm, 1e-9)
    }

    @Test
    fun moveHandleSmoothMirrorPreservesCollinearity() {
        // After moving the out-handle, in-handle must be collinear with out-handle through anchor.
        val anchor = Pt(5.0, 5.0)
        val node = PathNode(anchor, handleIn = Pt(2.0, 3.0), handleOut = Pt(8.0, 7.0), smooth = true)
        val path = EditablePath(listOf(PathNode(Pt(0.0, 0.0)), node, PathNode(Pt(15.0, 10.0))))
        val newOut = Pt(9.0, 6.0)
        val moved = path.moveHandle(1, HandleSide.OUT, newOut)
        val n = moved.nodes[1]
        val hIn = n.handleIn!!
        val hOut = n.handleOut!!
        // Collinear: anchor, hIn, hOut all on one line.
        // Cross product (anchor->out) x (anchor->in) should be ~0.
        val ox = hOut.xMm - anchor.xMm; val oy = hOut.yMm - anchor.yMm
        val ix = hIn.xMm  - anchor.xMm; val iy = hIn.yMm  - anchor.yMm
        val cross = ox * iy - oy * ix
        assertEquals(0.0, cross, 1e-9)
        // And hIn should be on the opposite side of the anchor from hOut.
        val dot = ox * ix + oy * iy
        assertTrue("handles must point in opposite directions (dot < 0)", dot < 0)
    }

    // -----------------------------------------------------------------------
    // setSmooth
    // -----------------------------------------------------------------------

    @Test
    fun setSmoothTrueMakesHandlesCollinear() {
        // Handles that are not collinear: after setSmooth they should be.
        val anchor = Pt(5.0, 5.0)
        val node = PathNode(anchor, handleIn = Pt(3.0, 7.0), handleOut = Pt(8.0, 3.0))
        val path = EditablePath(listOf(PathNode(Pt(0.0, 0.0)), node, PathNode(Pt(15.0, 0.0))))
        val smoothed = path.setSmooth(1, true)
        val n = smoothed.nodes[1]
        assertTrue(n.smooth)
        val ox = n.handleOut!!.xMm - anchor.xMm; val oy = n.handleOut!!.yMm - anchor.yMm
        val ix = n.handleIn!!.xMm  - anchor.xMm; val iy = n.handleIn!!.yMm  - anchor.yMm
        val cross = ox * iy - oy * ix
        assertEquals(0.0, cross, 1e-6)
    }

    @Test
    fun setSmoothFalseLeavesHandlesUnchanged() {
        val anchor = Pt(5.0, 5.0)
        val node = PathNode(anchor, handleIn = Pt(3.0, 5.0), handleOut = Pt(7.0, 5.0), smooth = true)
        val path = EditablePath(listOf(PathNode(Pt(0.0, 0.0)), node, PathNode(Pt(15.0, 0.0))))
        val cornered = path.setSmooth(1, false)
        val n = cornered.nodes[1]
        assertEquals(false, n.smooth)
        assertEquals(3.0, n.handleIn!!.xMm,  1e-9)
        assertEquals(7.0, n.handleOut!!.xMm, 1e-9)
    }

    @Test
    fun setSmoothPreservesHandleLengths() {
        val anchor = Pt(0.0, 0.0)
        // Non-collinear handles at known distances.
        val hIn  = Pt(-3.0, 0.0)  // distance 3
        val hOut = Pt( 2.0, 2.0)  // distance sqrt(8)
        val node = PathNode(anchor, handleIn = hIn, handleOut = hOut)
        val path = EditablePath(listOf(PathNode(Pt(-10.0, 0.0)), node, PathNode(Pt(10.0, 0.0))))
        val smoothed = path.setSmooth(1, true)
        val n = smoothed.nodes[1]
        val distIn  = hypot(n.handleIn!!.xMm - anchor.xMm,  n.handleIn!!.yMm - anchor.yMm)
        val distOut = hypot(n.handleOut!!.xMm - anchor.xMm, n.handleOut!!.yMm - anchor.yMm)
        assertEquals(3.0,            distIn,  1e-9)
        assertEquals(hypot(2.0, 2.0), distOut, 1e-9)
    }

    // -----------------------------------------------------------------------
    // deleteNode
    // -----------------------------------------------------------------------

    @Test
    fun deleteNodeRemovesNode() {
        val nodes = listOf(
            PathNode(Pt(0.0, 0.0)), PathNode(Pt(5.0, 0.0)), PathNode(Pt(10.0, 0.0))
        )
        val path = EditablePath(nodes)
        val after = path.deleteNode(1)
        assertEquals(2, after.nodes.size)
        assertEquals(Pt(0.0, 0.0),  after.nodes[0].anchor)
        assertEquals(Pt(10.0, 0.0), after.nodes[1].anchor)
    }

    @Test
    fun deleteNodeOpenNoOpAtMinimum() {
        val path = EditablePath(listOf(PathNode(Pt(0.0, 0.0)), PathNode(Pt(5.0, 0.0))))
        val after = path.deleteNode(0)
        assertEquals(2, after.nodes.size)  // no-op, already at minimum
    }

    @Test
    fun deleteNodeClosedNoOpAtMinimum() {
        val nodes = listOf(
            PathNode(Pt(0.0, 0.0)), PathNode(Pt(5.0, 0.0)), PathNode(Pt(2.5, 5.0))
        )
        val path = EditablePath(nodes, closed = true)
        val after = path.deleteNode(0)
        assertEquals(3, after.nodes.size)  // minimum for closed = 3
    }

    @Test
    fun deleteNodeClosedWithFourNodesSucceeds() {
        val nodes = listOf(
            PathNode(Pt(0.0, 0.0)), PathNode(Pt(5.0, 0.0)),
            PathNode(Pt(5.0, 5.0)), PathNode(Pt(0.0, 5.0))
        )
        val path = EditablePath(nodes, closed = true)
        val after = path.deleteNode(2)
        assertEquals(3, after.nodes.size)
    }

    // -----------------------------------------------------------------------
    // insertNode — shape preservation
    // -----------------------------------------------------------------------

    private fun polylineDist(a: Polyline, b: Polyline): Double {
        // Max distance between matched points after resampling — simplified: check against
        // nearest point instead.  We pick the first polyline's points and measure min-dist
        // to any point in the second, then take the max.
        return a.points.maxOf { pa ->
            b.points.minOf { pb -> hypot(pa.xMm - pb.xMm, pa.yMm - pb.yMm) }
        }
    }

    @Test
    fun insertNodeIncreaseCountByOne() {
        val nodes = listOf(
            PathNode(Pt(0.0, 0.0), handleOut = Pt(5.0, 10.0)),
            PathNode(Pt(20.0, 0.0), handleIn = Pt(15.0, 10.0)),
        )
        val path = EditablePath(nodes)
        val after = path.insertNode(0, 0.5)
        assertEquals(3, after.nodes.size)
    }

    @Test
    fun insertNodeCubicPreservesShape() {
        val tol = 0.1
        val nodes = listOf(
            PathNode(Pt(0.0, 0.0), handleOut = Pt(5.0, 20.0)),
            PathNode(Pt(20.0, 0.0), handleIn = Pt(15.0, 20.0)),
        )
        val path = EditablePath(nodes)
        val before = path.toPolyline(tol)
        val after  = path.insertNode(0, 0.5).toPolyline(tol)
        // Shape must be preserved within a tight bound.
        val d = polylineDist(before, after)
        assertTrue("shape changed by $d mm (threshold 0.5 mm)", d < 0.5)
    }

    @Test
    fun insertNodeStraightPreservesShape() {
        val nodes = listOf(PathNode(Pt(0.0, 0.0)), PathNode(Pt(10.0, 0.0)))
        val path = EditablePath(nodes)
        val after = path.insertNode(0, 0.5)
        assertEquals(3, after.nodes.size)
        // Mid-node should land at (5, 0).
        val mid = after.nodes[1]
        assertEquals(5.0, mid.anchor.xMm, 1e-9)
        assertEquals(0.0, mid.anchor.yMm, 1e-9)
        assertNull(mid.handleIn)
        assertNull(mid.handleOut)
    }

    @Test
    fun insertNodeAtTZeroGivesAnchorAtFrom() {
        val nodes = listOf(
            PathNode(Pt(0.0, 0.0), handleOut = Pt(3.0, 5.0)),
            PathNode(Pt(10.0, 0.0), handleIn = Pt(7.0, 5.0)),
        )
        val path = EditablePath(nodes)
        val after = path.insertNode(0, 0.0)
        // At t=0 the new anchor coincides with node[0].
        assertPtEq(Pt(0.0, 0.0), after.nodes[1].anchor, 1e-9)
    }

    @Test
    fun insertNodeAtTOneGivesAnchorAtTo() {
        val nodes = listOf(
            PathNode(Pt(0.0, 0.0), handleOut = Pt(3.0, 5.0)),
            PathNode(Pt(10.0, 0.0), handleIn = Pt(7.0, 5.0)),
        )
        val path = EditablePath(nodes)
        val after = path.insertNode(0, 1.0)
        // At t=1 the new anchor coincides with node[1].
        assertPtEq(Pt(10.0, 0.0), after.nodes[1].anchor, 1e-9)
    }

    @Test
    fun insertNodeClosedPreservesShapeAndCount() {
        val nodes = listOf(
            PathNode(Pt(0.0, 0.0), handleOut = Pt(0.0, 5.0)),
            PathNode(Pt(10.0, 0.0), handleIn = Pt(10.0, 5.0), handleOut = Pt(10.0, -5.0)),
            PathNode(Pt(5.0, -8.0), handleIn = Pt(0.0, -8.0)),
        )
        val path = EditablePath(nodes, closed = true)
        val before = path.toPolyline(0.1)
        val after  = path.insertNode(2, 0.4)  // segment: last → first (closed)
        assertEquals(4, after.nodes.size)
        val d = polylineDist(before, after.toPolyline(0.1))
        assertTrue("shape changed by $d mm", d < 0.5)
    }

    // -----------------------------------------------------------------------
    // Polyline.toEditablePath round-trip
    // -----------------------------------------------------------------------

    @Test
    fun polylineToEditablePathRoundTripOpen() {
        val pts = listOf(Pt(0.0, 0.0), Pt(5.0, 3.0), Pt(10.0, 0.0))
        val poly = Polyline(pts, closed = false)
        val ep   = poly.toEditablePath()
        assertEquals(false, ep.closed)
        assertEquals(3, ep.nodes.size)
        val back = ep.toPolyline()
        assertEquals(3, back.points.size)  // no handles, straight lines → exactly the 3 anchors
        assertPtEq(pts[0], back.points[0])
        assertPtEq(pts[1], back.points[1])
        assertPtEq(pts[2], back.points[2])
    }

    @Test
    fun polylineToEditablePathRoundTripClosed() {
        // A closed polyline whose last point duplicates the first.
        val pts = listOf(Pt(0.0, 0.0), Pt(10.0, 0.0), Pt(5.0, 5.0), Pt(0.0, 0.0))
        val poly = Polyline(pts, closed = true)
        val ep   = poly.toEditablePath()
        assertTrue(ep.closed)
        // Duplicate last point should be dropped.
        assertEquals(3, ep.nodes.size)
        // toPolyline on a closed path appends the closing segment back to first.
        val back = ep.toPolyline()
        assertTrue(back.closed)
    }

    @Test
    fun polylineToEditablePathNoDuplicateDropOnDistinctLastPoint() {
        val pts = listOf(Pt(0.0, 0.0), Pt(10.0, 0.0), Pt(5.0, 5.0))
        val poly = Polyline(pts, closed = true)
        val ep   = poly.toEditablePath()
        assertEquals(3, ep.nodes.size)
    }

    @Test
    fun polylineToEditablePathAllNodesAreCorners() {
        val poly = Polyline(listOf(Pt(1.0, 2.0), Pt(3.0, 4.0)), closed = false)
        for (n in poly.toEditablePath().nodes) {
            assertNull(n.handleIn)
            assertNull(n.handleOut)
            assertEquals(false, n.smooth)
        }
    }

    // -----------------------------------------------------------------------
    // Hit-testing: nearestNode
    // -----------------------------------------------------------------------

    private fun simplePath(): EditablePath = EditablePath(
        listOf(
            PathNode(Pt(0.0, 0.0)),
            PathNode(Pt(10.0, 0.0)),
            PathNode(Pt(10.0, 10.0)),
        )
    )

    @Test
    fun nearestNodeHitsFirstNode() {
        val path = simplePath()
        assertEquals(0, path.nearestNode(Pt(0.5, 0.5), 2.0))
    }

    @Test
    fun nearestNodeHitsLastNode() {
        val path = simplePath()
        assertEquals(2, path.nearestNode(Pt(10.1, 9.9), 1.0))
    }

    @Test
    fun nearestNodeMissWhenTooFar() {
        val path = simplePath()
        assertNull(path.nearestNode(Pt(50.0, 50.0), 1.0))
    }

    @Test
    fun nearestNodePicksClosest() {
        val path = simplePath()
        // Query near node 1 (10,0), not node 0 (0,0).
        assertEquals(1, path.nearestNode(Pt(9.5, 0.0), 5.0))
    }

    // -----------------------------------------------------------------------
    // Hit-testing: nearestHandle
    // -----------------------------------------------------------------------

    private fun handlePath(): EditablePath = EditablePath(
        listOf(
            PathNode(Pt(0.0, 0.0), handleOut = Pt(2.0, 5.0)),
            PathNode(Pt(10.0, 0.0), handleIn = Pt(8.0, 5.0), handleOut = Pt(12.0, -4.0)),
        )
    )

    @Test
    fun nearestHandleHitsOut() {
        val path = handlePath()
        val hit = path.nearestHandle(Pt(2.1, 5.1), 1.0)
        assertNotNull(hit)
        assertEquals(0, hit!!.nodeIndex)
        assertEquals(HandleSide.OUT, hit.side)
    }

    @Test
    fun nearestHandleHitsIn() {
        val path = handlePath()
        val hit = path.nearestHandle(Pt(7.9, 4.9), 1.0)
        assertNotNull(hit)
        assertEquals(1, hit!!.nodeIndex)
        assertEquals(HandleSide.IN, hit.side)
    }

    @Test
    fun nearestHandleMissWhenTooFar() {
        val path = handlePath()
        assertNull(path.nearestHandle(Pt(50.0, 50.0), 1.0))
    }

    @Test
    fun nearestHandleDoesNotHitAnchor() {
        // Query exactly on an anchor; should not match a handle at a different position.
        val path = handlePath()
        val hit = path.nearestHandle(Pt(0.0, 0.0), 0.5)
        assertNull(hit)
    }

    // -----------------------------------------------------------------------
    // Hit-testing: nearestSegment
    // -----------------------------------------------------------------------

    @Test
    fun nearestSegmentHitsStraightLine() {
        val path = EditablePath(listOf(PathNode(Pt(0.0, 0.0)), PathNode(Pt(10.0, 0.0))))
        val hit = path.nearestSegment(Pt(5.0, 0.5), 1.0)
        assertNotNull(hit)
        assertEquals(0, hit!!.segmentIndex)
        assertTrue(hit.distMm < 0.6)
        // t should be approximately 0.5 (midpoint of segment).
        assertEquals(0.5, hit.t, 0.05)
    }

    @Test
    fun nearestSegmentMissWhenTooFar() {
        val path = EditablePath(listOf(PathNode(Pt(0.0, 0.0)), PathNode(Pt(10.0, 0.0))))
        assertNull(path.nearestSegment(Pt(5.0, 50.0), 1.0))
    }

    @Test
    fun nearestSegmentPicksCorrectSegment() {
        val path = EditablePath(
            listOf(PathNode(Pt(0.0, 0.0)), PathNode(Pt(10.0, 0.0)), PathNode(Pt(20.0, 0.0)))
        )
        // Query near segment 1 (between nodes 1 and 2).
        val hit = path.nearestSegment(Pt(15.0, 0.1), 1.0)
        assertNotNull(hit)
        assertEquals(1, hit!!.segmentIndex)
    }

    @Test
    fun nearestSegmentCubicHit() {
        // A symmetric S-curve: query at the approximate midpoint.
        val nodes = listOf(
            PathNode(Pt(0.0, 0.0), handleOut = Pt(0.0, 10.0)),
            PathNode(Pt(10.0, 0.0), handleIn = Pt(10.0, 10.0)),
        )
        val path = EditablePath(nodes)
        // Analytic midpoint at t=0.5 of this cubic: (5, 7.5)
        val hit = path.nearestSegment(Pt(5.0, 7.5), 2.0)
        assertNotNull(hit)
        assertEquals(0, hit!!.segmentIndex)
        assertEquals(0.5, hit.t, 0.05)
        assertTrue(hit.distMm < 1.0)
    }

    @Test
    fun nearestSegmentDistanceIsAccurate() {
        val path = EditablePath(listOf(PathNode(Pt(0.0, 0.0)), PathNode(Pt(10.0, 0.0))))
        val hit = path.nearestSegment(Pt(5.0, 2.0), 5.0)
        assertNotNull(hit)
        // Closest point on the segment to (5,2) is (5,0), distance = 2.0.
        assertEquals(2.0, hit!!.distMm, 0.1)
    }

    @Test
    fun nearestSegmentReturnsNullForSingleNode() {
        val path = EditablePath(listOf(PathNode(Pt(0.0, 0.0))))
        assertNull(path.nearestSegment(Pt(0.0, 0.0), 100.0))
    }
}
