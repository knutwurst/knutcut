package de.knutwurst.knutcut.svgcore

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Posterize-trace a raster image (PNG/JPG/BMP/…) into cuttable vector contours.
 *
 * The pipeline is, in order: median-cut colour quantisation → optional background drop → per-colour
 * marching-squares boundary tracing → RDP simplification → millimetre [Polyline]s. It is pure Kotlin
 * (no Android types) so it runs in JVM unit tests; the app feeds it an ARGB pixel array decoded from
 * the picked image and turns each [TracedColor] into a coloured layer.
 *
 * Photographs trace messily by nature — this targets high-contrast art, logos and clipart. The
 * colour count and speckle/detail knobs are the mitigation.
 */

/** An ARGB raster (row-major, length == width*height) to vectorise. */
class RasterImage(val width: Int, val height: Int, val pixels: IntArray) {
    init { require(pixels.size == width * height) { "pixels (${pixels.size}) != width*height ($width*$height)" } }
}

/** A pixel-space rectangle to restrict tracing to (so a single object can be isolated). */
data class CropRect(val x: Int, val y: Int, val w: Int, val h: Int)

/** Tunable parameters for [RasterTrace.trace]. */
data class TraceParams(
    /** Palette size for the posterisation (clamped to 2..12). */
    val numColors: Int = 6,
    /** Drop the colour that dominates the (crop) border, so a solid background isn't cut. */
    val dropBackground: Boolean = true,
    /** RDP tolerance in mm: higher = fewer points / smoother staircases, lower = more faithful. */
    val detailMm: Double = 0.4,
    /** Contours whose area is below this (mm²) are discarded as speckle. */
    val minAreaMm2: Double = 4.0,
    /** Image pixels per millimetre — sets the real-world size of the traced geometry. */
    val pxPerMm: Double = 4.0,
    /** Round the pixel staircase into swung curves (corner-preserving). Off = raw polygon. */
    val smooth: Boolean = true,
    /** Only trace pixels inside this rectangle; null traces the whole image. */
    val crop: CropRect? = null,
)

/** One quantised colour and its closed contours (outer boundaries and holes), in millimetres. */
data class TracedColor(val argb: Int, val contours: List<Polyline>)

/**
 * Trace output: [colors] are the cuttable layers, ordered largest-area first so big shapes sit
 * behind small ones. [palette] + [indexMap] (one palette index per pixel, -1 = transparent) let a
 * caller paint a posterised preview without re-quantising. [backgroundIndex] is the dropped colour
 * (or -1 when none was dropped).
 */
data class TraceResult(
    val colors: List<TracedColor>,
    val palette: IntArray,
    val indexMap: IntArray,
    val backgroundIndex: Int,
    val width: Int,
    val height: Int,
)

object RasterTrace {
    /** Pixels with alpha below this count as transparent: excluded from quantising and never cut. */
    private const val ALPHA_CUTOFF = 128

    /**
     * [shouldContinue] is polled at coarse boundaries (k-means iterations, per traced colour) so a
     * caller can abandon a superseded recompute instead of burning CPU; this pure code has no other
     * cancellation. A cancelled run returns whatever it has built so far (the caller discards it).
     */
    fun trace(img: RasterImage, params: TraceParams, shouldContinue: () -> Boolean = { true }): TraceResult {
        val k = params.numColors.coerceIn(2, 12)
        val region = cropOf(img, params.crop)
        val (palette, indexMap) = quantize(region, k, shouldContinue)
        if (palette.isEmpty()) return TraceResult(emptyList(), palette, indexMap, -1, region.width, region.height)

        val bg = if (params.dropBackground) detectBackground(region.width, region.height, indexMap, palette.size) else -1

        // Population per palette colour, to order layers (largest first) and skip empty colours.
        val counts = IntArray(palette.size)
        for (idx in indexMap) if (idx >= 0) counts[idx]++
        val order = palette.indices.sortedByDescending { counts[it] }

        val colors = ArrayList<TracedColor>()
        for (ci in order) {
            if (!shouldContinue()) break
            if (ci == bg || counts[ci] == 0) continue
            val contours = traceColor(region.width, region.height, indexMap, ci, params)
            if (contours.isNotEmpty()) colors.add(TracedColor(palette[ci], contours))
        }
        return TraceResult(colors, palette, indexMap, bg, region.width, region.height)
    }

