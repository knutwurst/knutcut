package de.knutwurst.knutcut.svgcore

import org.junit.Assert.assertEquals
import org.junit.Test

class EtxFramerTest {

    private fun feed(f: EtxFramer, s: String): List<String> {
        val b = s.toByteArray(Charsets.UTF_8)
        return f.append(b, b.size)
    }

    @Test
    fun splitsTwoTokens() {
        val f = EtxFramer()
        assertEquals(listOf("0", "FW132"), feed(f, "0\u0003FW132\u0003"))
    }

    @Test
    fun buffersPartialTokenAcrossAppends() {
        val f = EtxFramer()
        assertEquals(emptyList<String>(), feed(f, "M0,0"))
        assertEquals(listOf("M0,0"), feed(f, "\u0003"))
    }

    @Test
    fun emptyTokenBetweenTerminators() {
        val f = EtxFramer()
        assertEquals(listOf("0", ""), feed(f, "0\u0003\u0003"))
    }

    @Test
    fun multiByteCharSplitAcrossReadsIsNotCorrupted() {
        // "ü" is two UTF-8 bytes (0xC3 0xBC); split them across two appends.
        val f = EtxFramer()
        val bytes = "ü\u0003".toByteArray(Charsets.UTF_8)
        assertEquals(emptyList<String>(), f.append(byteArrayOf(bytes[0]), 1))
        val rest = bytes.copyOfRange(1, bytes.size)
        assertEquals(listOf("ü"), f.append(rest, rest.size))
    }
}
