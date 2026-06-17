package de.knutwurst.knutcut.data

import org.junit.Assert.assertEquals
import org.junit.Test

class TextDecodeTest {

    private val text = "<svg>äöü</svg>"

    @Test
    fun decodesUtf16LeWithBom() {
        val bytes = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + text.toByteArray(Charsets.UTF_16LE)
        assertEquals(text, TextDecode.decode(bytes))
    }

    @Test
    fun decodesUtf16BeWithBom() {
        val bytes = byteArrayOf(0xFE.toByte(), 0xFF.toByte()) + text.toByteArray(Charsets.UTF_16BE)
        assertEquals(text, TextDecode.decode(bytes))
    }

    @Test
    fun decodesUtf8WithBom() {
        val bytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + text.toByteArray(Charsets.UTF_8)
        assertEquals(text, TextDecode.decode(bytes))
    }

    @Test
    fun decodesPlainUtf8() {
        assertEquals(text, TextDecode.decode(text.toByteArray(Charsets.UTF_8)))
    }

    @Test
    fun utf16DecodedAsUtf8WouldHaveBeenGarbage() {
        // Guards the regression: the old code did toString("UTF-8") on UTF-16 bytes.
        val utf16 = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + text.toByteArray(Charsets.UTF_16LE)
        assertEquals(text, TextDecode.decode(utf16))
        org.junit.Assert.assertNotEquals(text, String(utf16, Charsets.UTF_8))
    }
}
