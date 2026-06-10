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

    // -----------------------------------------------------------------------
    // renderGlyphs tests
    // -----------------------------------------------------------------------

    @Test fun `renderGlyphs returns one run per non-newline character`() {
        val runs = HersheyFont.parse(twoGlyph).renderGlyphs("! ", 21.0)
        assertEquals(2, runs.size)
    }

    @Test fun `renderGlyphs glyph-local x starts at zero`() {
        // The '!' glyph has left bound J = -8; after shifting, the minimum x must be ≥ 0.
        val run = HersheyFont.parse(twoGlyph).renderGlyphs("!", 21.0)[0]
        assertTrue("glyph x origin at 0", run.polylines[0].points.all { it.xMm >= -1e-9 })
    }

    @Test fun `renderGlyphs advance matches glyph width`() {
        // '!' glyph: left J = -8, right Z = 8  → width 16 units; at scale 1.0 advance = 16.0 mm.
        val run = HersheyFont.parse(twoGlyph).renderGlyphs("!", 21.0)[0]
        assertEquals(16.0, run.advanceMm, 1e-9)
    }

    @Test fun `renderGlyphs space produces empty polylines but non-zero advance`() {
        // Space glyph: bounds JZ → left -8, right 8 → advance 16.0 at scale 1.0; no strokes.
        val run = HersheyFont.parse(twoGlyph).renderGlyphs(" ", 21.0)[0]
        assertTrue("space has no strokes", run.polylines.isEmpty())
        assertTrue("space still advances", run.advanceMm > 0.0)
    }

    @Test fun `renderGlyphs newlines are stripped`() {
        val runs = HersheyFont.parse(twoGlyph).renderGlyphs("!\n!", 21.0)
        assertEquals("newline stripped, 2 glyphs", 2, runs.size)
    }

    @Test fun `renderGlyphs total advance equals sum of individual advances`() {
        val font = HersheyFont.parse(twoGlyph)
        val runsAll = font.renderGlyphs("! !", 21.0)
        val sum = runsAll.sumOf { it.advanceMm }
        val excl = font.renderGlyphs("!", 21.0)[0].advanceMm
        val spc = font.renderGlyphs(" ", 21.0)[0].advanceMm
        assertEquals(excl + spc + excl, sum, 1e-9)
    }
}
