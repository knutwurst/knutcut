package de.knutwurst.knutcut.svgcore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContourPickTest {

    private fun square(cx: Double, cy: Double, half: Double): Polyline {
        val pts = listOf(
            Pt(cx - half, cy - half), Pt(cx + half, cy - half),
            Pt(cx + half, cy + half), Pt(cx - half, cy + half),
        )
        return Polyline(pts, closed = true)
    }

    @Test fun nearestBoundary_picksContourUnderTheTap() {
        val a = square(0.0, 0.0, 10.0)     // left square
        val b = square(100.0, 0.0, 10.0)   // right square
        val (idx, dist) = ContourPick.nearestBoundary(listOf(a, b), Pt(100.0, 10.0))!!
        assertEquals(1, idx)               // the right square's edge is right under the tap
        assertTrue("expected the tap on the edge to be close", dist < 0.001)
    }

    @Test fun nearestBoundary_tapNearLeftEdgeOfRightSquarePicksRight() {
        val a = square(0.0, 0.0, 10.0)
        val b = square(100.0, 0.0, 10.0)
        // 1 mm outside the right square's left edge (x=90), far from the left square (right edge x=10).
        val (idx, dist) = ContourPick.nearestBoundary(listOf(a, b), Pt(89.0, 0.0))!!
        assertEquals(1, idx)
        assertEquals(1.0, dist, 1e-9)
    }

    @Test fun nearestBoundary_emptyReturnsNull() {
        assertNull(ContourPick.nearestBoundary(emptyList(), Pt(0.0, 0.0)))
    }

    @Test fun boundaryDist_interiorMeasuresToNearestEdgeNotZero() {
        // A point in the dead center of a 20 mm square is 10 mm from every edge — proving interior
        // taps are NOT treated as "on" the contour (the editor uses a small threshold to switch).
        val s = square(0.0, 0.0, 10.0)
        assertEquals(10.0, ContourPick.boundaryDist(Pt(0.0, 0.0), s), 1e-9)
    }

    @Test fun largestByArea_picksTheBiggerContour() {
        val small = square(0.0, 0.0, 2.0)   // 4x4 = 16
        val big = square(50.0, 50.0, 10.0)  // 20x20 = 400
        assertEquals(1, ContourPick.largestByArea(listOf(small, big)))
    }

    @Test fun area_squareIsSideSquared() {
        assertEquals(400.0, ContourPick.area(square(0.0, 0.0, 10.0)), 1e-9)
    }

    @Test fun area_degenerateContourIsZero() {
        assertEquals(0.0, ContourPick.area(Polyline(listOf(Pt(0.0, 0.0), Pt(1.0, 1.0)), closed = true)), 1e-9)
    }

    @Test fun nearestBoundary_holePickedWhenTapOnInnerEdge() {
        val outer = square(0.0, 0.0, 50.0)  // big body
        val hole = square(0.0, 0.0, 10.0)   // concentric hole, edges at +-10
        // Tap right on the hole's edge: the hole, not the outer body, should win.
        val (idx, dist) = ContourPick.nearestBoundary(listOf(outer, hole), Pt(10.0, 0.0))!!
        assertEquals(1, idx)
        assertTrue(dist < 0.001)
        assertNotNull(idx)
    }
}
