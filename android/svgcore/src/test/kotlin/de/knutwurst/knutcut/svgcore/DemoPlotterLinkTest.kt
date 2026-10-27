package de.knutwurst.knutcut.svgcore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The demo plotter must satisfy the real [PlotterSession] exactly like a VEVOR machine would. */
class DemoPlotterLinkTest {

    private fun session(link: DemoPlotterLink = DemoPlotterLink(timeScale = 0.0)) = PlotterSession(link)

    @Test
    fun handshakeAndCommandsAreAcknowledged() {
        val s = session()
        assertNotNull(s.send(Handshake, timeoutMs = 200))
        assertTrue(responseOk(s.send(PltCommand("SP1;FS180;"), timeoutMs = 200)))
        assertTrue(responseOk(s.send(PltFile(0, 1, "PU0,0;PD100,0"), timeoutMs = 200)))
    }

    @Test
    fun repliesEchoTheSentCseq() {
        val s = PlotterSession(DemoPlotterLink(timeScale = 0.0), cseq = 41)
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
    fun chunksTakeAsLongAsTheToolWouldNeed() {
        // 100 mm cut (4000 units) at 50 mm/s = 2 s, plus 100 mm travel back at 100 mm/s = 1 s → 3 s.
        // Scaled by 0.05 → about 150 ms.
        val link = DemoPlotterLink(cutMmPerS = 50.0, travelMmPerS = 100.0, timeScale = 0.05)
        val s = PlotterSession(link)
        s.send(Handshake, timeoutMs = 200)
        val t0 = System.nanoTime()
        assertTrue(responseOk(s.send(PltFile(0, 1, "PD4000,0;PU0,0"), timeoutMs = 2000)))
        val ms = (System.nanoTime() - t0) / 1_000_000
        assertTrue("took $ms ms, expected about 150", ms in 130..600)
    }

    @Test
    fun theHeadPositionCarriesAcrossChunks() {
        // The second chunk starts where the first ended: PD back to the origin is another 100 mm.
        val link = DemoPlotterLink(cutMmPerS = 100.0, travelMmPerS = 100.0, timeScale = 0.1)
        val s = PlotterSession(link)
        s.send(Handshake, timeoutMs = 200)
        s.send(PltFile(0, 2, "PD4000,0"), timeoutMs = 2000)
        val t0 = System.nanoTime()
        s.send(PltFile(1, 2, ";PD0,0"), timeoutMs = 2000)
        val ms = (System.nanoTime() - t0) / 1_000_000
        assertTrue("took $ms ms, expected about 100", ms in 80..500)
    }

    @Test
    fun aClosedLinkGoesSilent() {
        val link = DemoPlotterLink(timeScale = 0.0)
        link.close()
        assertNull(PlotterSession(link).send(Handshake, timeoutMs = 50, maxAttempts = 1))
    }
}
