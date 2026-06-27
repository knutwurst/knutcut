package de.knutwurst.knutcut.svgcore

import org.junit.Assert.assertEquals
import org.junit.Test

class PlacementTest {

    // A 10×10 box at the origin, placed so its center stays at (5,5): an identity placement.
    private val box = Bounds(0.0, 0.0, 10.0, 10.0)
    private val center = Pt(5.0, 5.0)

    @Test
    fun identityKeepsPoints() {
        val m = Placement.matrix(box, center, 1.0, 1.0, 0.0)
        val p = m.apply(Pt(0.0, 0.0))
        assertEquals(0.0, p.xMm, 1e-9)
        assertEquals(0.0, p.yMm, 1e-9)
    }

    @Test
    fun flipXMirrorsAroundCentre() {
        val m = Placement.matrix(box, center, 1.0, 1.0, 0.0, flipX = true)
        val p = m.apply(Pt(0.0, 0.0)) // left edge moves to the right edge
        assertEquals(10.0, p.xMm, 1e-9)
        assertEquals(0.0, p.yMm, 1e-9)
    }

    @Test
    fun flipYMirrorsAroundCentre() {
        val m = Placement.matrix(box, center, 1.0, 1.0, 0.0, flipY = true)
        val p = m.apply(Pt(0.0, 0.0))
        assertEquals(0.0, p.xMm, 1e-9)
        assertEquals(10.0, p.yMm, 1e-9)
    }

    @Test
    fun scaleGrowsAroundCentre() {
        val m = Placement.matrix(box, center, 2.0, 2.0, 0.0)
        val p = m.apply(Pt(0.0, 0.0)) // corner moves out by the extra half-size
        assertEquals(-5.0, p.xMm, 1e-9)
        assertEquals(-5.0, p.yMm, 1e-9)
    }

    @Test
    fun scaleForMapsLengths() {
        assertEquals(2.0, Placement.scaleFor(10.0, 20.0), 1e-9)
        assertEquals(0.5, Placement.scaleFor(40.0, 20.0), 1e-9)
        assertEquals(1.0, Placement.scaleFor(0.0, 20.0), 1e-9) // degenerate
    }

    // ---- resize ----
    // Local handle coordinates for the 10×10 box: corners then side midpoints.
    private val tl = Pt(0.0, 0.0); private val tr = Pt(10.0, 0.0)
    private val br = Pt(10.0, 10.0); private val bl = Pt(0.0, 10.0)
    private val leftMid = Pt(0.0, 5.0); private val rightMid = Pt(10.0, 5.0)

    @Test
    fun cornerResizeDoublesAboutTheAnchor() {
        // Drag the TL corner (handle 0) out so the box doubles about the fixed BR corner at (10,10).
        val r = Placement.resize(
            handle = 0, dragWorld = Pt(-10.0, -10.0), anchorWorld = br,
            anchorLocal = br, draggedLocal = tl, centerLocal = center,
            startScaleX = 1.0, startScaleY = 1.0, rotationDeg = 0.0,
        )
        assertEquals(2.0, r.scaleX, 1e-9)
        assertEquals(2.0, r.scaleY, 1e-9)
        assertEquals(0.0, r.center.xMm, 1e-9) // a 20×20 box anchored at (10,10) is centered at (0,0)
        assertEquals(0.0, r.center.yMm, 1e-9)
    }

    @Test
    fun horizontalResizeWithFlipDoesNotCollapse() {
        // flipX mirrors the box around x=5, so the right-mid handle (local x=10) sits at world x=0 and
        // the left-mid anchor (local x=0) sits at world x=10. Dragging the handle left to x=-10 should
        // double the width. The old maths (ignoring the flip sign) projected the wrong way and clamped
        // the scale to the 0.02 floor — this guards that regression.
        val r = Placement.resize(
            handle = 5, dragWorld = Pt(-10.0, 5.0), anchorWorld = Pt(10.0, 5.0),
            anchorLocal = leftMid, draggedLocal = rightMid, centerLocal = center,
            startScaleX = 1.0, startScaleY = 1.0, rotationDeg = 0.0, flipX = true,
        )
        assertEquals("flipped layer still scales up", 2.0, r.scaleX, 1e-9)
        assertEquals("the off-axis scale is untouched", 1.0, r.scaleY, 1e-9)
    }

    @Test
    fun resizeKeepsTheAnchorFixedUnderFlip() {
        // Rebuilding the placement from the resize result must map the anchor back onto its world point
        // and the dragged handle onto the finger — the invariant a resize must hold.
        val anchorWorld = Pt(10.0, 5.0); val dragWorld = Pt(-10.0, 5.0)
        val r = Placement.resize(
            handle = 5, dragWorld = dragWorld, anchorWorld = anchorWorld,
            anchorLocal = leftMid, draggedLocal = rightMid, centerLocal = center,
            startScaleX = 1.0, startScaleY = 1.0, rotationDeg = 0.0, flipX = true,
        )
        val m = Placement.matrix(box, r.center, r.scaleX, r.scaleY, 0.0, flipX = true)
        val a = m.apply(leftMid); val d = m.apply(rightMid)
        assertEquals(anchorWorld.xMm, a.xMm, 1e-9); assertEquals(anchorWorld.yMm, a.yMm, 1e-9)
        assertEquals(dragWorld.xMm, d.xMm, 1e-9); assertEquals(dragWorld.yMm, d.yMm, 1e-9)
    }

    @Test
    fun resizeKeepsTheAnchorFixedUnderRotation() {
        // Same invariant with a 90° rotation and no flip: a corner drag that exactly doubles the box.
        // Build the world points from the matrix so the test stays independent of rotation conventions.
        val m0 = Placement.matrix(box, center, 1.0, 1.0, 90.0)
        val anchorWorld = m0.apply(br)
        // Doubling about BR sends TL to anchor + 2×(TL−anchor) in world space (the matrix's linear
        // part scales the anchor→corner offset; the translation cancels in the difference).
        val startTl = m0.apply(tl)
        val target = Pt(
            anchorWorld.xMm + 2 * (startTl.xMm - anchorWorld.xMm),
            anchorWorld.yMm + 2 * (startTl.yMm - anchorWorld.yMm),
        )
        val r = Placement.resize(
            handle = 0, dragWorld = target, anchorWorld = anchorWorld,
            anchorLocal = br, draggedLocal = tl, centerLocal = center,
            startScaleX = 1.0, startScaleY = 1.0, rotationDeg = 90.0,
        )
        assertEquals(2.0, r.scaleX, 1e-9)
        assertEquals(2.0, r.scaleY, 1e-9)
        val m = Placement.matrix(box, r.center, r.scaleX, r.scaleY, 90.0)
        val a = m.apply(br)
        assertEquals(anchorWorld.xMm, a.xMm, 1e-9); assertEquals(anchorWorld.yMm, a.yMm, 1e-9)
    }
}
