package de.knutwurst.knutcut.svgcore

import kotlin.math.abs

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

/** Tunable parameters for [RasterTrace.trace]. */
data class TraceParams(
    /** Palette size for the posterisation (clamped to 2..12). */
    val numColors: Int = 6,
    /** Drop the colour that dominates the image border (so a solid background isn't cut). */
    val dropBackground: Boolean = true,
    /** RDP tolerance in mm: higher = fewer points / smoother staircases, lower = more faithful. */
    val detailMm: Double = 0.4,
    /** Contours whose area is below this (mm²) are discarded as speckle. */
    val minAreaMm2: Double = 4.0,
    /** Image pixels per millimetre — sets the real-world size of the traced geometry. */
    val pxPerMm: Double = 4.0,
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

    fun trace(img: RasterImage, params: TraceParams): TraceResult {
        val k = params.numColors.coerceIn(2, 12)
        val (palette, indexMap) = quantize(img, k)
        if (palette.isEmpty()) return TraceResult(emptyList(), palette, indexMap, -1, img.width, img.height)

        val bg = if (params.dropBackground) detectBackground(img.width, img.height, indexMap, palette.size) else -1

        // Population per palette colour, to order layers (largest first) and skip empty colours.
        val counts = IntArray(palette.size)
        for (idx in indexMap) if (idx >= 0) counts[idx]++
        val order = palette.indices.sortedByDescending { counts[it] }

        val colors = ArrayList<TracedColor>()
        for (ci in order) {
            if (ci == bg || counts[ci] == 0) continue
            val contours = traceColor(img.width, img.height, indexMap, ci, params)
            if (contours.isNotEmpty()) colors.add(TracedColor(palette[ci], contours))
        }
        return TraceResult(colors, palette, indexMap, bg, img.width, img.height)
    }

    // ---------------------------------------------------------------------------
    // Median-cut colour quantisation
    // ---------------------------------------------------------------------------

    /** A populated 5-bit-per-channel histogram cell: pixel [cnt] and true-colour sums for averaging. */
    private class Bin(val cnt: Int, val sr: Long, val sg: Long, val sb: Long) {
        val r get() = (sr / cnt).toInt(); val g get() = (sg / cnt).toInt(); val b get() = (sb / cnt).toInt()
    }

    /** Returns (palette as packed 0xFFRRGGBB, per-pixel palette index; -1 for transparent pixels). */
    private fun quantize(img: RasterImage, k: Int): Pair<IntArray, IntArray> {
        // Histogram opaque pixels at 5 bits/channel (32768 cells max), keeping true colour sums so the
        // palette stays accurate. The 5-bit key also lets us resolve the nearest palette colour once
        // per cell instead of once per pixel.
        val cntC = IntArray(1 shl 15); val sumR = LongArray(1 shl 15); val sumG = LongArray(1 shl 15); val sumB = LongArray(1 shl 15)
        for (p in img.pixels) {
            if (((p ushr 24) and 0xFF) < ALPHA_CUTOFF) continue
            val r = (p ushr 16) and 0xFF; val g = (p ushr 8) and 0xFF; val b = p and 0xFF
            val bin = ((r ushr 3) shl 10) or ((g ushr 3) shl 5) or (b ushr 3)
            cntC[bin]++; sumR[bin] += r; sumG[bin] += g; sumB[bin] += b
        }
        val bins = ArrayList<Bin>()
        val binKey = ArrayList<Int>()
        for (key in 0 until (1 shl 15)) if (cntC[key] > 0) { bins.add(Bin(cntC[key], sumR[key], sumG[key], sumB[key])); binKey.add(key) }
        if (bins.isEmpty()) return IntArray(0) to IntArray(img.pixels.size) { -1 }

        // Median cut: split the most-populated box along its longest channel at the weighted median.
        val arr = bins.toTypedArray()
        val boxes = ArrayList<IntArray>().apply { add(intArrayOf(0, arr.size)) } // each = [lo, hi)
        while (boxes.size < k) {
            val box = boxes.filter { it[1] - it[0] > 1 }.maxByOrNull { boxPixels(arr, it[0], it[1]) } ?: break
            val lo = box[0]; val hi = box[1]
            val ch = longestChannel(arr, lo, hi)
            val sub = arr.copyOfRange(lo, hi).sortedBy { channel(it, ch) }
            for (i in lo until hi) arr[i] = sub[i - lo]
            val total = boxPixels(arr, lo, hi)
            var acc = 0; var cut = lo + 1
            for (i in lo until hi) { acc += arr[i].cnt; if (acc * 2 >= total) { cut = (i + 1).coerceIn(lo + 1, hi - 1); break } }
            boxes.remove(box); boxes.add(intArrayOf(lo, cut)); boxes.add(intArrayOf(cut, hi))
        }

        val palette = IntArray(boxes.size)
        for ((i, box) in boxes.withIndex()) {
            var c = 0L; var r = 0L; var g = 0L; var b = 0L
            for (j in box[0] until box[1]) { val bin = arr[j]; c += bin.cnt; r += bin.sr; g += bin.sg; b += bin.sb }
            if (c == 0L) c = 1
            palette[i] = (0xFF shl 24) or (((r / c).toInt()) shl 16) or (((g / c).toInt()) shl 8) or ((b / c).toInt())
        }

        // Nearest palette colour per 5-bit cell, then map every pixel through that cache.
        val nearestForKey = HashMap<Int, Int>(binKey.size * 2)
        for (key in binKey) {
            val r = ((key ushr 10) and 0x1F) shl 3; val g = ((key ushr 5) and 0x1F) shl 3; val b = (key and 0x1F) shl 3
            nearestForKey[key] = nearest(palette, r, g, b)
        }
        val indexMap = IntArray(img.pixels.size)
        for (i in img.pixels.indices) {
            val p = img.pixels[i]
            indexMap[i] = if (((p ushr 24) and 0xFF) < ALPHA_CUTOFF) -1
            else nearestForKey[(((p ushr 16) and 0xFF) ushr 3 shl 10) or (((p ushr 8) and 0xFF) ushr 3 shl 5) or ((p and 0xFF) ushr 3)] ?: 0
        }
        return palette to indexMap
    }

    private fun boxPixels(arr: Array<Bin>, lo: Int, hi: Int): Int { var s = 0; for (i in lo until hi) s += arr[i].cnt; return s }
    private fun channel(b: Bin, ch: Int) = when (ch) { 0 -> b.r; 1 -> b.g; else -> b.b }
    private fun longestChannel(arr: Array<Bin>, lo: Int, hi: Int): Int {
        var rmin = 255; var rmax = 0; var gmin = 255; var gmax = 0; var bmin = 255; var bmax = 0
        for (i in lo until hi) { val x = arr[i]; rmin = minOf(rmin, x.r); rmax = maxOf(rmax, x.r); gmin = minOf(gmin, x.g); gmax = maxOf(gmax, x.g); bmin = minOf(bmin, x.b); bmax = maxOf(bmax, x.b) }
        val dr = rmax - rmin; val dg = gmax - gmin; val db = bmax - bmin
        return if (dr >= dg && dr >= db) 0 else if (dg >= db) 1 else 2
    }
    private fun nearest(palette: IntArray, r: Int, g: Int, b: Int): Int {
        var best = 0; var bestD = Int.MAX_VALUE
        for (i in palette.indices) {
            val pr = (palette[i] ushr 16) and 0xFF; val pg = (palette[i] ushr 8) and 0xFF; val pb = palette[i] and 0xFF
            val d = (pr - r) * (pr - r) + (pg - g) * (pg - g) + (pb - b) * (pb - b)
            if (d < bestD) { bestD = d; best = i }
        }
        return best
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
        return Polyline(simplified, closed = true)
    }
}
