package de.knutwurst.knutcut.svgcore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GpglSessionTest {

    private class FakeLink(val readResponder: () -> String?) : PlotterLink {
        val writes = ArrayList<String>()
        override fun write(text: String) { writes.add(text) }
        override fun readLine(timeoutMs: Long): String? = readResponder()
    }

    private val cameo3 = SilhouetteDevice("Cameo 3", SilhouetteFamily.CAMEO3, 304.8, 3000.0)
    private val settings = GpglCutSettings(speed = 10, pressure = 20)
    private val line = listOf(Polyline(listOf(Pt(0.0, 0.0), Pt(10.0, 0.0)), closed = false))

    @Test
    fun cutInitialisesPollsThenWritesSetupPlotTrailer() {
        val link = FakeLink { "0" } // always ready
        var lastProgress = 0f
        val ok = GpglSession(link, sleep = {}).cut(cameo3, settings, line) { lastProgress = it }

        assertTrue(ok)
        assertEquals(GpglProtocol.INIT, link.writes[0])
        assertEquals(GpglProtocol.STATUS_QUERY, link.writes[1])           // initial waitForReady
        assertEquals(GpglProtocol.delimit(GpglProtocol.setup(cameo3, settings)), link.writes[2])
        assertEquals(GpglProtocol.delimit(GpglProtocol.plot(cameo3, line)), link.writes[3])
        assertEquals(GpglProtocol.STATUS_QUERY, link.writes[4])           // ready-wait after the chunk
        assertEquals(GpglProtocol.delimit(GpglProtocol.trailer(line)), link.writes[5])
        assertEquals(6, link.writes.size)
        assertEquals(1f, lastProgress, 0f)
    }

    @Test
    fun cutAbortsWhenNeverReady() {
        val link = FakeLink { "1" } // perpetually moving
        val ok = GpglSession(link, sleep = {}).cut(cameo3, settings, line)
        assertFalse(ok)
        // only INIT plus the status polls were written; no setup/plot was sent
        assertEquals(GpglProtocol.INIT, link.writes[0])
        assertTrue(link.writes.drop(1).all { it == GpglProtocol.STATUS_QUERY })
    }

    @Test
    fun waitForReadyReturnsTrueOnceReady() {
        var n = 0
        val link = FakeLink { if (++n < 3) "1" else "0" }
        assertTrue(GpglSession(link, sleep = {}).waitForReady(attempts = 5, pollMs = 0))
    }

    @Test
    fun statusDecodesTheReply() {
        assertEquals(GpglStatus.UNLOADED, GpglSession(FakeLink { "2" }, sleep = {}).status())
    }
}
