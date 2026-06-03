package de.knutwurst.knutcut.svgcore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlotterSessionTest {

    private class FakeLink(val responder: (Int) -> String?) : PlotterLink {
        val writes = ArrayList<String>()
        override fun write(text: String) { writes.add(text) }
        override fun readLine(timeoutMs: Long): String? = responder(writes.size)
    }

    @Test
    fun sendsFramedMessagesWithIncrementingCseq() {
        val link = FakeLink { "{\"type\":\"ack\"}" }
        val result = PlotterSession(link).run(listOf(Handshake, Bye))
        assertEquals(CutResult.Done, result)
        assertEquals(2, link.writes.size)
        assertEquals(Frame.encode(Handshake, 0), link.writes[0])
        assertEquals(Frame.encode(Bye, 1), link.writes[1])
    }

    @Test
    fun resendsOnCrcRejection() {
        // first response is a crc error, then ack
        var n = 0
        val link = FakeLink { n++; if (n == 1) "{\"type\":\"crc\"}" else "{\"type\":\"ack\"}" }
        val result = PlotterSession(link).run(listOf(Handshake))
        assertEquals(CutResult.Done, result)
        assertEquals(2, link.writes.size) // sent twice
    }

    @Test
    fun failsAfterMaxAttempts() {
        val link = FakeLink { "{\"type\":\"crc\"}" }
        val result = PlotterSession(link).run(listOf(Handshake), maxAttempts = 3)
        assertTrue(result is CutResult.Failed)
        assertEquals(0, (result as CutResult.Failed).atMessage)
        assertEquals(3, link.writes.size)
    }

    @Test
    fun reportsProgress() {
        val link = FakeLink { "{\"type\":\"ack\"}" }
        val session = PlotterSession(link)
        val seen = ArrayList<Pair<Int, Int>>()
        session.onProgress = { sent, total -> seen.add(sent to total) }
        session.run(listOf(Handshake, PltCommand("TB66;"), Bye))
        assertEquals(listOf(1 to 3, 2 to 3, 3 to 3), seen)
    }
}
