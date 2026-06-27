package de.knutwurst.knutcut.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Pure hex <-> ARGB helpers used by the layer color picker. */
class ColorHexTest {

    @Test
    fun parsesSixDigitAsOpaque() {
        assertEquals(0xFFFF0000.toInt(), hexToArgb("#FF0000"))
        assertEquals(0xFF00FF00.toInt(), hexToArgb("00FF00"))
        assertEquals(0xFF1E88E5.toInt(), hexToArgb("#1e88e5")) // lowercase accepted
    }

    @Test
    fun parsesEightDigitWithAlpha() {
        assertEquals(0x80FF0000.toInt(), hexToArgb("#80FF0000"))
        assertEquals(0xFFFFFFFF.toInt(), hexToArgb("FFFFFFFF"))
    }

    @Test
    fun rejectsInvalid() {
        assertNull(hexToArgb(""))
        assertNull(hexToArgb("#GG0000"))
        assertNull(hexToArgb("12345"))    // 5 digits
        assertNull(hexToArgb("#1234567")) // 7 digits
        assertNull(hexToArgb("nope"))
    }

    @Test
    fun formatsRgbUppercaseWithoutAlpha() {
        assertEquals("#FF0000", argbToHex(0xFFFF0000.toInt()))
        assertEquals("#1E88E5", argbToHex(0xFF1E88E5.toInt()))
        assertEquals("#000000", argbToHex(0xFF000000.toInt()))
        assertEquals("#FFFFFF", argbToHex(0x80FFFFFF.toInt())) // alpha dropped
    }

    @Test
    fun roundTripsRgb() {
        val argb = 0xFF3949AB.toInt()
        assertEquals(argb, hexToArgb(argbToHex(argb)))
    }
}
