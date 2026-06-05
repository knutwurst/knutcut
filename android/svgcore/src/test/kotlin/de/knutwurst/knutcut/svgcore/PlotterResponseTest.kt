package de.knutwurst.knutcut.svgcore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlotterResponseTest {

    @Test fun `parses the flat fields`() {
        val r = PlotterResponse.parse("""{"type":"ack","success":true,"state":3,"cseq":7}""")
        assertEquals("ack", r.type)
        assertEquals(true, r.success)
        assertEquals(3, r.state)
        assertEquals(7, r.cseq)
        assertFalse(r.rejected)
    }

    @Test fun `state can be boolean`() {
        assertEquals(1, PlotterResponse.parse("""{"state":true}""").state)
        assertEquals(0, PlotterResponse.parse("""{"state":false}""").state)
        assertNull(PlotterResponse.parse("""{"type":"x"}""").state)
    }

    @Test fun `responseOk rejects crc, failure and missing lines`() {
        assertTrue(responseOk("""{"type":"ack","success":true}"""))
        assertFalse(responseOk("""{"type":"crc"}"""))
        assertFalse(responseOk("""{"success":false}"""))
        assertFalse(responseOk("""{"crc":123,"error":"bad"}"""))
        assertFalse(responseOk(null))
    }

    @Test fun `responseStateReady follows the numeric state`() {
        assertTrue(responseStateReady("""{"state":3}"""))
        assertTrue(responseStateReady("""{"state":true}"""))
        assertFalse(responseStateReady("""{"state":0}"""))
        assertFalse(responseStateReady(null))
    }
}
