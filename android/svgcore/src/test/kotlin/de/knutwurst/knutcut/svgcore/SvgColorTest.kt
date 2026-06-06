package de.knutwurst.knutcut.svgcore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SvgColorTest {

    @Test
    fun sixDigitHex() {
        assertEquals(0xFFFF0000.toInt(), SvgColor.parse("#ff0000"))
        assertEquals(0xFF00FF00.toInt(), SvgColor.parse("#00FF00"))
    }

    @Test
    fun threeDigitHexIsExpanded() {
        assertEquals(0xFFFF0000.toInt(), SvgColor.parse("#f00"))
        assertEquals(0xFFAABBCC.toInt(), SvgColor.parse("#abc"))
    }

    @Test
    fun hexWithAlpha() {
        assertEquals(0x80FF0000.toInt(), SvgColor.parse("#ff000080"))
        assertEquals(0xFFFF0000.toInt(), SvgColor.parse("#f00f"))
    }

    @Test
    fun rgbAndRgba() {
        assertEquals(0xFF010203.toInt(), SvgColor.parse("rgb(1, 2, 3)"))
        assertEquals(0xFFFF0000.toInt(), SvgColor.parse("rgb(100%, 0%, 0%)"))
        assertEquals(0x80FF0000.toInt(), SvgColor.parse("rgba(255, 0, 0, 0.5)"))
    }

    @Test
    fun namedColours() {
        assertEquals(0xFF000000.toInt(), SvgColor.parse("black"))
        assertEquals(0xFFFFA500.toInt(), SvgColor.parse("Orange"))
    }

    @Test
    fun noneAndJunkAreNull() {
        assertNull(SvgColor.parse("none"))
        assertNull(SvgColor.parse("transparent"))
        assertNull(SvgColor.parse("currentColor"))
        assertNull(SvgColor.parse(""))
        assertNull(SvgColor.parse(null))
        assertNull(SvgColor.parse("notacolor"))
        assertNull(SvgColor.parse("#12"))
    }
}
