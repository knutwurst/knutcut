package de.knutwurst.knutcut.svgcore

import org.junit.Assert.assertEquals
import org.junit.Test

class PlacementTest {

    // A 10×10 box at the origin, placed so its centre stays at (5,5): an identity placement.
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
}
