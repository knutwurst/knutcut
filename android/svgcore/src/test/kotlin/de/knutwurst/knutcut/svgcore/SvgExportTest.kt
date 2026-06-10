package de.knutwurst.knutcut.svgcore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SvgExportTest {

    private fun square(x: Double, y: Double, side: Double) =
        Polyline(listOf(Pt(x, y), Pt(x + side, y), Pt(x + side, y + side), Pt(x, y + side)), closed = true)

    @Test
    fun writesMmDimensionsAndViewBoxFromBounds() {
        val svg = SvgExport.toSvg(listOf(SvgExport.Stroke(square(10.0, 10.0, 20.0))))
        assertTrue(svg.contains("<svg"))
        assertTrue("width in mm", svg.contains("width=\"20.000mm\""))
        assertTrue("height in mm", svg.contains("height=\"20.000mm\""))
        assertTrue("viewBox is the bounds", svg.contains("viewBox=\"10.000 10.000 20.000 20.000\""))
        assertTrue("closed path gets a Z", svg.contains(" Z"))
    }

    @Test
    fun usesDotDecimalSeparatorNotComma() {
        // The device locale is German; coordinates must still use '.' so the SVG is valid.
        val svg = SvgExport.toSvg(listOf(SvgExport.Stroke(square(0.0, 0.0, 12.5))))
        assertFalse("no comma decimals in lengths", svg.contains("12,500"))
        assertTrue(svg.contains("12.500"))
    }

    @Test
    fun colourBecomesHexStroke() {
        val red = 0xFFEE1122.toInt()
        val svg = SvgExport.toSvg(listOf(SvgExport.Stroke(square(0.0, 0.0, 10.0), red)))
        assertTrue("ARGB packed to #RRGGBB", svg.contains("stroke=\"#EE1122\""))
        assertTrue("no fill", svg.contains("fill=\"none\""))
    }

    @Test
    fun roundTripsThroughSvgParserAtMmScale() {
        val svg = SvgExport.toSvg(listOf(SvgExport.Stroke(square(10.0, 10.0, 20.0))))
        val shapes = SvgParser.parseShapes(svg)
        assertTrue("parser reads the exported svg", shapes.isNotEmpty())
        val pts = shapes.flatMap { it.polylines }.flatMap { it.points }
        val w = pts.maxOf { it.xMm } - pts.minOf { it.xMm }
        val h = pts.maxOf { it.yMm } - pts.minOf { it.yMm }
        assertEquals("width preserved in mm", 20.0, w, 0.5)
        assertEquals("height preserved in mm", 20.0, h, 0.5)
    }

    @Test
    fun emptyInputStillProducesAnSvg() {
        val svg = SvgExport.toSvg(emptyList())
        assertTrue(svg.contains("<svg"))
        assertTrue(svg.contains("</svg>"))
    }
}