    /** The pixels inside [crop] as a standalone image (clamped to bounds); the whole image when null. */
    private fun cropOf(img: RasterImage, crop: CropRect?): RasterImage {
        if (crop == null) return img
        val x0 = crop.x.coerceIn(0, img.width); val y0 = crop.y.coerceIn(0, img.height)
        val x1 = (crop.x + crop.w).coerceIn(x0, img.width); val y1 = (crop.y + crop.h).coerceIn(y0, img.height)
        val w = x1 - x0; val h = y1 - y0
        if (w <= 0 || h <= 0 || (w == img.width && h == img.height)) return img
        val px = IntArray(w * h)
        for (yy in 0 until h) System.arraycopy(img.pixels, (y0 + yy) * img.width + x0, px, yy * w, w)
        return RasterImage(w, h, px)
    }

    // ---------------------------------------------------------------------------
    // Perceptual (CIELAB k-means) colour quantisation
    // ---------------------------------------------------------------------------

    private const val KMEANS_ITERS = 16

    /**
     * Quantise to at most [k] colours by k-means in CIELAB, so clusters separate by perceived
     * lightness/chroma rather than raw RGB population. That is what lets a low colour count split
     * dark-vs-light (the figure pops out) instead of averaging into mush. Deterministic: weighted
     * k-means++ seeding (no RNG) plus a fixed iteration cap.
     *
     * Returns (palette as packed 0xFFRRGGBB, per-pixel palette index; -1 for transparent pixels).
     */
    private fun quantize(img: RasterImage, k: Int, shouldContinue: () -> Boolean = { true }): Pair<IntArray, IntArray> {
        // 5-bit/channel histogram of opaque pixels (true colour sums for accurate averages). Clustering
        // runs over the ≤32768 populated cells, not per pixel, so Lab conversion stays cheap.
        val cntC = IntArray(1 shl 15); val sumR = LongArray(1 shl 15); val sumG = LongArray(1 shl 15); val sumB = LongArray(1 shl 15)
        for (p in img.pixels) {
            if (((p ushr 24) and 0xFF) < ALPHA_CUTOFF) continue
            val r = (p ushr 16) and 0xFF; val g = (p ushr 8) and 0xFF; val b = p and 0xFF
            val bin = ((r ushr 3) shl 10) or ((g ushr 3) shl 5) or (b ushr 3)
            cntC[bin]++; sumR[bin] += r; sumG[bin] += g; sumB[bin] += b
        }
        val keys = ArrayList<Int>(); val wt = ArrayList<Int>()
        val rAvg = ArrayList<Double>(); val gAvg = ArrayList<Double>(); val bAvg = ArrayList<Double>()
        for (key in 0 until (1 shl 15)) {
            val c = cntC[key]; if (c == 0) continue
            keys.add(key); wt.add(c)
            rAvg.add(sumR[key].toDouble() / c); gAvg.add(sumG[key].toDouble() / c); bAvg.add(sumB[key].toDouble() / c)
        }
        val m = keys.size
        if (m == 0) return IntArray(0) to IntArray(img.pixels.size) { -1 }

        val labL = DoubleArray(m); val labA = DoubleArray(m); val labB = DoubleArray(m)
        for (i in 0 until m) { val lab = rgbToLab(rAvg[i], gAvg[i], bAvg[i]); labL[i] = lab[0]; labA[i] = lab[1]; labB[i] = lab[2] }

        val kk = minOf(k, m)
        // Deterministic k-means++ seeding: most-populous cell first, then the cell FARTHEST in Lab from
        // the nearest existing seed (pure distance², NOT weighted by population). This is the key to the
        // user's "expected the subject, got grey soup": a small but very different region (the dark
        // figure) must win a seed over a large secondary background tone. Population still biases the
        // centroid UPDATE below, so the palette colours stay representative.
        val seeds = IntArray(kk)
        seeds[0] = (0 until m).maxByOrNull { wt[it] } ?: 0
        val d2 = DoubleArray(m) { labDist2(labL, labA, labB, it, labL[seeds[0]], labA[seeds[0]], labB[seeds[0]]) }
        for (c in 1 until kk) {
            var bestIdx = 0; var bestScore = -1.0
            for (i in 0 until m) { if (d2[i] > bestScore) { bestScore = d2[i]; bestIdx = i } }
            seeds[c] = bestIdx
            for (i in 0 until m) { val nd = labDist2(labL, labA, labB, i, labL[bestIdx], labA[bestIdx], labB[bestIdx]); if (nd < d2[i]) d2[i] = nd }
        }
        val cL = DoubleArray(kk) { labL[seeds[it]] }; val cA = DoubleArray(kk) { labA[seeds[it]] }; val cB = DoubleArray(kk) { labB[seeds[it]] }
        val assign = IntArray(m) { -1 }
        for (iter in 0 until KMEANS_ITERS) {
            if (!shouldContinue()) break
            var changed = false
            for (i in 0 until m) {
                var bj = 0; var bd = Double.MAX_VALUE
                for (j in 0 until kk) {
                    val dd = sq(labL[i] - cL[j]) + sq(labA[i] - cA[j]) + sq(labB[i] - cB[j])
                    if (dd < bd) { bd = dd; bj = j }
                }
                if (assign[i] != bj) { assign[i] = bj; changed = true }
            }
            val sL = DoubleArray(kk); val sA = DoubleArray(kk); val sB = DoubleArray(kk); val sw = DoubleArray(kk)
            for (i in 0 until m) { val j = assign[i]; val w = wt[i].toDouble(); sL[j] += labL[i] * w; sA[j] += labA[i] * w; sB[j] += labB[i] * w; sw[j] += w }
            for (j in 0 until kk) if (sw[j] > 0) { cL[j] = sL[j] / sw[j]; cA[j] = sA[j] / sw[j]; cB[j] = sB[j] / sw[j] }
            if (!changed) break
        }

        // Palette colour = count-weighted average RGB of each cluster's cells; empty clusters dropped.
        val accR = DoubleArray(kk); val accG = DoubleArray(kk); val accB = DoubleArray(kk); val accW = DoubleArray(kk)
        for (i in 0 until m) { val j = assign[i]; val w = wt[i].toDouble(); accR[j] += rAvg[i] * w; accG[j] += gAvg[i] * w; accB[j] += bAvg[i] * w; accW[j] += w }
        val clusterToPalette = IntArray(kk) { -1 }
        val paletteList = ArrayList<Int>(kk)
        for (j in 0 until kk) {
            if (accW[j] <= 0) continue
            clusterToPalette[j] = paletteList.size
            val r = (accR[j] / accW[j]).roundToInt().coerceIn(0, 255)
            val g = (accG[j] / accW[j]).roundToInt().coerceIn(0, 255)
            val b = (accB[j] / accW[j]).roundToInt().coerceIn(0, 255)
            paletteList.add((0xFF shl 24) or (r shl 16) or (g shl 8) or b)
        }
        val palette = paletteList.toIntArray()

        val keyToPalette = HashMap<Int, Int>(m * 2)
        for (i in 0 until m) keyToPalette[keys[i]] = clusterToPalette[assign[i]]
        val indexMap = IntArray(img.pixels.size)
        for (i in img.pixels.indices) {
            val p = img.pixels[i]
            indexMap[i] = if (((p ushr 24) and 0xFF) < ALPHA_CUTOFF) -1
            else keyToPalette[(((p ushr 16) and 0xFF) ushr 3 shl 10) or (((p ushr 8) and 0xFF) ushr 3 shl 5) or ((p and 0xFF) ushr 3)] ?: 0
        }
        return palette to indexMap
    }

