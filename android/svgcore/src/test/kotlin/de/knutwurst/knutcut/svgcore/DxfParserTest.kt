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

    // ─── Bulge arcs (LWPOLYLINE) ────────────────────────────────────────────────────

    @Test
    fun lwPolylineSemicircleBulge() {
        // Open polyline from (0,0) to (100,0) with bulge=1.0 → a semicircle of radius 50.
        // Positive bulge is CCW. With Y flipped, the arc bows to screen +Y (DXF -Y).
        val text = dxf("""
            0
            LWPOLYLINE
            8
            0
            90
            2
            70
            0
            10
            0.0
            20
            0.0
            42
            1.0
            10
            100.0
            20
            0.0
        """)
        val polys = DxfParser.parse(text)
        assertEquals(1, polys.size)
        val b = Bounds.of(polys[0].points)
        // Width spans the diameter, height spans the radius (sagitta of a semicircle == radius).
        assertClose(100.0, b.widthMm, "semicircle width", eps = 0.5)
        assertClose(50.0, b.heightMm, "semicircle bulge height", eps = 0.5)
        // Endpoints land on the two vertices.
        assertClose(0.0, polys[0].points.first().xMm, "start x", eps = 0.5)
        assertClose(100.0, polys[0].points.last().xMm, "end x", eps = 0.5)
    }

    @Test
    fun lwPolylineQuarterCircleBulge() {
        // bulge = tan(90°/4) = tan(22.5°) ≈ 0.41421356 → quarter circle.
        val text = dxf("""
            0
            LWPOLYLINE
            8
            0
            90
            2
            70
            0
            10
            0.0
            20
            0.0
            42
            0.41421356
            10
            100.0
            20
            0.0
        """)
        val polys = DxfParser.parse(text)
        assertEquals(1, polys.size)
        val b = Bounds.of(polys[0].points)
        // Quarter circle through a 100mm chord: radius = chord / (2 sin(45°)) ≈ 70.71.
        // Sagitta (bulge height) = r (1 - cos(45°)) ≈ 20.71.
        assertClose(100.0, b.widthMm, "quarter width", eps = 0.5)
        assertClose(20.71, b.heightMm, "quarter bulge height", eps = 0.5)
    }

    // ─── ELLIPSE ────────────────────────────────────────────────────────────────────

    @Test
    fun ellipseFull() {
        // Centre (0,0), major axis endpoint (40,0), ratio 0.5 → 80mm × 40mm ellipse.
        val text = dxf("""
            0
            ELLIPSE
            8
            0
            10
            0.0
            20
            0.0
            11
            40.0
            21
            0.0
            40
            0.5
            41
            0.0
            42
            6.283185307179586
        """)
        val polys = DxfParser.parse(text)
        assertEquals(1, polys.size)
        assertTrue(polys[0].closed)
        val b = Bounds.of(polys[0].points)
        assertClose(80.0, b.widthMm, "ellipse width", eps = 0.5)
        assertClose(40.0, b.heightMm, "ellipse height", eps = 0.5)
    }

    @Test
    fun ellipseHalf() {
        // Half ellipse (0 to π): upper half in DXF, so full major width, half minor height.
        val text = dxf("""
            0
            ELLIPSE
            8
            0
            10
            0.0
            20
            0.0
            11
            40.0
            21
            0.0
            40
            0.5
            41
            0.0
            42
            3.141592653589793
        """)
        val polys = DxfParser.parse(text)
        assertEquals(1, polys.size)
        assertFalse(polys[0].closed)
        val b = Bounds.of(polys[0].points)
        assertClose(80.0, b.widthMm, "half-ellipse width", eps = 0.5)
        assertClose(20.0, b.heightMm, "half-ellipse height", eps = 0.5)
    }

    // ─── SPLINE ─────────────────────────────────────────────────────────────────────

    @Test
    fun splineOpenCubicEndpoints() {
        // Open cubic B-spline, 4 control points, clamped knots → endpoints == first/last ctrl pts.
        val text = dxf("""
            0
            SPLINE
            8
            0
            70
            8
            71
            3
            10
            0.0
            20
            0.0
            10
            10.0
            20
            30.0
            10
            40.0
            20
            30.0
            10
            50.0
            20
            0.0
        """)
        val polys = DxfParser.parse(text)
        assertEquals(1, polys.size)
        val pts = polys[0].points
        // First control point (0,0) → (0,0); last (50,0) → (50,0). Y flip is 0 here.
        assertClose(0.0, pts.first().xMm, "spline start x", eps = 0.01)
        assertClose(0.0, pts.first().yMm, "spline start y", eps = 0.01)
        assertClose(50.0, pts.last().xMm, "spline end x", eps = 0.01)
        assertClose(0.0, pts.last().yMm, "spline end y", eps = 0.01)
    }

    @Test
    fun splineClosedClosesGeometry() {
        // Closed/periodic flag (bit 1) → last point must equal first.
        val text = dxf("""
            0
            SPLINE
            8
            0
            70
            1
            71
            3
            10
            0.0
            20
            0.0
            10
            10.0
            20
            30.0
            10
            40.0
            20
            30.0
            10
            50.0
            20
            0.0
        """)
        val polys = DxfParser.parse(text)
        assertEquals(1, polys.size)
        assertTrue(polys[0].closed)
        assertEquals(polys[0].points.first(), polys[0].points.last())
    }

    @Test
    fun splineFitPointsOnlyInterpolated() {
        // Only fit points (11/21), no control points (10/20) → polyline through fit points.
        val text = dxf("""
            0
            SPLINE
            8
            0
            70
            0
            71
            3
            11
            0.0
            21
            0.0
            11
            20.0
            21
            10.0
            11
            40.0
            21
            0.0
        """)
        val result = DxfParser.parseShapes(text)
        // Interpolated, so not skipped.
        assertEquals(0, result.skipped)
        val polys = result.shapes.flatMap { it.polylines }
        assertEquals(1, polys.size)
        val b = Bounds.of(polys[0].points)
        assertClose(40.0, b.widthMm, "fit-point width", eps = 0.01)
    }

    @Test
    fun splineNoUsablePointsCountedSkipped() {
        // A SPLINE with a single fit point and no control points → no geometry → skipped.
        val text = dxf("""
            0
            SPLINE
            8
            0
            70
            0
            71
            3
            11
            5.0
            21
            5.0
        """)
        val result = DxfParser.parseShapes(text)
        assertEquals(1, result.skipped)
        assertTrue(result.shapes.isEmpty())
    }

    // ─── Old-style POLYLINE ──────────────────────────────────────────────────────────

    @Test
    fun oldPolylineBulgeVertexInterpolated() {
        // POLYLINE/VERTEX/SEQEND with a semicircle bulge on the first vertex.
        val text = dxf("""
            0
            POLYLINE
            8
            0
            70
            0
            0
            VERTEX
            10
            0.0
            20
            0.0
            42
            1.0
            0
            VERTEX
            10
            100.0
            20
            0.0
            0
            SEQEND
        """)
        val polys = DxfParser.parse(text)
        assertEquals(1, polys.size)
        // Bulge interpolation produces many more than 2 points.
        assertTrue("expected arc interpolation", polys[0].points.size > 2)
        val b = Bounds.of(polys[0].points)
        assertClose(100.0, b.widthMm, "old-poly bulge width", eps = 0.5)
        assertClose(50.0, b.heightMm, "old-poly bulge height", eps = 0.5)
    }

    @Test
    fun oldPolylineInBlocksSectionIgnored() {
        // A POLYLINE living in a BLOCKS section must NOT be parsed (only ENTITIES counts).
        val text = buildString {
            appendLine("  0\nSECTION\n  2\nBLOCKS")
            appendLine("""
                0
                POLYLINE
                70
                0
                0
                VERTEX
                10
                0.0
                20
                0.0
                0
                VERTEX
                10
                10.0
                20
                0.0
                0
                SEQEND
            """.trimIndent())
            appendLine("  0\nENDSEC")
            appendLine("  0\nSECTION\n  2\nENTITIES")
            appendLine("""
                0
                LINE
                10
                0.0
                20
                0.0
                11
                5.0
                21
                0.0
            """.trimIndent())
            appendLine("  0\nENDSEC\n  0\nEOF")
        }
        val polys = DxfParser.parse(text)
        // Only the LINE in ENTITIES should appear.
        assertEquals(1, polys.size)
        assertClose(5.0, Bounds.of(polys[0].points).widthMm, "only entities line", eps = 0.01)
    }

    // ─── Units: cm and metres ────────────────────────────────────────────────────────

    @Test
    fun centimetresConvertedToMm() {
        val header = """
            9
            ${'$'}INSUNITS
             70
                 5
        """
        val text = dxf("""
            0
            LINE
            10
            0.0
            20
            0.0
            11
            3.0
            21
            0.0
        """, header)
        assertClose(30.0, Bounds.of(DxfParser.parse(text)[0].points).widthMm, "3cm → 30mm", eps = 1e-9)
    }

    @Test
    fun metresConvertedToMm() {
        val header = """
            9
            ${'$'}INSUNITS
             70
                 6
        """
        val text = dxf("""
            0
            LINE
            10
            0.0
            20
            0.0
            11
            2.0
            21
            0.0
        """, header)
        assertClose(2000.0, Bounds.of(DxfParser.parse(text)[0].points).widthMm, "2m → 2000mm", eps = 1e-9)
    }

    // ─── looksLikeDxf ────────────────────────────────────────────────────────────────

    @Test
    fun looksLikeDxfPositive() {
        val text = dxf("""
            0
            LINE
            10
            0.0
            20
            0.0
            11
            5.0
            21
            0.0
        """)
        assertTrue(DxfParser.looksLikeDxf(text))
    }

    @Test
    fun looksLikeDxfRejectsSvg() {
        assertFalse(DxfParser.looksLikeDxf("<svg xmlns=\"http://www.w3.org/2000/svg\"><path d=\"M0 0\"/></svg>"))
    }

    @Test
    fun looksLikeDxfRejectsPltWithWordSection() {
        // A HPGL/PLT-ish blob that merely contains the substring SECTION must not be misdetected.
        assertFalse(DxfParser.looksLikeDxf("IN;PU0,0;PD100,0;SECTION HEADER COMMENT;PU;SP0;"))
    }

    @Test
    fun looksLikeDxfRejectsEmpty() {
        assertFalse(DxfParser.looksLikeDxf(""))
        assertFalse(DxfParser.looksLikeDxf("   \n  \n"))
    }

    // ─── parseShapes: layer grouping + colour ────────────────────────────────────────

    @Test
    fun parseShapesGroupsByLayer() {
        val text = dxf("""
            0
            LINE
            8
            A
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
            8
            A
            10
            0.0
            20
            5.0
            11
            10.0
            21
            5.0
            0
            LINE
            8
            B
            10
            0.0
            20
            10.0
            11
            10.0
            21
            10.0
        """)
        val shapes = DxfParser.parseShapes(text).shapes
        assertEquals(2, shapes.size)
        assertEquals("A", shapes[0].name)
        assertEquals(2, shapes[0].polylines.size)
        assertEquals("B", shapes[1].name)
        assertEquals(1, shapes[1].polylines.size)
    }

    @Test
    fun parseShapesMapsAciColour() {
        val text = dxf("""
            0
            LINE
            8
            red
            62
            1
            10
            0.0
            20
            0.0
            11
            10.0
            21
            0.0
        """)
        val shapes = DxfParser.parseShapes(text).shapes
        assertEquals(1, shapes.size)
        assertEquals(0xFFFF0000.toInt(), shapes[0].colorArgb)
    }

    @Test
    fun parseShapesBlankLayerNamedDxf() {
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
        """)
        val shapes = DxfParser.parseShapes(text).shapes
        assertEquals(1, shapes.size)
        assertEquals("DXF", shapes[0].name)
        assertNull(shapes[0].colorArgb)
    }

    // ─── Entity order preserved ──────────────────────────────────────────────────────

    @Test
    fun entityOrderPreserved() {
        // LINE (2 pts) → POLYLINE (3 pts, open) → CIRCLE (closed). Order must hold.
        val text = dxf("""
            0
            LINE
            10
            0.0
            20
            0.0
            11
            5.0
            21
            0.0
            0
            POLYLINE
            70
            0
            0
            VERTEX
            10
            0.0
            20
            0.0
            0
            VERTEX
            10
            10.0
            20
            0.0
            0
            VERTEX
            10
            10.0
            20
            10.0
            0
            SEQEND
            0
            CIRCLE
            10
            0.0
            20
            0.0
            40
            25.0
        """)
        val polys = DxfParser.parse(text)
        assertEquals(3, polys.size)
        // 1: LINE → exactly 2 points, open.
        assertEquals(2, polys[0].points.size)
        assertFalse(polys[0].closed)
        // 2: POLYLINE → 3 points, open.
        assertEquals(3, polys[1].points.size)
        assertFalse(polys[1].closed)
        // 3: CIRCLE → closed.
        assertTrue(polys[2].closed)
    }
}
