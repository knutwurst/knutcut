package de.knutwurst.knutcut.svgcore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    @Test
    fun skipsMalformedElementsAndReportsCount() {
        // A good rect, plus a path with a malformed transform (too few matrix args). The bad transform
        // is counted as skipped; both elements still survive (the transform falls back to the parent).
        val svg = """<svg xmlns="http://www.w3.org/2000/svg" width="40mm" height="40mm" viewBox="0 0 40 40">
            <rect x="0" y="0" width="40" height="40"/>
            <path d="M0,0 L10,0 L10,10" transform="matrix(1,2,3)"/>
            </svg>"""
        val result = SvgParser.parseShapesResult(svg)
        assertEquals(2, result.shapes.size)
        assertEquals(1, result.skipped)
    }

    @Test
    fun fillColourIsCapturedPerShape() {
        val svg = """<svg xmlns="http://www.w3.org/2000/svg" width="40mm" height="40mm" viewBox="0 0 40 40">
            <rect x="0" y="0" width="10" height="10" fill="#ff0000"/>
            <rect x="20" y="0" width="10" height="10" style="fill:rgb(0,0,255)"/>
            </svg>"""
        val shapes = SvgParser.parseShapes(svg)
        assertEquals(0xFFFF0000.toInt(), shapes[0].colorArgb)
        assertEquals(0xFF0000FF.toInt(), shapes[1].colorArgb)
    }

    @Test
    fun colourIsInheritedFromParentGroup() {
        val svg = """<svg xmlns="http://www.w3.org/2000/svg" width="40mm" height="40mm" viewBox="0 0 40 40">
            <g fill="green"><rect x="0" y="0" width="10" height="10"/></g></svg>"""
        assertEquals(0xFF008000.toInt(), SvgParser.parseShapes(svg).single().colorArgb)
    }

    @Test
    fun fillNoneFallsBackToStroke() {
        val svg = """<svg xmlns="http://www.w3.org/2000/svg" width="40mm" height="40mm" viewBox="0 0 40 40">
            <path d="M0,0 L10,0 L10,10" fill="none" stroke="#112233"/></svg>"""
        assertEquals(0xFF112233.toInt(), SvgParser.parseShapes(svg).single().colorArgb)
    }

    @Test
    fun missingColourIsNull() {
        val svg = """<svg xmlns="http://www.w3.org/2000/svg" width="40mm" height="40mm" viewBox="0 0 40 40">
            <rect x="0" y="0" width="10" height="10"/></svg>"""
        assertNull(SvgParser.parseShapes(svg).single().colorArgb)
    }

    @Test
    fun skipsDisplayNoneVisibilityHiddenAndOpacityZero() {
        fun one(extra: String) = """<svg xmlns="http://www.w3.org/2000/svg" width="40mm" height="40mm" viewBox="0 0 40 40">
            <rect x="0" y="0" width="10" height="10" $extra/></svg>"""
        assertTrue("display:none skipped", SvgParser.parseShapes(one("""style="display:none"""")).isEmpty())
        assertTrue("visibility:hidden skipped", SvgParser.parseShapes(one("""visibility="hidden"""")).isEmpty())
        assertTrue("opacity:0 skipped", SvgParser.parseShapes(one("""style="opacity:0"""")).isEmpty())
        assertEquals("a plain rect is kept", 1, SvgParser.parseShapes(one("")).size)
    }

    @Test
    fun visibilityHiddenOnGroupIsOverriddenByVisibleChild() {
        // visibility is inherited but a descendant can switch it back on.
        val svg = """<svg xmlns="http://www.w3.org/2000/svg" width="40mm" height="40mm" viewBox="0 0 40 40">
            <g visibility="hidden">
                <rect x="0" y="0" width="10" height="10"/>
                <rect x="20" y="20" width="10" height="10" visibility="visible"/>
            </g></svg>"""
        assertEquals("only the explicitly-visible child survives", 1, SvgParser.parseShapes(svg).size)
    }

    @Test
    fun displayNoneOnGroupRemovesWholeSubtree() {
        val svg = """<svg xmlns="http://www.w3.org/2000/svg" width="40mm" height="40mm" viewBox="0 0 40 40">
            <g style="display:none">
                <rect x="0" y="0" width="10" height="10" visibility="visible"/>
            </g></svg>"""
        assertTrue("display:none can't be overridden by a child", SvgParser.parseShapes(svg).isEmpty())
    }
}
