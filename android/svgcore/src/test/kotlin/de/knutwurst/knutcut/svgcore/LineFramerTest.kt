package de.knutwurst.knutcut.svgcore

import org.junit.Assert.assertEquals
import org.junit.Test

class LineFramerTest {

    private fun feed(f: LineFramer, s: String): List<String> {
        val b = s.toByteArray(Charsets.UTF_8)
        return f.append(b, b.size)
    }

    @Test
    fun splitsTwoLines() {
        val f = LineFramer()
        assertEquals(listOf("a", "bb"), feed(f, "a\r\nbb\r\n"))
    }

    @Test
    fun buffersPartialLineAcrossAppends() {
        val f = LineFramer()
        assertEquals(emptyList<String>(), feed(f, "{\"x\":1}"))
        assertEquals(listOf("{\"x\":1}"), feed(f, "\r\n"))
    }

    @Test
    fun crlfSplitAcrossAppends() {
        val f = LineFramer()
        assertEquals(emptyList<String>(), feed(f, "ab\r")) // CR buffered, line not yet closed
        assertEquals(listOf("ab", "cd"), feed(f, "\ncd\r\n")) // LF closes "ab"; then "cd" closes
    }

    @Test
    fun multiByteCharSplitAcrossReadsIsNotCorrupted() {
        // "ü" is two UTF-8 bytes (0xC3 0xBC); split them across two appends.
        val f = LineFramer()
        val bytes = "ü\r\n".toByteArray(Charsets.UTF_8)
        assertEquals(emptyList<String>(), f.append(byteArrayOf(bytes[0]), 1))
        val rest = bytes.copyOfRange(1, bytes.size)
        assertEquals(listOf("ü"), f.append(rest, rest.size))
    }
}
