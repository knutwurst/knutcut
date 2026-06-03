package de.knutwurst.knutcut.svgcore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PltParserTest {

    @Test
    fun parsesASquareInUnitsToMm() {
        // 40 units = 1 mm; this is a 40 mm square returning to its start (closed).
        val plt = "IN;SP1;PU0,0;PD1600,0;PD1600,1600;PD0,1600;PD0,0;PU;PG;"
        val polys = PltParser.parse(plt)
        assertEquals(1, polys.size)
        assertTrue(polys[0].closed)
        val b = Bounds.of(polys[0].points)
        assertEquals(0.0, b.minX, 1e-9)
        assertEquals(40.0, b.widthMm, 1e-9)
        assertEquals(40.0, b.heightMm, 1e-9)
    }

    @Test
    fun multipleCoordinatePairsInOnePD() {
        val polys = PltParser.parse("PU0,0;PD400,0,400,400;")
        assertEquals(1, polys.size)
        assertEquals(3, polys[0].points.size) // start + two PD points
    }

    @Test
    fun separateContoursOnEachPenUp() {
        val polys = PltParser.parse("PU0,0;PD400,0;PU800,0;PD1200,0;")
        assertEquals(2, polys.size)
    }

    @Test
    fun ignoresUnknownCommandsAndBlanks() {
        assertEquals(emptyList<Polyline>(), PltParser.parse("IN;SP1;PG;"))
    }
}
