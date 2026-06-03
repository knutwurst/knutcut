package de.knutwurst.knutcut.svgcore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SvgParserTest {

    private val squareCommands =
        listOf("PU0,0", "PD1600,0", "PD1600,1600", "PD0,1600", "PD0,0")

    @Test
    fun rectMmSvgToSquareCommands() {
        val svg = """<svg xmlns="http://www.w3.org/2000/svg" width="40mm" height="40mm" viewBox="0 0 40 40">
            <rect x="0" y="0" width="40" height="40"/></svg>"""
        assertEquals(squareCommands, HpglEncoder.encode(SvgParser.parse(svg)))
    }

    @Test
    fun pathMmSvgToSquareCommands() {
        val svg = """<svg xmlns="http://www.w3.org/2000/svg" width="40mm" height="40mm" viewBox="0 0 40 40">
            <path d="M0 0 L40 0 L40 40 L0 40 Z"/></svg>"""
        assertEquals(squareCommands, HpglEncoder.encode(SvgParser.parse(svg)))
    }

    @Test
    fun viewBoxScalesToPhysicalWidth() {
        // 40-unit-wide rect drawn in an 80mm-wide viewport -> 80mm -> 3200 units
        val svg = """<svg xmlns="http://www.w3.org/2000/svg" width="80mm" height="80mm" viewBox="0 0 40 40">
            <rect x="0" y="0" width="40" height="40"/></svg>"""
        assertEquals(
            listOf("PU0,0", "PD3200,0", "PD3200,3200", "PD0,3200", "PD0,0"),
            HpglEncoder.encode(SvgParser.parse(svg)),
        )
    }

    @Test
    fun groupTransformIsApplied() {
        val svg = """<svg xmlns="http://www.w3.org/2000/svg" width="100mm" height="100mm" viewBox="0 0 100 100">
            <g transform="translate(10,20)"><rect x="0" y="0" width="10" height="10"/></g></svg>"""
        assertEquals(
            listOf("PU400,800", "PD800,800", "PD800,1200", "PD400,1200", "PD400,800"),
            HpglEncoder.encode(SvgParser.parse(svg)),
        )
    }

    @Test
    fun circleFlattensToClosedPolylineOfRoughlyCorrectSize() {
        val svg = """<svg xmlns="http://www.w3.org/2000/svg" width="20mm" height="20mm" viewBox="0 0 20 20">
            <circle cx="10" cy="10" r="5"/></svg>"""
        val polys = SvgParser.parse(svg)
        assertEquals(1, polys.size)
        assertTrue(polys[0].closed)
        val b = Bounds.of(polys[0].points)
        assertEquals(10.0, b.widthMm, 0.2)
        assertEquals(10.0, b.heightMm, 0.2)
    }

    @Test
    fun pxUnitsUseNinetySixDpi() {
        // 96px wide with no viewBox -> 1 inch -> 25.4 mm -> 1016 units
        val svg = """<svg xmlns="http://www.w3.org/2000/svg" width="96px" height="96px">
            <rect x="0" y="0" width="96" height="96"/></svg>"""
        val cmds = HpglEncoder.encode(SvgParser.parse(svg))
        assertEquals("PU0,0", cmds.first())
        assertTrue("expected ~1016, got ${cmds[1]}", cmds[1] == "PD1016,0")
    }

    @Test
    fun cubicPathFlattens() {
        val svg = """<svg xmlns="http://www.w3.org/2000/svg" width="10mm" height="10mm" viewBox="0 0 10 10">
            <path d="M0 0 C0 10 10 10 10 0"/></svg>"""
        val polys = SvgParser.parse(svg)
        assertEquals(1, polys.size)
        assertTrue("curve should subdivide into several points", polys[0].points.size > 4)
        assertEquals(Pt(0.0, 0.0), polys[0].points.first())
        assertEquals(10.0, polys[0].points.last().xMm, 1e-6)
        assertEquals(0.0, polys[0].points.last().yMm, 1e-6)
    }
}
