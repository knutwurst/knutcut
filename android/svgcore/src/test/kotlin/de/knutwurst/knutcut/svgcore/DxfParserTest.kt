package de.knutwurst.knutcut.svgcore

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class DxfParserTest {

    // ─── Helpers ─────────────────────────────────────────────────────────────────

    private fun dxf(entities: String, header: String = "") = buildString {
        if (header.isNotEmpty()) {
            appendLine("  0\nSECTION\n  2\nHEADER")
            appendLine(header.trimIndent())
            appendLine("  0\nENDSEC")
        }
        appendLine("  0\nSECTION\n  2\nENTITIES")
        appendLine(entities.trimIndent())
        appendLine("  0\nENDSEC\n  0\nEOF")
    }

    private fun assertClose(expected: Double, actual: Double, msg: String = "", eps: Double = 1e-6) =
        assertTrue("$msg: expected $expected but was $actual", abs(actual - expected) < eps)

    // ─── LINE ────────────────────────────────────────────────────────────────────

    @Test
    fun lineHorizontal() {
        val text = dxf("""
            0
            LINE
            8
            0
            10
            0.0
            20
            0.0
            11
            100.0
            21
            0.0
        """)
        val polys = DxfParser.parse(text)
        assertEquals(1, polys.size)
        val b = Bounds.of(polys[0].points)
        assertClose(100.0, b.widthMm, "width")
        assertClose(0.0, b.heightMm, "height")
        assertFalse(polys[0].closed)
    }

    @Test
    fun lineVertical() {
        val text = dxf("""
            0
            LINE
            8
            0
            10
            0.0
            20
            0.0
            11
            0.0
            21
            50.0
        """)
        val polys = DxfParser.parse(text)
        assertEquals(1, polys.size)
        val b = Bounds.of(polys[0].points)
        assertClose(0.0, b.widthMm, "width")
        assertClose(50.0, b.heightMm, "height")
    }

    // ─── LWPOLYLINE ───────────────────────────────────────────────────────────────

    @Test
    fun lwPolylineClosedSquare() {
        // 100 × 100 mm closed square
        val text = dxf("""
            0
            LWPOLYLINE
            8
            0
            90
            4
            70
            1
            10
            0.0
            20
            0.0
            10
            100.0
            20
            0.0
            10
            100.0
            20
            100.0
            10
            0.0
            20
            100.0
        """)
        val polys = DxfParser.parse(text)
        assertEquals(1, polys.size)
        assertTrue(polys[0].closed)
        val b = Bounds.of(polys[0].points)
        assertClose(100.0, b.widthMm, "width", eps = 0.5)
        assertClose(100.0, b.heightMm, "height", eps = 0.5)
    }

    @Test
    fun lwPolylineOpenPath() {
        val text = dxf("""
            0
            LWPOLYLINE
            8
            0
            90
            3
            70
            0
            10
            0.0
            20
            0.0
            10
            50.0
            20
            0.0
            10
            50.0
            20
            30.0
        """)
        val polys = DxfParser.parse(text)
        assertEquals(1, polys.size)
        assertFalse(polys[0].closed)
        assertEquals(3, polys[0].points.size)
    }

    // ─── CIRCLE ───────────────────────────────────────────────────────────────────

    @Test
    fun circleRadius25() {
        val text = dxf("""
            0
            CIRCLE
            8
            0
            10
            0.0
            20
            0.0
            40
            25.0
        """)
        val polys = DxfParser.parse(text)
        assertEquals(1, polys.size)
        assertTrue(polys[0].closed)
        val b = Bounds.of(polys[0].points)
        // Diameter should be ≈50mm in both axes
        assertClose(50.0, b.widthMm, "diameter X", eps = 0.5)
        assertClose(50.0, b.heightMm, "diameter Y", eps = 0.5)
        // Centre should be ≈0,0
        assertClose(0.0, (b.minX + b.maxX) / 2, "centre X", eps = 0.5)
        assertClose(0.0, (b.minY + b.maxY) / 2, "centre Y", eps = 0.5)
    }

    // ─── ARC ─────────────────────────────────────────────────────────────────────

    @Test
    fun arcQuarterCircle() {
        // Quarter arc from 0° to 90°, radius 10mm, centre at origin
        val text = dxf("""
            0
            ARC
            8
            0
            10
            0.0
            20
            0.0
            40
            10.0
            50
            0.0
            51
            90.0
        """)
        val polys = DxfParser.parse(text)
        assertEquals(1, polys.size)
        assertFalse(polys[0].closed)
        // First point should be near (10, 0) → (10, 0) in screen coords
        assertClose(10.0, polys[0].points.first().xMm, "start X", eps = 0.5)
        assertClose(0.0, polys[0].points.first().yMm, "start Y", eps = 0.5)
        // Last point should be near (0, 10) in DXF → (0, -10) in screen coords (Y flip)
        assertClose(0.0, polys[0].points.last().xMm, "end X", eps = 0.5)
        assertClose(-10.0, polys[0].points.last().yMm, "end Y flipped", eps = 0.5)
    }

    // ─── Unit conversion ─────────────────────────────────────────────────────────

    @Test
    fun inchesConvertedToMm() {
        // $INSUNITS = 1 (inches), line from 0 to 1 inch → should be ≈25.4mm
        val header = """
            9
            ${'$'}INSUNITS
             70
                 1
        """
        val text = dxf("""
            0
            LINE
            8
            0
            10
            0.0
            20
            0.0
            11
            1.0
            21
            0.0
        """, header)
        val polys = DxfParser.parse(text)
        assertEquals(1, polys.size)
        assertClose(25.4, Bounds.of(polys[0].points).widthMm, "1 inch in mm", eps = 0.01)
    }

    @Test
    fun nativeMillimetresUnchanged() {
        // $INSUNITS = 4 (mm), scale factor must be 1.0
        val header = """
            9
            ${'$'}INSUNITS
             70
                 4
        """
        val text = dxf("""
            0
            LINE
            8
            0
            10
            0.0
            20
            0.0
            11
            42.0
            21
            0.0
        """, header)
        val polys = DxfParser.parse(text)
        assertClose(42.0, Bounds.of(polys[0].points).widthMm, "42mm unchanged", eps = 1e-9)
    }

    // ─── Robustness ───────────────────────────────────────────────────────────────

    @Test
    fun emptyFileReturnsNothing() {
        assertEquals(emptyList<Polyline>(), DxfParser.parse(""))
    }

    @Test
    fun noEntitiesSectionReturnsNothing() {
        assertEquals(emptyList<Polyline>(), DxfParser.parse("  0\nSECTION\n  2\nHEADER\n  0\nENDSEC\n  0\nEOF"))
    }

    @Test
    fun multipleEntities() {
        val text = dxf("""
            0
            LINE
            10
            0.0
            20
            0.0
            11
            10.0
            21
            0.0
            0
            LINE
            10
            20.0
            20
            0.0
            11
            30.0
            21
            0.0
        """)
        assertEquals(2, DxfParser.parse(text).size)
    }
}
