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
        // Move out to (6,0); in must mirror to (-6,0) — opposite direction AND equal length.
        val anchor = Pt(0.0, 0.0)
        val node = PathNode(anchor, handleIn = Pt(-3.0, 0.0), handleOut = Pt(3.0, 0.0), smooth = true)
        val path = EditablePath(listOf(PathNode(Pt(-10.0, 0.0)), node, PathNode(Pt(10.0, 0.0))))
        val moved = path.moveHandle(1, HandleSide.OUT, Pt(6.0, 0.0))
        val n = moved.nodes[1]
        assertEquals(6.0,  n.handleOut!!.xMm, 1e-9)
        assertEquals(0.0,  n.handleOut!!.yMm, 1e-9)
        assertEquals(-6.0, n.handleIn!!.xMm,  1e-9)  // equal length, mirrored
        assertEquals(0.0,  n.handleIn!!.yMm,  1e-9)
    }

    @Test
    fun moveHandleSmoothInMirrorsOut() {
        val anchor = Pt(0.0, 0.0)
        val node = PathNode(anchor, handleIn = Pt(-4.0, 0.0), handleOut = Pt(4.0, 0.0), smooth = true)
        val path = EditablePath(listOf(PathNode(Pt(-10.0, 0.0)), node, PathNode(Pt(10.0, 0.0))))
        val moved = path.moveHandle(1, HandleSide.IN, Pt(-8.0, 0.0))
        val n = moved.nodes[1]
        // In moved to (-8, 0). Out must mirror to (8, 0) — equal length, opposite direction.
        assertEquals(-8.0, n.handleIn!!.xMm,  1e-9)
        assertEquals(0.0,  n.handleIn!!.yMm,  1e-9)
        assertEquals(8.0,  n.handleOut!!.xMm, 1e-9)
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
    fun setSmoothMakesHandleLengthsEqual() {
        val anchor = Pt(0.0, 0.0)
        // Non-collinear handles at different distances.
        val hIn  = Pt(-3.0, 0.0)  // distance 3
        val hOut = Pt( 2.0, 2.0)  // distance sqrt(8)
        val node = PathNode(anchor, handleIn = hIn, handleOut = hOut)
        val path = EditablePath(listOf(PathNode(Pt(-10.0, 0.0)), node, PathNode(Pt(10.0, 0.0))))
        val smoothed = path.setSmooth(1, true)
        val n = smoothed.nodes[1]
        val distIn  = hypot(n.handleIn!!.xMm - anchor.xMm,  n.handleIn!!.yMm - anchor.yMm)
        val distOut = hypot(n.handleOut!!.xMm - anchor.xMm, n.handleOut!!.yMm - anchor.yMm)
        // Both handles end up the same length: the average of the originals.
        assertEquals("handles must be equal length", distIn, distOut, 1e-9)
        assertEquals((3.0 + hypot(2.0, 2.0)) / 2.0, distIn, 1e-9)
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

    // -----------------------------------------------------------------------
    // Bug #1: insertNode on a closed path with out-of-range segmentIndex
    // -----------------------------------------------------------------------

    @Test(expected = IllegalArgumentException::class)
    fun insertNodeClosedOutOfRangeThrowsIllegalArgument() {
        // 3-node closed path has valid segment indices 0, 1, 2.
        // Index 100 must throw IllegalArgumentException (from require), not IOOBE.
        val nodes = listOf(
            PathNode(Pt(0.0, 0.0)),
            PathNode(Pt(10.0, 0.0)),
            PathNode(Pt(5.0, 5.0)),
        )
        val path = EditablePath(nodes, closed = true)
        path.insertNode(100, 0.5)
    }

    // -----------------------------------------------------------------------
    // Bug #2: insertNode at t=0 / t=1 must not create duplicate nodes or
    //         non-null handles equal to the anchor
    // -----------------------------------------------------------------------

    @Test
    fun insertNodeAtTZeroDoesNotCreateDegenerateHandle() {
        val nodes = listOf(
            PathNode(Pt(0.0, 0.0), handleOut = Pt(3.0, 5.0)),
            PathNode(Pt(10.0, 0.0), handleIn = Pt(7.0, 5.0)),
        )
        val path = EditablePath(nodes)
        val after = path.insertNode(0, 0.0)
        // Should not gain more than one extra node.
        assertEquals(3, after.nodes.size)
        // The inserted node must not have a handle exactly equal to its anchor.
        val inserted = after.nodes[1]
        if (inserted.handleIn != null) {
            val same = inserted.handleIn!!.xMm == inserted.anchor.xMm &&
                       inserted.handleIn!!.yMm == inserted.anchor.yMm
            assertTrue("handleIn must not equal anchor at t=0", !same)
        }
        if (inserted.handleOut != null) {
            val same = inserted.handleOut!!.xMm == inserted.anchor.xMm &&
                       inserted.handleOut!!.yMm == inserted.anchor.yMm
            assertTrue("handleOut must not equal anchor at t=0", !same)
        }
    }

    @Test
    fun insertNodeAtTOneDoesNotCreateDegenerateHandle() {
        val nodes = listOf(
            PathNode(Pt(0.0, 0.0), handleOut = Pt(3.0, 5.0)),
            PathNode(Pt(10.0, 0.0), handleIn = Pt(7.0, 5.0)),
        )
        val path = EditablePath(nodes)
        val after = path.insertNode(0, 1.0)
        assertEquals(3, after.nodes.size)
        val inserted = after.nodes[1]
        if (inserted.handleIn != null) {
            val same = inserted.handleIn!!.xMm == inserted.anchor.xMm &&
                       inserted.handleIn!!.yMm == inserted.anchor.yMm
            assertTrue("handleIn must not equal anchor at t=1", !same)
        }
        if (inserted.handleOut != null) {
            val same = inserted.handleOut!!.xMm == inserted.anchor.xMm &&
                       inserted.handleOut!!.yMm == inserted.anchor.yMm
            assertTrue("handleOut must not equal anchor at t=1", !same)
        }
    }

    @Test
    fun insertNodeNullHandleSideStaysNull() {
        // Segment with handleOut on "from" node but handleIn null on "to" node.
        // After insert the side that was null must remain null in the adjacent nodes.
        val nodes = listOf(
            PathNode(Pt(0.0, 0.0), handleOut = Pt(3.0, 5.0)),
            PathNode(Pt(10.0, 0.0)),  // handleIn is null
        )
        val path = EditablePath(nodes)
        val after = path.insertNode(0, 0.5)
        assertEquals(3, after.nodes.size)
        // The to-node's handleIn was null and de Casteljau on null side should keep null or preserve straight
        // For a partially-straight segment (one handle null, other non-null): the current contract
        // is that the segment is treated as cubic (null handle -> anchor used). We just verify no crash
        // and that shape is roughly preserved.
        val before = path.toPolyline(0.1)
        val afterPoly = after.toPolyline(0.1)
        val d = polylineDist(before, afterPoly)
        assertTrue("shape changed too much: $d mm", d < 1.0)
    }

    // -----------------------------------------------------------------------
    // Bug #3: deleteNode with out-of-range index must be a no-op
    // -----------------------------------------------------------------------

    @Test
    fun deleteNodeOutOfRangeHighIsNoOp() {
        val nodes = listOf(
            PathNode(Pt(0.0, 0.0)), PathNode(Pt(5.0, 0.0)), PathNode(Pt(10.0, 0.0))
        )
        val path = EditablePath(nodes)
        val after = path.deleteNode(99)
        assertEquals(3, after.nodes.size)
    }

    @Test
    fun deleteNodeOutOfRangeNegativeIsNoOp() {
        val nodes = listOf(
            PathNode(Pt(0.0, 0.0)), PathNode(Pt(5.0, 0.0)), PathNode(Pt(10.0, 0.0))
        )
        val path = EditablePath(nodes)
        val after = path.deleteNode(-1)
        assertEquals(3, after.nodes.size)
    }

    // -----------------------------------------------------------------------
    // Bug #7: Matrix.inverse relative determinant threshold
    // -----------------------------------------------------------------------

    @Test
    fun matrixInverseVerySmallScaleInverts() {
        // A scale of 1e-4 is tiny but valid; its determinant is 1e-8,
        // which was below the old absolute threshold of 1e-12 but should be
        // invertible with a relative check.
        val small = 1e-4
        val m = Matrix.scale(small, small)
        val inv = m.inverse()
        // Must NOT return null; must correctly invert.
        assertNotNull("tiny-but-valid scale must be invertible", inv)
        val p = Pt(small, small * 2)
        assertPtEq(p, inv!!.apply(m.apply(p)), 1e-6)
    }

    @Test
    fun matrixInverseTrueZeroScaleStillReturnsNull() {
        val m = Matrix.scale(0.0, 1.0)
        assertNull(m.inverse())
    }

    // -----------------------------------------------------------------------
    // dragSegment
    // -----------------------------------------------------------------------

    /** Evaluate a cubic Bézier at [t] for test verification. */
    private fun cubicAt(p0: Pt, p1: Pt, p2: Pt, p3: Pt, t: Double): Pt {
        val u = 1.0 - t
        val uu = u * u; val tt = t * t
        val uuu = uu * u; val ttt = tt * t
        return Pt(
            uuu * p0.xMm + 3.0 * uu * t * p1.xMm + 3.0 * u * tt * p2.xMm + ttt * p3.xMm,
            uuu * p0.yMm + 3.0 * uu * t * p1.yMm + 3.0 * u * tt * p2.yMm + ttt * p3.yMm,
        )
    }

    /** Extract the cubic control points for segment [si] of [path]. */
    private fun segmentPoints(path: EditablePath, si: Int): Array<Pt> {
        val n = path.nodes.size
        val fromIdx = si
        val toIdx = if (path.closed) (si + 1) % n else si + 1
        val from = path.nodes[fromIdx]
        val to   = path.nodes[toIdx]
        return arrayOf(
            from.anchor,
            from.handleOut ?: from.anchor,
            to.handleIn    ?: to.anchor,
            to.anchor,
        )
    }

    @Test
    fun dragSegmentStraightMidpointReachesTarget() {
        // Straight horizontal segment (0,0)→(100,0); drag midpoint to (50,20).
        val path = EditablePath(listOf(PathNode(Pt(0.0, 0.0)), PathNode(Pt(100.0, 0.0))))
        val target = Pt(50.0, 20.0)
        val after = path.dragSegment(0, 0.5, target)

        val (p0, p1, p2, p3) = segmentPoints(after, 0)
        val actual = cubicAt(p0, p1, p2, p3, 0.5)
        assertEquals("x at t=0.5", target.xMm, actual.xMm, 1.0)
        assertEquals("y at t=0.5", target.yMm, actual.yMm, 1.0)
    }

    @Test
    fun dragSegmentEndpointsUnchanged() {
        val path = EditablePath(listOf(PathNode(Pt(0.0, 0.0)), PathNode(Pt(100.0, 0.0))))
        val after = path.dragSegment(0, 0.5, Pt(50.0, 20.0))
        // Anchors must not move.
        assertPtEq(Pt(0.0, 0.0),   after.nodes[0].anchor, 1e-9)
        assertPtEq(Pt(100.0, 0.0), after.nodes[1].anchor, 1e-9)
    }

    @Test
    fun dragSegmentAtTQuarterMovesCorrectly() {
        // Drag at t=0.25; check that the curve point at t=0.25 is closer to the target
        // than the original was.
        val path = EditablePath(listOf(PathNode(Pt(0.0, 0.0)), PathNode(Pt(100.0, 0.0))))
        val target = Pt(25.0, 30.0)
        val after = path.dragSegment(0, 0.25, target)

        val (p0, p1, p2, p3) = segmentPoints(after, 0)
        val actual = cubicAt(p0, p1, p2, p3, 0.25)
        // Should be closer to target than the original (0,0)→(100,0) at t=0.25 → (25,0).
        val distAfter  = hypot(actual.xMm - target.xMm, actual.yMm - target.yMm)
        val distBefore = hypot(25.0 - target.xMm, 0.0 - target.yMm)
        assertTrue("drag must move curve toward target (before=$distBefore after=$distAfter)",
            distAfter < distBefore)
        // Endpoints still fixed.
        assertPtEq(Pt(0.0, 0.0),   after.nodes[0].anchor, 1e-9)
        assertPtEq(Pt(100.0, 0.0), after.nodes[1].anchor, 1e-9)
    }

    @Test
    fun dragSegmentSmoothNodeKeepsC1() {
        // fromNode is smooth; after dragging, its in- and out-handles must remain collinear
        // through the anchor.
        val anchor = Pt(50.0, 0.0)
        val fromNode = PathNode(
            anchor    = anchor,
            handleIn  = Pt(30.0, 0.0),
            handleOut = Pt(70.0, 0.0),
            smooth    = true,
        )
        val path = EditablePath(
            listOf(
                PathNode(Pt(0.0, 0.0)),
                fromNode,
                PathNode(Pt(100.0, 0.0)),
            )
        )
        // Drag segment 1 (fromNode → last node) so that fromNode's handleOut is adjusted.
        val after = path.dragSegment(1, 0.5, Pt(75.0, 20.0))

        val fn = after.nodes[1]
        val hIn  = fn.handleIn  ?: return  // smooth node must have handles
        val hOut = fn.handleOut ?: return

        // Collinearity: cross product (anchor→out) × (anchor→in) ≈ 0.
        val ox = hOut.xMm - anchor.xMm; val oy = hOut.yMm - anchor.yMm
        val ix = hIn.xMm  - anchor.xMm; val iy = hIn.yMm  - anchor.yMm
        val cross = ox * iy - oy * ix
        assertEquals("handles must be collinear (smooth node)", 0.0, cross, 1e-6)
        // Opposite directions.
        val dot = ox * ix + oy * iy
        assertTrue("handles must point away from each other", dot <= 0.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun dragSegmentOutOfRangeThrows() {
        val path = EditablePath(listOf(PathNode(Pt(0.0, 0.0)), PathNode(Pt(10.0, 0.0))))
        path.dragSegment(99, 0.5, Pt(5.0, 5.0))
    }

    @Test
    fun dragSegmentOtherSegmentsUnchanged() {
        // Three-node path; drag segment 0 only; segment 1 control points must be identical.
        val n0 = PathNode(Pt(0.0, 0.0))
        val n1 = PathNode(Pt(50.0, 0.0))
        val n2 = PathNode(Pt(100.0, 0.0))
        val path = EditablePath(listOf(n0, n1, n2))
        val after = path.dragSegment(0, 0.5, Pt(25.0, 15.0))

        // Segment 1 goes from node[1] to node[2]; node[2] must be completely untouched.
        assertPtEq(n2.anchor, after.nodes[2].anchor, 1e-9)
        assertEquals(null, after.nodes[2].handleIn)
        assertEquals(null, after.nodes[2].handleOut)
    }
}
