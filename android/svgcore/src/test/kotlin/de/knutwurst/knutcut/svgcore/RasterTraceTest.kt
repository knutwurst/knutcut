package de.knutwurst.knutcut.svgcore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class RasterTraceTest {

    private val BLACK = 0xFF000000.toInt()
    private val WHITE = 0xFFFFFFFF.toInt()
    private val RED = 0xFFFF0000.toInt()
    private val TRANSPARENT = 0x00000000

    /** Build a raster from rows of chars via a legend of char -> ARGB. */
    private fun image(vararg rows: String, legend: Map<Char, Int>): RasterImage {
        val h = rows.size; val w = rows[0].length
        val px = IntArray(w * h)
        for (y in 0 until h) for (x in 0 until w) px[y * w + x] = legend.getValue(rows[y][x])
        return RasterImage(w, h, px)
    }

    private fun solid(w: Int, h: Int, argb: Int) = RasterImage(w, h, IntArray(w * h) { argb })

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

    @Test
    fun lowColorCountSeparatesDarkFigureFromLightGround() {
        // A small dark figure on a mottled light background. At 2 colours the perceptual cluster MUST
        // isolate the dark figure (the user's "I expected the bunny, got grey soup" case).
        val w = 10; val h = 10; val px = IntArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            val g = if (x in 3..6 && y in 3..6) 25 else if ((x + y) % 2 == 0) 190 else 210
            px[y * w + x] = (0xFF shl 24) or (g shl 16) or (g shl 8) or g
        }
        val r = RasterTrace.trace(RasterImage(w, h, px), params(numColors = 2))
        assertEquals("two clusters", 2, r.palette.size)
        val dark = colorFor(r, BLACK)
        assertNotNull("the dark figure forms its own colour at 2 colours", dark)
        assertEquals("dark figure is a single contour", 1, dark!!.contours.size)
        assertEquals("contour matches the 4x4 figure", 16.0, area(dark.contours[0]), 0.001)
    }

    @Test
    fun twoToneLightBackgroundStillIsolatesDarkFigure() {
        // Regression guard for the seeding bug: a TWO-tone light background (warm paper + cool shadow)
        // with a small dark figure. Weighted k-means++ seeding let the second light tone steal the
        // second seed and the figure vanished into grey; pure-distance seeding must isolate it.
        val warm = 0xFFEBE6DC.toInt(); val cool = 0xFFC8D2EB.toInt(); val dark = 0xFF202028.toInt()
        val w = 40; val h = 40; val px = IntArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            px[y * w + x] = when {
                x in 5..10 && y in 5..10 -> dark   // 6x6 figure in the warm half
                x < 20 -> warm
                else -> cool
            }
        }
        val r = RasterTrace.trace(RasterImage(w, h, px), params(numColors = 2))
        assertEquals(2, r.palette.size)
        val figure = colorFor(r, BLACK)
        assertNotNull("dark figure survives a multi-tone light background at 2 colours", figure)
        assertEquals("figure is one contour", 1, figure!!.contours.size)
        assertEquals("figure area is 6x6", 36.0, area(figure.contours[0]), 0.001)
    }

    @Test
    fun outputIsDeterministicAcrossRuns() {
        // The headline property of the k-means rewrite: same input → byte-identical output every run.
        val warm = 0xFFEBE6DC.toInt(); val cool = 0xFFC8D2EB.toInt(); val dark = 0xFF202028.toInt()
        val w = 30; val h = 30; val px = IntArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            px[y * w + x] = if (x in 4..9 && y in 4..9) dark else if (x < 15) warm else cool
        }
        val img = RasterImage(w, h, px)
        val a = RasterTrace.trace(img, params(numColors = 4))
        val b = RasterTrace.trace(img, params(numColors = 4))
        assertArrayEquals("same palette", a.palette, b.palette)
        assertEquals("same colour count", a.colors.size, b.colors.size)
        assertEquals(
            "same geometry",
            a.colors.map { c -> c.argb to c.contours.map { it.points } },
            b.colors.map { c -> c.argb to c.contours.map { it.points } },
        )
    }

    @Test
    fun moreColoursThanDistinctClampsWithNoEmptyOrDuplicateEntries() {
        val img = image(
            "RRWW",
            "RRWW",
            "KKWW",
            "KKWW",
            legend = mapOf('R' to RED, 'W' to WHITE, 'K' to BLACK),
        )
        val r = RasterTrace.trace(img, params(numColors = 12))
        assertEquals("palette clamps to the distinct count", 3, r.palette.size)
        assertEquals("no duplicate palette entries", 3, r.palette.toSet().size)
        assertNotNull(colorFor(r, RED)); assertNotNull(colorFor(r, WHITE)); assertNotNull(colorFor(r, BLACK))
    }

    @Test
    fun fullyTransparentImageYieldsAnEmptyResult() {
        val r = RasterTrace.trace(solid(5, 4, TRANSPARENT), params())
        assertEquals(0, r.palette.size)
        assertTrue(r.colors.isEmpty())
        assertEquals(-1, r.backgroundIndex)
        assertEquals("size preserved", 5, r.width); assertEquals(4, r.height)
        assertTrue("every pixel is transparent (-1)", r.indexMap.all { it == -1 })
    }

    @Test
    fun transparentBorderIsNotDroppedAndOpaqueSubjectIsTraced() {
        // Opaque red square on a fully-transparent field (clipart with alpha). With drop-background on,
        // the transparent border drops nothing and the subject still traces.
        val w = 8; val h = 8; val px = IntArray(w * h) { TRANSPARENT }
        for (y in 2..5) for (x in 2..5) px[y * w + x] = RED
        val r = RasterTrace.trace(RasterImage(w, h, px), params(dropBg = true))
        assertEquals("transparent border → nothing detected as background", -1, r.backgroundIndex)
        val red = colorFor(r, RED)
        assertNotNull(red)
        assertEquals(1, red!!.contours.size)
        assertEquals(16.0, area(red.contours[0]), 0.001)
    }

    @Test
    fun cropLimitsTracingToTheSelectedRegion() {
        val img = image(
            ".............",
            ".##......##..",
            ".##......##..",
            ".............",
            legend = mapOf('.' to WHITE, '#' to BLACK),
        )
        val black = colorFor(RasterTrace.trace(img, params().copy(crop = CropRect(8, 0, 5, 4))), BLACK)
        assertNotNull(black)
        assertEquals("only the cropped square is traced", 1, black!!.contours.size)
        assertEquals("the 2x2 square's area", 4.0, area(black.contours[0]), 0.001)
        assertEquals("coordinates are crop-local", 1.0, Bounds.of(black.contours[0].points).minX, 0.001)
    }

    private fun twoSquares() = image(
        ".............",
        ".##......##..",
        ".##......##..",
        ".............",
        legend = mapOf('.' to WHITE, '#' to BLACK),
    )

    @Test
    fun cropOverrunIsClampedAndDoesNotCrash() {
        val black = colorFor(RasterTrace.trace(twoSquares(), params().copy(crop = CropRect(8, 0, 99, 99))), BLACK)
        assertNotNull(black)
        assertEquals("clamped to the right square only", 1, black!!.contours.size)
        assertEquals(4.0, area(black.contours[0]), 0.001)
    }

    @Test
    fun cropWithNegativeOriginIsClampedToZero() {
        val black = colorFor(RasterTrace.trace(twoSquares(), params().copy(crop = CropRect(-5, 0, 8, 4))), BLACK)
        assertNotNull(black)
        assertEquals("clamped origin includes the left square only", 1, black!!.contours.size)
        assertEquals(4.0, area(black.contours[0]), 0.001)
    }

    @Test
    fun degenerateCropFallsBackToTheWholeImage() {
        val black = colorFor(RasterTrace.trace(twoSquares(), params().copy(crop = CropRect(0, 0, 0, 0))), BLACK)
        assertNotNull(black)
        assertEquals("zero-size crop traces the full image", 2, black!!.contours.size)
    }

    @Test
    fun fullImageCropEqualsNoCrop() {
        val img = image(
            "......",
            ".####.",
            ".####.",
            ".####.",
            ".####.",
            "......",
            legend = mapOf('.' to WHITE, '#' to BLACK),
        )
        val withoutCrop = colorFor(RasterTrace.trace(img, params()), BLACK)!!
        val withFullCrop = colorFor(RasterTrace.trace(img, params().copy(crop = CropRect(0, 0, 6, 6))), BLACK)!!
        assertEquals(withoutCrop.contours.size, withFullCrop.contours.size)
        assertEquals(area(withoutCrop.contours[0]), area(withFullCrop.contours[0]), 0.001)
        assertEquals(withoutCrop.contours[0].points, withFullCrop.contours[0].points)
    }

    @Test
    fun uniformImageWithDropBackgroundYieldsNoCuttableColours() {
        val img = solid(6, 6, RED)
        assertEquals("the lone colour still traces with drop off", 1, RasterTrace.trace(img, params()).palette.size)
        val r = RasterTrace.trace(img, params(dropBg = true))
        assertTrue("the single colour is the background", r.backgroundIndex >= 0)
        assertTrue("nothing left to cut", r.colors.isEmpty())
    }
}
