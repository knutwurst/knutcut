package de.knutwurst.knutcut.svgcore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HersheyTest {

    // A tiny synthetic font: glyph 0 = space (bounds JZ), glyph 1 = '!' (a single vertical stroke).
    // Header layout: 5-char id, 3-char vertex count, then two chars per vertex (value = char - 'R').
    private val twoGlyph = "00000  1JZ\n00001  3JZRFRT\n"

    @Test fun `renders a glyph shifted so its left bound sits at the pen`() {
        val polys = HersheyFont.parse(twoGlyph).render("!", 21.0) // scale 1.0
        assertEquals(1, polys.size)
        val p = polys[0].points
        // left bound J = -8, so dx = 0 - (-8) = 8; stroke R F (0,-12) .. R T (0,2)
        assertEquals(8.0, p[0].xMm, 1e-9)
        assertEquals(-12.0, p[0].yMm, 1e-9)
        assertEquals(8.0, p[1].xMm, 1e-9)
        assertEquals(2.0, p[1].yMm, 1e-9)
        assertEquals(false, polys[0].closed)
    }

    @Test fun `a pen-up splits one glyph into two strokes`() {
        // bounds JZ, stroke RF-RT, pen-up ' R', stroke RX-RZ  => count 6
        val font = HersheyFont.parse("00001  6JZRFRT RRXRZ\n00001  6JZRFRT RRXRZ\n")
        val polys = font.render(" ", 21.0) // glyph 0 (space) is the 6-vertex one here
        assertEquals(2, polys.size)
    }

    @Test fun `newlines advance to the next line`() {
        val polys = HersheyFont.parse(twoGlyph).render("!\n!", 21.0)
        assertEquals(2, polys.size)
        // second line is lower (greater y) than the first
        assertTrue(polys[1].points[0].yMm > polys[0].points[0].yMm)
    }

    @Test fun `unknown characters fall back to space without crashing`() {
        val polys = HersheyFont.parse(twoGlyph).render("ä!", 21.0)
        assertEquals(1, polys.size) // 'ä' unknown -> space (no strokes), '!' -> one stroke
    }

    @Test fun `height scales the glyph`() {
        val small = HersheyFont.parse(twoGlyph).render("!", 21.0)[0].points
        val big = HersheyFont.parse(twoGlyph).render("!", 42.0)[0].points
        assertEquals(small[0].yMm * 2, big[0].yMm, 1e-9)
    }
}
