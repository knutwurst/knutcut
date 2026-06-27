package de.knutwurst.knutcut.svgcore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class RasterTraceTest {

    private val BLACK = 0xFF000000.toInt()
    private val WHITE = 0xFFFFFFFF.toInt()
    private val RED = 0xFFFF0000.toInt()

    /** Build a raster from rows of chars via a legend of char -> ARGB. */
    private fun image(vararg rows: String, legend: Map<Char, Int>): RasterImage {
        val h = rows.size; val w = rows[0].length
        val px = IntArray(w * h)
        for (y in 0 until h) for (x in 0 until w) px[y * w + x] = legend.getValue(rows[y][x])
        return RasterImage(w, h, px)
    }

    /** Polyline area in mm² (shoelace, unsigned). */
    private fun area(p: Polyline): Double {
        var a = 0.0
        for (i in p.points.indices) {
            val u = p.points[i]; val v = p.points[(i + 1) % p.points.size]
            a += u.xMm * v.yMm - v.xMm * u.yMm
        }
        return abs(a) / 2.0
    }

    private fun colorFor(r: TraceResult, argb: Int): TracedColor? =
        r.colors.minByOrNull { rgbDist(it.argb, argb) }?.takeIf { rgbDist(it.argb, argb) < 0x3000 }

    private fun rgbDist(a: Int, b: Int): Int {
        val dr = ((a ushr 16) and 0xFF) - ((b ushr 16) and 0xFF)
        val dg = ((a ushr 8) and 0xFF) - ((b ushr 8) and 0xFF)
        val db = (a and 0xFF) - (b and 0xFF)
        return dr * dr + dg * dg + db * db
    }

    private fun params(numColors: Int = 2, dropBg: Boolean = false, minAreaMm2: Double = 0.5, pxPerMm: Double = 1.0) =
        TraceParams(numColors = numColors, dropBackground = dropBg, detailMm = 0.1, minAreaMm2 = minAreaMm2, pxPerMm = pxPerMm)

    @Test
    fun medianCutFindsTheDistinctColours() {
        val img = image(
            "RRWW",
            "RRWW",
            "KKWW",
            "KKWW",
            legend = mapOf('R' to RED, 'W' to WHITE, 'K' to BLACK),
        )
        val r = RasterTrace.trace(img, params(numColors = 3))
        assertEquals("palette has the three colours", 3, r.palette.size)
        assertNotNull("red traced", colorFor(r, RED))
        assertNotNull("white traced", colorFor(r, WHITE))
        assertNotNull("black traced", colorFor(r, BLACK))
    }

    @Test
    fun filledSquareIsOneContourWithTheRightArea() {
        // 4x4 black square inside a 6x6 white field. pxPerMm = 1 → area should be exactly 16 mm².
        val img = image(
            "......",
            ".####.",
            ".####.",
            ".####.",
            ".####.",
            "......",
            legend = mapOf('.' to WHITE, '#' to BLACK),
        )
        val black = colorFor(RasterTrace.trace(img, params()), BLACK)
        assertNotNull(black)
        assertEquals("one outer contour", 1, black!!.contours.size)
        assertEquals("area is 4x4 mm", 16.0, area(black.contours[0]), 0.001)
        assertEquals("a clean rectangle has 4 corners", 4, black.contours[0].points.size)
        assertTrue("contour is closed", black.contours[0].closed)
    }

    @Test
    fun squareWithAHoleTracesOuterAndInnerContours() {
        val img = image(
            "........",
            ".######.",
            ".#....#.",
            ".#....#.",
            ".#....#.",
            ".#....#.",
            ".######.",
            "........",
            legend = mapOf('.' to WHITE, '#' to BLACK),
        )
        val black = colorFor(RasterTrace.trace(img, params()), BLACK)
        assertNotNull(black)
        assertEquals("outer boundary + hole boundary", 2, black!!.contours.size)
    }

    @Test
    fun twoDisjointSquaresAreTwoContours() {
        val img = image(
            ".......",
            ".##.##.",
            ".##.##.",
            ".......",
            legend = mapOf('.' to WHITE, '#' to BLACK),
        )
        val black = colorFor(RasterTrace.trace(img, params()), BLACK)
        assertNotNull(black)
        assertEquals(2, black!!.contours.size)
    }

    @Test
    fun specklesBelowMinAreaAreDropped() {
        // A single black pixel (1 mm²) must be removed when minArea is 4 mm².
        val img = image(
            "....",
            ".#..",
            "....",
            "....",
            legend = mapOf('.' to WHITE, '#' to BLACK),
        )
        val r = RasterTrace.trace(img, params(minAreaMm2 = 4.0))
        assertNull("speckle dropped → no black layer", colorFor(r, BLACK))
    }

    @Test
    fun pxPerMmScalesTheGeometry() {
        // Same 4x4 square but at 2 px/mm → 2x2 mm → area 4 mm², bounds 2 mm wide.
        val img = image(
            "......",
            ".####.",
            ".####.",
            ".####.",
            ".####.",
            "......",
            legend = mapOf('.' to WHITE, '#' to BLACK),
        )
        val black = colorFor(RasterTrace.trace(img, params(pxPerMm = 2.0)), BLACK)
        assertNotNull(black)
        assertEquals(4.0, area(black!!.contours[0]), 0.001)
        val b = Bounds.of(black.contours[0].points)
        assertEquals(2.0, b.widthMm, 0.001)
        assertEquals(2.0, b.heightMm, 0.001)
    }

    @Test
    fun dropBackgroundRemovesTheDominantBorderColour() {
        val img = image(
            "......",
            ".####.",
            ".####.",
            ".####.",
            ".####.",
            "......",
            legend = mapOf('.' to WHITE, '#' to BLACK),
        )
        val r = RasterTrace.trace(img, params(dropBg = true))
        assertTrue("white border was detected as background", r.backgroundIndex >= 0)
        assertNull("background colour is not emitted as a layer", colorFor(r, WHITE))
        assertNotNull("foreground still traced", colorFor(r, BLACK))
    }
}
