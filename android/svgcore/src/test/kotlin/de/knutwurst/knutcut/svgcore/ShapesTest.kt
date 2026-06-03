package de.knutwurst.knutcut.svgcore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShapesTest {

    @Test
    fun rectIsAClosedBoxOfTheRightSize() {
        val r = Shapes.rect(40.0, 20.0)
        assertTrue(r.closed)
        assertEquals(5, r.points.size) // 4 corners + closing point
        val b = Bounds.of(r.points)
        assertEquals(40.0, b.widthMm, 1e-9)
        assertEquals(20.0, b.heightMm, 1e-9)
    }

    @Test
    fun circleSpansItsDiameter() {
        val c = Shapes.circle(30.0, segments = 64)
        val b = Bounds.of(c.points)
        assertEquals(30.0, b.widthMm, 1e-6)
        assertEquals(30.0, b.heightMm, 1e-6)
    }

    @Test
    fun triangleHasThreeCorners() {
        val t = Shapes.regularPolygon(3, 40.0)
        assertEquals(4, t.points.size) // 3 corners + closing point
    }

    @Test
    fun fivePointStarHasTenVertices() {
        val s = Shapes.star(5, 40.0)
        assertEquals(11, s.points.size) // 10 vertices + closing point
    }
}
