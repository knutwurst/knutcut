package de.knutwurst.knutcut.svgcore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlotterSessionTest {

    private class FakeLink(val responder: (Int) -> String?) : PlotterLink {
        val writes = ArrayList<String>()
        override fun write(text: String) { writes.add(text) }
        override fun readLine(timeoutMs: Long): String? = responder(writes.size)
    }

    @Test
    fun sendFramesWithIncrementingCseq() {
        val link = FakeLink { "{\"type\":\"ack\",\"success\":true}" }
        val session = PlotterSession(link)
        assertEquals("{\"type\":\"ack\",\"success\":true}", session.send(Handshake))
        assertEquals("{\"type\":\"ack\",\"success\":true}", session.send(Bye))
        assertEquals(Frame.encode(Handshake, 0), link.writes[0])
        assertEquals(Frame.encode(Bye, 1), link.writes[1])
    }

    @Test
    fun resendsOnCrcRejection() {
        var n = 0
        val link = FakeLink { n++; if (n == 1) "{\"type\":\"crc\"}" else "{\"type\":\"ack\"}" }
        assertEquals("{\"type\":\"ack\"}", PlotterSession(link).send(Handshake))
        assertEquals(2, link.writes.size)
    }

    @Test
    fun resendsOnExplicitFailure() {
        var n = 0
        val link = FakeLink { n++; if (n == 1) "{\"success\":false}" else "{\"success\":true}" }
        assertEquals("{\"success\":true}", PlotterSession(link).send(Handshake))
        assertEquals(2, link.writes.size)
    }

    @Test
    fun returnsNullAfterMaxAttempts() {
        val link = FakeLink { "{\"type\":\"crc\"}" }
        assertNull(PlotterSession(link).send(Handshake, maxAttempts = 3))
        assertEquals(3, link.writes.size)
    }

    @Test
    fun discardsStaleAckWithWrongCseqAndWaitsForTheMatchingOne() {
        // First read returns a late ack carrying an OLD cseq; it must be skipped, not accepted, and
        // the correct ack (cseq 0, matching the first send) returned — without resending.
        var reads = 0
        val link = FakeLink {
            reads++
            if (reads == 1) "{\"type\":\"ack\",\"success\":true,\"cseq\":99}"
            else "{\"type\":\"ack\",\"success\":true,\"cseq\":0}"
        }
        val session = PlotterSession(link)
        assertEquals("{\"type\":\"ack\",\"success\":true,\"cseq\":0}", session.send(Handshake))
        assertEquals("stale ack skipped, not retried", 1, link.writes.size)
    }

    @Test
    fun matchingCseqIsAccepted() {
        val link = FakeLink { "{\"type\":\"ack\",\"success\":true,\"cseq\":0}" }
        assertEquals("{\"type\":\"ack\",\"success\":true,\"cseq\":0}", PlotterSession(link).send(Handshake))
        assertEquals(1, link.writes.size)
    }

    @Test
    fun responseOkRules() {
        assertTrue(responseOk("{\"type\":\"ack\",\"success\":true}"))
        assertTrue(responseOk("{\"type\":\"pltFile\",\"index\":3}"))
        assertFalse(responseOk(null))
        assertFalse(responseOk("{\"type\":\"crc\"}"))
        assertFalse(responseOk("{\"success\":false}"))
    }

    @Test
    fun stateReadyParsing() {
        assertTrue(responseStateReady("{\"type\":\"query\",\"data\":{\"state\":1},\"cseq\":3}"))
        assertTrue(responseStateReady("{\"data\":{\"state\":true}}"))
        assertTrue(responseStateReady("{\"data\":{\"state\":3}}"))
        assertEquals(false, responseStateReady("{\"data\":{\"state\":0}}"))
        assertEquals(false, responseStateReady("{\"data\":{\"state\":false}}"))
        assertEquals(false, responseStateReady(null))
        assertEquals(false, responseStateReady("{\"type\":\"ack\"}"))
    }

    @Test
    fun stateNumberParsing() {
        assertEquals(3, responseState("{\"type\":\"query\",\"data\":{\"state\":3}}"))
        assertEquals(1, responseState("{\"data\":{\"state\":1}}"))
        assertEquals(1, responseState("{\"data\":{\"state\":true}}"))
        assertEquals(0, responseState("{\"data\":{\"state\":false}}"))
        assertEquals(null, responseState("{\"type\":\"ack\"}"))
    }
}