    private fun sq(x: Double) = x * x
    private fun labDist2(L: DoubleArray, A: DoubleArray, B: DoubleArray, i: Int, l: Double, a: Double, b: Double) =
        sq(L[i] - l) + sq(A[i] - a) + sq(B[i] - b)

    /** sRGB (channels 0..255) → CIELAB (D65). Called only per histogram cell, so the cost is bounded. */
    private fun rgbToLab(r: Double, g: Double, b: Double): DoubleArray {
        fun lin(c: Double): Double { val cs = c / 255.0; return if (cs <= 0.04045) cs / 12.92 else Math.pow((cs + 0.055) / 1.055, 2.4) }
        val rl = lin(r); val gl = lin(g); val bl = lin(b)
        val x = (rl * 0.4124 + gl * 0.3576 + bl * 0.1805) / 0.95047
        val y = (rl * 0.2126 + gl * 0.7152 + bl * 0.0722)
        val z = (rl * 0.0193 + gl * 0.1192 + bl * 0.9505) / 1.08883
        fun f(t: Double): Double = if (t > 0.008856) Math.cbrt(t) else (7.787 * t + 16.0 / 116.0)
        val fx = f(x); val fy = f(y); val fz = f(z)
        return doubleArrayOf(116.0 * fy - 16.0, 500.0 * (fx - fy), 200.0 * (fy - fz))
    }

