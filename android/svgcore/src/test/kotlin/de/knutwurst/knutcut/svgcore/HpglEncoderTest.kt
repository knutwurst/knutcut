package de.knutwurst.knutcut.svgcore

import org.junit.Assert.assertEquals
import org.junit.Test

class HpglEncoderTest {

    @Test
    fun fortyMmSquareAtOrigin() {
        val square = Polyline(
            listOf(Pt(0.0, 0.0), Pt(40.0, 0.0), Pt(40.0, 40.0), Pt(0.0, 40.0), Pt(0.0, 0.0)),
            closed = true,
        )
        assertEquals(
            listOf("PU0,0", "PD1600,0", "PD1600,1600", "PD0,1600", "PD0,0"),
            HpglEncoder.encode(listOf(square)),
        )
    }

    @Test
    fun closedPolylineGetsClosingMove() {
        // points do not repeat the start; encoder adds the closing PD
        val tri = Polyline(listOf(Pt(0.0, 0.0), Pt(10.0, 0.0), Pt(0.0, 10.0)), closed = true)
        assertEquals(
            listOf("PU0,0", "PD400,0", "PD0,400", "PD0,0"),
            HpglEncoder.encode(listOf(tri)),
        )
    }

    @Test
    fun openPolylineHasNoClosingMove() {
        val line = Polyline(listOf(Pt(0.0, 0.0), Pt(10.0, 0.0)), closed = false)
        assertEquals(listOf("PU0,0", "PD400,0"), HpglEncoder.encode(listOf(line)))
    }

    @Test
    fun zeroLengthMovesDropped() {
        val pl = Polyline(listOf(Pt(0.0, 0.0), Pt(0.001, 0.0), Pt(5.0, 0.0)), closed = false)
        // 0.001 mm rounds to 0 units, same as start, so it is skipped
        assertEquals(listOf("PU0,0", "PD200,0"), HpglEncoder.encode(listOf(pl)))
    }
}
