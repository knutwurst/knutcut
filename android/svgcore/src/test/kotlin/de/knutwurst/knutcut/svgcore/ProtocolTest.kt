package de.knutwurst.knutcut.svgcore

import org.junit.Assert.assertEquals
import org.junit.Test

class ProtocolTest {

    // CRC values cross-checked against an independent CRC-16/X25 reference.

    @Test
    fun handshakeFrame() {
        assertEquals("{\"type\":\"handshake\",\"cseq\":0}74C5\r\n", Frame.encode(Handshake, 0))
    }

    @Test
    fun byeFrame() {
        assertEquals("{\"type\":\"bye\",\"cseq\":0}8C76\r\n", Frame.encode(Bye, 0))
    }

    @Test
    fun queryFrame() {
        assertEquals(
            "{\"type\":\"query\",\"action\":\"queryPulled\",\"cseq\":0}A3F1\r\n",
            Frame.encode(Query("queryPulled"), 0),
        )
    }

    @Test
    fun pltCommandFrame() {
        assertEquals("{\"type\":\"pltCommand\",\"data\":\"TB66;\",\"cseq\":0}C05B\r\n", Frame.encode(PltCommand("TB66;"), 0))
    }

    @Test
    fun setmatFrameWithCseq() {
        assertEquals(
            "{\"type\":\"pltCommand\",\"data\":\"setmat:1;\",\"cseq\":2}4FD1\r\n",
            Frame.encode(PltCommand("setmat:1;"), 2),
        )
    }

    @Test
    fun pltFileFrameForSquare() {
        val data = "PU0,0;PD1600,0;PD1600,1600;PD0,1600;PD0,0"
        assertEquals(
            "{\"type\":\"pltFile\",\"index\":0,\"total\":1,\"data\":\"$data\",\"cseq\":5}F874\r\n",
            Frame.encode(PltFile(0, 1, data), 5),
        )
    }

    @Test
    fun crcKnownValues() {
        assertEquals("74C5", Frame.crc16X25("{\"type\":\"handshake\",\"cseq\":0}"))
        assertEquals("C05B", Frame.crc16X25("{\"type\":\"pltCommand\",\"data\":\"TB66;\",\"cseq\":0}"))
    }

    @Test
    fun pathFileChunking() {
        val cmds = listOf("PU0,0", "PD10,0", "PD10,10", "PD0,10", "PD0,0")
        val files = Protocol.pathFile(cmds, chunk = 2)
        assertEquals(3, files.size)
        assertEquals(PltFile(0, 3, "PU0,0;PD10,0"), files[0])
        assertEquals(PltFile(1, 3, ";PD10,10;PD0,10"), files[1])
        assertEquals(PltFile(2, 3, ";PD0,0"), files[2])
        // concatenating the data of all chunks reproduces the single ';'-joined stream
        assertEquals(cmds.joinToString(";"), files.joinToString("") { it.data })
    }

    @Test
    fun buildCutHasExpectedShape() {
        val msgs = Protocol.buildCut(listOf("PU0,0", "PD1600,0"), CutSettings(materialId = "1", speed = 10, force = 30))
        assertEquals(Handshake, msgs.first())
        assertEquals(Bye, msgs.last())
        assert(msgs.any { it is PltCommand && it.data == "setmat:1;" })
        assert(msgs.any { it is PltCommand && it.data == "SP10;FS30;" })
        assert(msgs.any { it is PltFile })
    }
}
