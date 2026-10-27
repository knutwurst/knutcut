package de.knutwurst.knutcut.svgcore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The demo plotter must satisfy the real [PlotterSession] exactly like a VEVOR machine would. */
class DemoPlotterLinkTest {

    private fun session(link: DemoPlotterLink = DemoPlotterLink(chunkDelayMs = 0)) = PlotterSession(link)

    @Test
    fun handshakeAndCommandsAreAcknowledged() {
        val s = session()
        assertNotNull(s.send(Handshake, timeoutMs = 200))
        assertTrue(responseOk(s.send(PltCommand("SP1;FS180;"), timeoutMs = 200)))
        assertTrue(responseOk(s.send(PltFile(0, 1, "PU0,0;PD100,0"), timeoutMs = 200)))
    }

    @Test
    fun repliesEchoTheSentCseq() {
        val s = PlotterSession(DemoPlotterLink(chunkDelayMs = 0), cseq = 41)
        val resp = s.send(Handshake, timeoutMs = 200)
        assertEquals(41, PlotterResponse.parse(resp!!).cseq)
    }

    @Test
    fun materialAndStartKeyBecomeReadyAfterOnePoll() {
        val s = session()
        s.send(Handshake, timeoutMs = 200)
        assertEquals(1, responseState(s.send(Query("queryMaterial"), timeoutMs = 200)))
        assertEquals(3, responseState(s.send(Query("queryMaterial"), timeoutMs = 200)))
        assertFalse(responseStateReady(s.send(Query("queryStartKey"), timeoutMs = 200)))
        assertTrue(responseStateReady(s.send(Query("queryStartKey"), timeoutMs = 200)))
    }

    @Test
    fun aNewHandshakeStartsTheKeyPromptsAgain() {
        val s = session()
        s.send(Handshake, timeoutMs = 200)
        s.send(Query("queryMaterial"), timeoutMs = 200)
        assertEquals(3, responseState(s.send(Query("queryMaterial"), timeoutMs = 200)))
        s.send(Handshake, timeoutMs = 200)
        assertEquals(1, responseState(s.send(Query("queryMaterial"), timeoutMs = 200)))
    }

    @Test
    fun aClosedLinkGoesSilent() {
        val link = DemoPlotterLink(chunkDelayMs = 0)
        link.close()
        assertNull(PlotterSession(link).send(Handshake, timeoutMs = 50, maxAttempts = 1))
    }
}