    /** The palette index that dominates the 1-px image border, or -1 when the border is transparent. */
    private fun detectBackground(w: Int, h: Int, idx: IntArray, paletteSize: Int): Int {
        if (w == 0 || h == 0) return -1
        val votes = IntArray(paletteSize)
        var transparent = 0; var opaque = 0
        fun vote(x: Int, y: Int) { val i = idx[y * w + x]; if (i >= 0) { votes[i]++; opaque++ } else transparent++ }
        for (x in 0 until w) { vote(x, 0); vote(x, h - 1) }
        for (y in 0 until h) { vote(0, y); vote(w - 1, y) }
        if (opaque == 0 || transparent > opaque) return -1
        var best = 0; for (i in 1 until paletteSize) if (votes[i] > votes[best]) best = i
        return best
    }

    // ---------------------------------------------------------------------------
    // Marching-squares boundary tracing
    // ---------------------------------------------------------------------------

    /**
     * Trace every boundary of the region where the palette index equals [ci]. Boundary unit-edges are
     * emitted clockwise with the filled cell on their right, then stitched into closed loops; loops
     * are area-filtered, scaled to mm and RDP-simplified.
     */
    private fun traceColor(w: Int, h: Int, idx: IntArray, ci: Int, p: TraceParams): List<Polyline> {
        val cols = w + 1
        fun filled(x: Int, y: Int) = x in 0 until w && y in 0 until h && idx[y * w + x] == ci
        val outAdj = HashMap<Int, MutableList<Int>>()
        fun edge(ax: Int, ay: Int, bx: Int, by: Int) { outAdj.getOrPut(ay * cols + ax) { ArrayList(2) }.add(by * cols + bx) }
        for (y in 0 until h) for (x in 0 until w) {
            if (!filled(x, y)) continue
            if (!filled(x, y - 1)) edge(x, y, x + 1, y)           // top    TL -> TR
            if (!filled(x + 1, y)) edge(x + 1, y, x + 1, y + 1)   // right  TR -> BR
            if (!filled(x, y + 1)) edge(x + 1, y + 1, x, y + 1)   // bottom BR -> BL
            if (!filled(x - 1, y)) edge(x, y + 1, x, y)           // left   BL -> TL
        }

        val out = ArrayList<Polyline>()
        val guardMax = 4 * w * h + 16
        for ((start, lst) in outAdj) {
            while (lst.isNotEmpty()) {
                val loop = ArrayList<Int>()
                var cur = start
                var nxt = lst.removeAt(lst.size - 1)
                loop.add(cur)
                var guard = 0
                while (true) {
                    loop.add(nxt)
                    val cands = outAdj[nxt]
                    if (cands.isNullOrEmpty()) break
                    val chosen = pickNext(cur, nxt, cands, cols)
                    cands.remove(chosen)
                    cur = nxt; nxt = chosen
                    if (nxt == start) break
                    if (++guard > guardMax) break
                }
                buildPolyline(loop, cols, p)?.let { out.add(it) }
            }
        }
        return out
    }

    /** Pick the next edge that hugs the boundary: prefer a right turn, then straight, then left, then back. */
    private fun pickNext(prev: Int, cur: Int, cands: List<Int>, cols: Int): Int {
        if (cands.size == 1) return cands[0]
        val idx0 = (cur % cols) - (prev % cols); val idy0 = (cur / cols) - (prev / cols)
        var best = cands[0]; var bestRank = Int.MAX_VALUE
        for (c in cands) {
            val ox = (c % cols) - (cur % cols); val oy = (c / cols) - (cur / cols)
            val dot = idx0 * ox + idy0 * oy
            val cross = idx0 * oy - idy0 * ox
            val rank = when { dot == -1 -> 3; cross < 0 -> 0; dot == 1 -> 1; else -> 2 }
            if (rank < bestRank) { bestRank = rank; best = c }
        }
        return best
    }

