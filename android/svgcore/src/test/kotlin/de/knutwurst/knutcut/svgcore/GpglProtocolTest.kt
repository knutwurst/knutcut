package de.knutwurst.knutcut.svgcore

import org.junit.Assert.assertEquals
import org.junit.Test

class GpglProtocolTest {

    private val cameo3 = SilhouetteDevice("Cameo 3", SilhouetteFamily.CAMEO3, 304.8, 3000.0)
    private val cameo4 = SilhouetteDevice("Cameo 4", SilhouetteFamily.CAMEO4_LINE, 304.8, 3000.0)
    private val portrait2 = SilhouetteDevice("Portrait 2", SilhouetteFamily.LEGACY, 203.0, 3000.0)
    private val settings = GpglCutSettings(speed = 10, pressure = 20)

    @Test
    fun decodeStatusMapsResponseBytes() {
        assertEquals(GpglStatus.READY, GpglProtocol.decodeStatus("0"))
        assertEquals(GpglStatus.MOVING, GpglProtocol.decodeStatus("1"))
        assertEquals(GpglStatus.UNLOADED, GpglProtocol.decodeStatus("2"))
        assertEquals(GpglStatus.READY, GpglProtocol.decodeStatus("    0")) // padded
        assertEquals(GpglStatus.UNKNOWN, GpglProtocol.decodeStatus(null))
        assertEquals(GpglStatus.UNKNOWN, GpglProtocol.decodeStatus("?"))
    }

    @Test
    fun delimitTerminatesEachCommandWithEtx() {
        assertEquals("M0,0\u0003D0,200\u0003", GpglProtocol.delimit(listOf("M0,0", "D0,200")))
        assertEquals("", GpglProtocol.delimit(emptyList()))
    }

    @Test
    fun initAndStatusAreBareEscapeSequences() {
        assertEquals("\u001b\u0004", GpglProtocol.INIT)
        assertEquals("\u001b\u0005", GpglProtocol.STATUS_QUERY)
    }

    @Test
    fun cameo3Setup() {
        assertEquals(
            listOf(
                "TG0", "FN0", "TB50,0", "\\0,0", "Z60000,6096",
                "!10,1", "FX20,1", "FE0,1", "FF1,0,1", "FF1,1,1", "FC0,1,1", "FC18,1,1",
            ),
            GpglProtocol.setup(cameo3, settings),
        )
    }

    @Test
    fun cameo4LineSetupSelectsToolAndSetsAcceleration() {
        assertEquals(
            listOf(
                "TG0", "FN0", "TB50,0", "\\0,0", "Z60000,6096",
                "J1", "FX20,1", "TJ0", "!15,1", "FC0,1,1", "FE0,1",
                "FF1,0,1", "FF1,1,1", "FX20,1", "TJ3", "FC18,1,1",
            ),
            GpglProtocol.setup(cameo4, settings.copy(speed = 15)),
        )
    }

    @Test
    fun legacySetupUsesFwAndUnnumberedCommands() {
        assertEquals(
            listOf("FW132", "!10", "FX20", "FC18", "FY1", "FN0", "TB50,0", "FE0,0"),
            GpglProtocol.setup(portrait2, settings),
        )
    }

    @Test
    fun speedAndPressureClampedPerFamily() {
        // Cameo3 caps speed at 10; Cameo4-line at 30; pressure caps at 33 everywhere.
        assertEquals("!10,1", GpglProtocol.setup(cameo3, settings.copy(speed = 99))[5])
        assertEquals("!30,1", GpglProtocol.setup(cameo4, settings.copy(speed = 99))[8])
        assertEquals("FX33,1", GpglProtocol.setup(cameo3, settings.copy(pressure = 99))[6])
    }

    @Test
    fun plotAddsBoundaryOnLegacyOnly() {
        val line = listOf(Polyline(listOf(Pt(0.0, 0.0), Pt(10.0, 0.0)), closed = false))
        assertEquals(listOf("M0,0", "D0,200"), GpglProtocol.plot(cameo3, line))
        assertEquals(
            listOf("\\0,0", "Z60000,4060", "L0", "FE0,0", "FF0,0,0", "M0,0", "D0,200"),
            GpglProtocol.plot(portrait2, line),
        )
    }

    @Test
    fun trailerFeedsBelowTheLowestCut() {
        val square = listOf(
            Polyline(listOf(Pt(0.0, 0.0), Pt(40.0, 0.0), Pt(40.0, 40.0), Pt(0.0, 40.0)), closed = true),
        )
        // max y = 40 mm -> M800,0
        assertEquals(listOf("M800,0", "SO0"), GpglProtocol.trailer(square))
        assertEquals(listOf("M0,0", "SO0"), GpglProtocol.trailer(emptyList()))
    }
}
