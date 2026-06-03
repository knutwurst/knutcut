package de.knutwurst.knutcut.svgcore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DragKnifeTest {

    private val cmd = Regex("^P[UD]-?\\d+,-?\\d+$")

    @Test
    fun emptyStaysEmpty() {
        assertEquals(emptyList<String>(), DragKnife.process(emptyList()))
    }

    @Test
    fun sharpCornersGetExtraPoints() {
        // A 40 mm square (1600-unit edges); every corner is 90°, so each is replaced by an arc.
        val square = listOf("PU0,0", "PD1600,0", "PD1600,1600", "PD0,1600", "PD0,0")
        val out = DragKnife.process(square)
        assertTrue("compensation should add points", out.size > square.size)
        assertEquals("PU0,0", out.first())
        assertTrue("every command stays well-formed", out.all { cmd.matches(it) })
    }

    @Test
    fun offsetChangesTheCompensation() {
        val square = listOf("PU0,0", "PD1600,0", "PD1600,1600", "PD0,1600", "PD0,0")
        assertTrue(DragKnife.process(square, 13.0) != DragKnife.process(square, 26.0))
    }

    @Test
    fun straightOpenPathIsLeftAlone() {
        // Collinear points = 180° interior angles (> 150°), open path: nothing to compensate.
        val line = listOf("PU0,0", "PD1000,0", "PD2000,0", "PD3000,0")
        assertEquals(line, DragKnife.process(line))
    }

    @Test
    fun twoContoursAreEachProcessed() {
        val two = listOf(
            "PU0,0", "PD1600,0", "PD1600,1600", "PD0,1600", "PD0,0",
            "PU5000,0", "PD6600,0", "PD6600,1600", "PD5000,1600", "PD5000,0",
        )
        val out = DragKnife.process(two)
        // Both PU moves survive and both contours expand.
        assertEquals(2, out.count { it.startsWith("PU") })
        assertTrue(out.size > two.size)
        assertTrue(out.all { cmd.matches(it) })
    }
}