    /** Corner-id loop → area-filtered, mm-scaled, RDP-simplified closed [Polyline] (null if rejected). */
    private fun buildPolyline(loop: List<Int>, cols: Int, p: TraceParams): Polyline? {
        // Drop the trailing corner if it repeats the start (the closing edge is implicit).
        val ring = if (loop.size > 1 && loop.first() == loop.last()) loop.subList(0, loop.size - 1) else loop
        if (ring.size < 4) return null

        // Area in px² via the shoelace formula, then to mm² for the speckle filter.
        var area2 = 0L
        for (i in ring.indices) {
            val a = ring[i]; val b = ring[(i + 1) % ring.size]
            val ax = (a % cols).toLong(); val ay = (a / cols).toLong(); val bx = (b % cols).toLong(); val by = (b / cols).toLong()
            area2 += ax * by - bx * ay
        }
        val areaMm2 = abs(area2) / 2.0 / (p.pxPerMm * p.pxPerMm)
        if (areaMm2 < p.minAreaMm2) return null

        // Rotate so the seam (where RDP can't simplify across the wrap) lands on the lexicographically
        // smallest corner, which is always a real corner of the staircase.
        var startAt = 0
        for (i in ring.indices) {
            val cx = ring[i] % cols; val cy = ring[i] / cols
            val bx = ring[startAt] % cols; val by = ring[startAt] / cols
            if (cy < by || (cy == by && cx < bx)) startAt = i
        }
        val rotated = ArrayList<Pt>(ring.size + 1)
        for (i in ring.indices) {
            val c = ring[(startAt + i) % ring.size]
            rotated.add(Pt((c % cols).toDouble() / p.pxPerMm, (c / cols).toDouble() / p.pxPerMm))
        }
        // Repeat the start so RDP treats the path as closed and can drop a collinear vertex at the
        // seam; then strip the duplicated endpoint it keeps.
        rotated.add(rotated[0])
        val simplified = simplifyRdp(rotated, p.detailMm).toMutableList()
        if (simplified.size >= 2 && simplified.first() == simplified.last()) simplified.removeAt(simplified.size - 1)
        if (simplified.size < 3) return null
        val finished = if (p.smooth) smoothClosed(simplified) else simplified
        if (finished.size < 3) return null
        return Polyline(finished, closed = true)
    }

    // ---------------------------------------------------------------------------
    // Contour smoothing (corner-preserving quadratic-B-spline / Chaikin corner-cutting)
    // ---------------------------------------------------------------------------

    private const val SMOOTH_ITERS = 3
    private const val SMOOTH_CORNER_COS = 0.342 // keep turns sharper than ~70°…
    private const val SMOOTH_MIN_ARM_MM = 4.0   // …only where both arms are long. Short-armed sharp
                                                // turns are the pixel staircase, so they get rounded.

    /**
     * Fit smooth curves to the traced contour by repeated corner-cutting — the subdivision limit is a
     * quadratic B-spline, i.e. real Bézier curves. Unlike an interpolating (Catmull-Rom) fit it stays
     * INSIDE the polygon and never overshoots, so the cut never grows past the artwork. A vertex is
     * kept crisp only when its turn is sharp AND both arms are long (a deliberate corner, not a
     * one-pixel jog); corners are detected once and pinned across iterations.
     */
    private fun smoothClosed(poly: List<Pt>): List<Pt> {
        var pts = poly
        var keep = BooleanArray(pts.size) { isHardCorner(pts, it) }
        repeat(SMOOTH_ITERS) {
            val n = pts.size
            if (n < 3) return pts
            val outPts = ArrayList<Pt>(n * 2)
            val outKeep = ArrayList<Boolean>(n * 2)
            for (i in 0 until n) {
                val prev = pts[(i - 1 + n) % n]; val cur = pts[i]; val nxt = pts[(i + 1) % n]
                if (keep[i]) {
                    outPts.add(cur); outKeep.add(true)
                } else {
                    outPts.add(Pt(cur.xMm + 0.25 * (prev.xMm - cur.xMm), cur.yMm + 0.25 * (prev.yMm - cur.yMm)))
                    outPts.add(Pt(cur.xMm + 0.25 * (nxt.xMm - cur.xMm), cur.yMm + 0.25 * (nxt.yMm - cur.yMm)))
                    outKeep.add(false); outKeep.add(false)
                }
            }
            pts = outPts; keep = outKeep.toBooleanArray()
        }
        return pts
    }

    /** A vertex is a hard corner (kept crisp) when its turn is sharp and both arms are long. */
    private fun isHardCorner(poly: List<Pt>, i: Int): Boolean {
        val n = poly.size
        val prev = poly[(i - 1 + n) % n]; val cur = poly[i]; val nxt = poly[(i + 1) % n]
        val ix = cur.xMm - prev.xMm; val iy = cur.yMm - prev.yMm
        val ox = nxt.xMm - cur.xMm; val oy = nxt.yMm - cur.yMm
        val li = hypot(ix, iy); val lo = hypot(ox, oy)
        if (li < SMOOTH_MIN_ARM_MM || lo < SMOOTH_MIN_ARM_MM) return false
        return (ix * ox + iy * oy) / (li * lo) < SMOOTH_CORNER_COS
    }
}
