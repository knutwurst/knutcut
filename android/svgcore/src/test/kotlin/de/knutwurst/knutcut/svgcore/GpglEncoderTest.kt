package de.knutwurst.knutcut.svgcore

import org.junit.Assert.assertEquals
import org.junit.Test

class GpglEncoderTest {

    @Test
    fun squareAtOriginSwapsXAndYAt20SuPerMm() {
        val square = Polyline(
            listOf(Pt(0.0, 0.0), Pt(40.0, 0.0), Pt(40.0, 40.0), Pt(0.0, 40.0), Pt(0.0, 0.0)),
            closed = true,
        )
        // M/D are "<y>,<x>" in SU (20/mm): (40,0)mm -> y=0,x=800; (40,40)mm -> y=800,x=800; etc.
        assertEquals(
            listOf("M0,0", "D0,800", "D800,800", "D800,0", "D0,0"),
            GpglEncoder.encode(listOf(square)),
        )
    }

    @Test
    fun closedPolylineGetsClosingDraw() {
        val tri = Polyline(listOf(Pt(0.0, 0.0), Pt(10.0, 0.0), Pt(0.0, 10.0)), closed = true)
        assertEquals(
            listOf("M0,0", "D0,200", "D200,0", "D0,0"),
            GpglEncoder.encode(listOf(tri)),
        )
    }

    @Test
    fun openPolylineHasNoClosingDraw() {
        val line = Polyline(listOf(Pt(0.0, 0.0), Pt(10.0, 0.0)), closed = false)
        assertEquals(listOf("M0,0", "D0,200"), GpglEncoder.encode(listOf(line)))
    }

    @Test
    fun zeroLengthMovesDropped() {
        val pl = Polyline(listOf(Pt(0.0, 0.0), Pt(0.001, 0.0), Pt(5.0, 0.0)), closed = false)
        // 0.001 mm rounds to 0 SU, same as start, so it is skipped
        assertEquals(listOf("M0,0", "D0,100"), GpglEncoder.encode(listOf(pl)))
    }
}
