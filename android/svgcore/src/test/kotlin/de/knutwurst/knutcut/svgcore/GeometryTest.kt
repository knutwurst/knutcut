package de.knutwurst.knutcut.svgcore

import org.junit.Assert.assertEquals
import org.junit.Test

class GeometryTest {

    @Test
    fun fortyMillimetresIs1600Units() {
        assertEquals(1600, mmToUnits(40.0))
    }

    @Test
    fun oneMillimetreIs40Units() {
        assertEquals(40, mmToUnits(1.0))
    }

    @Test
    fun roundsToNearestUnit() {
        assertEquals(41, mmToUnits(1.02)) // 1.02 * 40 = 40.8
        assertEquals(40, mmToUnits(1.01)) // 1.01 * 40 = 40.4
    }

    @Test
    fun boundsCoverAllPoints() {
        val b = Bounds.of(listOf(Pt(1.0, 2.0), Pt(5.0, -3.0), Pt(0.0, 4.0)))
        assertEquals(0.0, b.minX, 1e-9)
        assertEquals(-3.0, b.minY, 1e-9)
        assertEquals(5.0, b.maxX, 1e-9)
        assertEquals(4.0, b.maxY, 1e-9)
        assertEquals(5.0, b.widthMm, 1e-9)
        assertEquals(7.0, b.heightMm, 1e-9)
    }
}
