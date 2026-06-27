package de.knutwurst.knutcut.svgcore

import kotlin.math.*

/**
 * Parses ASCII DXF (Drawing Exchange Format) files into polylines in millimeters.
 *
 * Supported entities: LINE, LWPOLYLINE (with bulge arcs), POLYLINE/VERTEX,
 * CIRCLE, ARC, ELLIPSE, and SPLINE (degrees 1–9 via de Boor).
 *
 * DXF uses Y-up coordinates; this parser flips Y so designs appear correctly in the
 * mat editor's screen-coordinate system (Y-down).
 *
 * Units are read from the $INSUNITS header variable and converted to mm automatically.
 * Files without a header (or with $INSUNITS = 0) are treated as mm.
 */
object DxfParser {

    private const val ARC_STEPS_FULL = 72  // steps for a full circle
    private const val SPLINE_STEPS  = 64  // samples per B-spline knot span

    /** $INSUNITS integer → mm conversion factor. */
    private val UNITS_TO_MM = mapOf(
        1  to 25.4,    // inches
        2  to 304.8,   // feet
        4  to 1.0,     // mm (native)
        5  to 10.0,    // cm
        6  to 1000.0,  // m
        8  to 914.4,   // yards
        10 to 0.0254,  // 0.001 inch
        14 to 100.0    // dm
    )

    /** Result of [parseShapes]: per-layer shapes plus a count of entities that produced no geometry. */
    class Result(val shapes: List<SvgShape>, val skipped: Int)

    fun parse(text: String): List<Polyline> = parseShapes(text).shapes.flatMap { it.polylines }

    /**
     * Parse a DXF document into per-layer [SvgShape]s. Entities are grouped by their group-8 layer
     * name (blank → "DXF") preserving layer encounter order; each layer's color is the first
     * mappable ACI color (group 62) found among its entities. [Result.skipped] counts entities that
     * produced no usable geometry (e.g. a fit-point-only spline we couldn't interpolate).
     */
    fun parseShapes(text: String): Result {
        val pairs = parsePairs(text)
        val scale = readScale(pairs)
        return extractShapes(pairs, scale)
    }

    /**
     * A tighter "is this DXF?" sniff than searching for "SECTION" anywhere. Robust to leading
     * whitespace and CRLF, and to the leading-space group codes the codebase's DXF writers emit.
     * True when the content opens with a `0`/`SECTION` group pair, or carries an `ENTITIES` section
     * marker (group 2 value ENTITIES).
     */
    fun looksLikeDxf(text: String): Boolean {
        val lines = text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.iterator()
        if (!lines.hasNext()) return false
        val first = lines.next()
        if (first == "0" && lines.hasNext() && lines.next().equals("SECTION", ignoreCase = true)) return true
        // Otherwise scan for a group-2 value of ENTITIES (a "2" line directly followed by "ENTITIES").
        val all = text.lineSequence().map { it.trim() }.toList()
        var i = 0
        while (i < all.size - 1) {
            if (all[i] == "2" && all[i + 1].equals("ENTITIES", ignoreCase = true)) return true
            i++
        }
        return false
    }

    // ─── Group-code parsing ───────────────────────────────────────────────────────

    private fun parsePairs(text: String): List<Pair<Int, String>> {
        val out = ArrayList<Pair<Int, String>>()
        val iter = text.lineSequence().map { it.trim() }.iterator()
        while (iter.hasNext()) {
            val codeLine = iter.next()
            if (!iter.hasNext()) break
            val value = iter.next()
            val code = codeLine.toIntOrNull() ?: continue
            out.add(code to value)
        }
        return out
    }

    // ─── Units ────────────────────────────────────────────────────────────────────

    private fun readScale(pairs: List<Pair<Int, String>>): Double {
        var inHeader = false
        var awaitUnits = false
        for ((code, value) in pairs) {
            if (code == 2 && value.equals("HEADER", ignoreCase = true)) { inHeader = true; continue }
            if (code == 0 && value.equals("ENDSEC", ignoreCase = true)) inHeader = false
            if (!inHeader) continue
            if (code == 9 && value == "\$INSUNITS") { awaitUnits = true; continue }
            if (awaitUnits) {
                if (code == 70) return UNITS_TO_MM[value.trim().toIntOrNull() ?: 0] ?: 1.0
                awaitUnits = false
            }
        }
        return 1.0
    }

    // ─── Entity extraction ────────────────────────────────────────────────────────

    /** One entity's polylines tagged with its layer name and (optional) mapped ARGB color. */
    private class Tagged(val layer: String, val colorArgb: Int?, val polylines: List<Polyline>)

    private fun extractShapes(pairs: List<Pair<Int, String>>, scale: Double): Result {
        // Locate the ENTITIES section once; both simple and old-style dispatch run inside it only.
        var i = 0
        while (i < pairs.size) {
            if (pairs[i].first == 2 && pairs[i].second.equals("ENTITIES", ignoreCase = true)) { i++; break }
            i++
        }

        val tagged = ArrayList<Tagged>()
        var skipped = 0

        while (i < pairs.size) {
            val (code, value) = pairs[i]
            if (code == 0 && value.equals("ENDSEC", ignoreCase = true)) break
            if (code != 0) { i++; continue }

            val type = value.uppercase()
            if (type == "POLYLINE") {
                // Old-style POLYLINE owns its VERTEX/SEQEND chain; parse it inline to keep file order.
                val (next, t, miss) = parseOldPolyline(pairs, i, scale)
                if (t != null) tagged.add(t) else if (miss) skipped++
                i = next
                continue
            }

            val end = nextEntityStart(pairs, i + 1)
            val block = pairs.subList(i, end)
            val layer = layerOf(block)
            val color = colorOf(block)
            val polys: List<Polyline>? = when (type) {
                "LINE"       -> parseLine(block, scale)?.let { listOf(it) }
                "LWPOLYLINE" -> parseLwPolyline(block, scale)
                "CIRCLE"     -> parseCircle(block, scale)?.let { listOf(it) }
                "ARC"        -> parseArc(block, scale)?.let { listOf(it) }
                "ELLIPSE"    -> parseEllipse(block, scale)?.let { listOf(it) }
                "SPLINE"     -> parseSpline(block, scale)
                else         -> null
            }
            val usable = polys?.filter { it.points.size >= 2 } ?: emptyList()
            if (usable.isNotEmpty()) {
                tagged.add(Tagged(layer, color, usable))
            } else if (type == "SPLINE") {
                // A SPLINE we recognized but could not turn into geometry counts as skipped.
                skipped++
            }
            i = end
        }

        return Result(groupByLayer(tagged), skipped)
    }

    /** Group tagged polylines into one SvgShape per layer, preserving layer encounter order. */
    private fun groupByLayer(tagged: List<Tagged>): List<SvgShape> {
        val order = ArrayList<String>()
        val polysByLayer = LinkedHashMap<String, ArrayList<Polyline>>()
        val colorByLayer = HashMap<String, Int?>()
        for (t in tagged) {
            if (t.layer !in polysByLayer) { polysByLayer[t.layer] = ArrayList(); order.add(t.layer) }
            polysByLayer[t.layer]!!.addAll(t.polylines)
            if (colorByLayer[t.layer] == null && t.colorArgb != null) colorByLayer[t.layer] = t.colorArgb
        }
        return order.map { layer ->
            val name = layer.ifBlank { "DXF" }
            SvgShape(name, polysByLayer[layer]!!, colorByLayer[layer])
        }
    }

    private fun layerOf(block: List<Pair<Int, String>>): String =
        block.firstOrNull { it.first == 8 }?.second?.trim() ?: ""

    private fun colorOf(block: List<Pair<Int, String>>): Int? {
        val aci = block.firstOrNull { it.first == 62 }?.second?.trim()?.toIntOrNull() ?: return null
        return ACI_TO_ARGB[aci]
    }

    /** AutoCAD Color Index → opaque ARGB. Unmapped (0 ByBlock, 7, 256 ByLayer, …) stays null. */
    private val ACI_TO_ARGB = mapOf(
        1 to 0xFFFF0000.toInt(),
        2 to 0xFFFFFF00.toInt(),
        3 to 0xFF00FF00.toInt(),
        4 to 0xFF00FFFF.toInt(),
        5 to 0xFF0000FF.toInt(),
        6 to 0xFFFF00FF.toInt(),
        8 to 0xFF808080.toInt(),
        9 to 0xFFC0C0C0.toInt()
    )

    private fun nextEntityStart(pairs: List<Pair<Int, String>>, from: Int): Int {
        for (k in from until pairs.size) if (pairs[k].first == 0) return k
        return pairs.size
    }

    // ─── Individual entity parsers ────────────────────────────────────────────────

    private fun parseLine(e: List<Pair<Int, String>>, s: Double): Polyline? {
        var x1 = Double.NaN; var y1 = Double.NaN
        var x2 = Double.NaN; var y2 = Double.NaN
        for ((c, v) in e) {
            val d = v.toDoubleOrNull() ?: continue
            when (c) { 10 -> x1 = d; 20 -> y1 = d; 11 -> x2 = d; 21 -> y2 = d }
        }
        if (x1.isNaN() || y1.isNaN() || x2.isNaN() || y2.isNaN()) return null
        return Polyline(listOf(pt(x1, y1, s), pt(x2, y2, s)), closed = false)
    }

    private fun parseLwPolyline(e: List<Pair<Int, String>>, s: Double): List<Polyline> {
        var flags = 0
        var pendingX = Double.NaN
        val xs = ArrayList<Double>(); val ys = ArrayList<Double>()
        val bulges = ArrayList<Double>()

        for ((c, v) in e) {
            when (c) {
                70 -> flags = v.trim().toIntOrNull() ?: 0
                10 -> pendingX = v.toDoubleOrNull() ?: Double.NaN
                20 -> if (!pendingX.isNaN()) {
                    xs.add(pendingX)
                    ys.add(v.toDoubleOrNull() ?: 0.0)
                    bulges.add(0.0)
                    pendingX = Double.NaN
                }
                42 -> if (bulges.isNotEmpty()) bulges[bulges.size - 1] = v.toDoubleOrNull() ?: 0.0
            }
        }

        if (xs.size < 2) return emptyList()
        val closed = (flags and 1) != 0
        val pts = ArrayList<Pt>()
        val count = if (closed) xs.size else xs.size - 1

        for (i in 0 until count) {
            val j = (i + 1) % xs.size
            pts.add(pt(xs[i], ys[i], s))
            val b = bulges.getOrElse(i) { 0.0 }
            if (abs(b) > 1e-10) pts.addAll(bulgeArc(xs[i], ys[i], xs[j], ys[j], b, s))
        }
        // Close the ring or append the final open vertex
        if (closed) pts.add(pts.first()) else pts.add(pt(xs.last(), ys.last(), s))

        return listOf(Polyline(pts, closed))
    }

    /** Result of parsing one old-style POLYLINE chain starting at [start] (a `0`/POLYLINE pair). */
    private data class OldPoly(val next: Int, val tagged: Tagged?, val missed: Boolean)

    /**
     * Parse one old-style POLYLINE…VERTEX…SEQEND chain. Honors the closed flag (bit 1 of group 70)
     * and per-vertex bulges (group 42), interpolating arcs the same way LWPOLYLINE does.
     * Returns the index just past the chain so the dispatch loop can continue in file order.
     */
    private fun parseOldPolyline(pairs: List<Pair<Int, String>>, start: Int, s: Double): OldPoly {
        var i = start
        // Header of the POLYLINE entity: read flags + layer/color up to the first VERTEX/SEQEND.
        i++
        var flags = 0
        var layer = ""
        var color: Int? = null
        while (i < pairs.size && pairs[i].first != 0) {
            val (c, v) = pairs[i]
            when (c) {
                70 -> flags = v.trim().toIntOrNull() ?: 0
                8  -> layer = v.trim()
                62 -> color = v.trim().toIntOrNull()?.let { ACI_TO_ARGB[it] }
            }
            i++
        }
        val closed = (flags and 1) != 0

        val xs = ArrayList<Double>(); val ys = ArrayList<Double>(); val bulges = ArrayList<Double>()
        while (i < pairs.size) {
            val (c0, v0) = pairs[i]
            if (c0 == 0 && v0.equals("SEQEND", ignoreCase = true)) { i++; break }
            if (c0 == 0 && !v0.equals("VERTEX", ignoreCase = true)) break
            if (c0 == 0) {
                i++
                var vx = 0.0; var vy = 0.0; var vb = 0.0
                while (i < pairs.size && pairs[i].first != 0) {
                    val d = pairs[i].second.toDoubleOrNull() ?: 0.0
                    when (pairs[i].first) { 10 -> vx = d; 20 -> vy = d; 42 -> vb = d }
                    i++
                }
                xs.add(vx); ys.add(vy); bulges.add(vb)
            } else i++
        }

        if (xs.size < 2) return OldPoly(i, null, true)

        val pts = ArrayList<Pt>()
        val count = if (closed) xs.size else xs.size - 1
        for (k in 0 until count) {
            val j = (k + 1) % xs.size
            pts.add(pt(xs[k], ys[k], s))
            val b = bulges.getOrElse(k) { 0.0 }
            if (abs(b) > 1e-10) pts.addAll(bulgeArc(xs[k], ys[k], xs[j], ys[j], b, s))
        }
        if (closed) pts.add(pts.first()) else pts.add(pt(xs.last(), ys.last(), s))

        return OldPoly(i, Tagged(layer, color, listOf(Polyline(pts, closed))), false)
    }

    private fun parseCircle(e: List<Pair<Int, String>>, s: Double): Polyline? {
        var cx = 0.0; var cy = 0.0; var r = 0.0
        for ((c, v) in e) {
            val d = v.toDoubleOrNull() ?: continue
            when (c) { 10 -> cx = d; 20 -> cy = d; 40 -> r = d }
        }
        if (r <= 0.0) return null
        val pts = (0..ARC_STEPS_FULL).map { k ->
            val a = 2.0 * PI * k / ARC_STEPS_FULL
            pt(cx + r * cos(a), cy + r * sin(a), s)
        }
        return Polyline(pts, closed = true)
    }

    private fun parseArc(e: List<Pair<Int, String>>, s: Double): Polyline? {
        var cx = 0.0; var cy = 0.0; var r = 0.0
        var startDeg = 0.0; var endDeg = 360.0
        for ((c, v) in e) {
            val d = v.toDoubleOrNull() ?: continue
            when (c) { 10 -> cx = d; 20 -> cy = d; 40 -> r = d; 50 -> startDeg = d; 51 -> endDeg = d }
        }
        if (r <= 0.0) return null
        var span = endDeg - startDeg
        if (span <= 0.0) span += 360.0  // DXF arcs always go CCW
        val steps = max(2, (ARC_STEPS_FULL * span / 360.0).roundToInt())
        val pts = (0..steps).map { k ->
            val a = (startDeg + span * k / steps) * PI / 180.0
            pt(cx + r * cos(a), cy + r * sin(a), s)
        }
        return Polyline(pts, closed = false)
    }

    private fun parseEllipse(e: List<Pair<Int, String>>, s: Double): Polyline? {
        var cx = 0.0; var cy = 0.0
        var axX = 1.0; var axY = 0.0   // major-axis endpoint relative to center
        var ratio = 1.0                 // minor/major radius ratio
        var startP = 0.0; var endP = 2.0 * PI
        for ((c, v) in e) {
            val d = v.toDoubleOrNull() ?: continue
            when (c) {
                10 -> cx = d; 20 -> cy = d
                11 -> axX = d; 21 -> axY = d
                40 -> ratio = d
                41 -> startP = d; 42 -> endP = d
            }
        }
        val majorR = sqrt(axX * axX + axY * axY)
        val minorR = majorR * ratio
        val rot = atan2(axY, axX)
        var span = endP - startP
        if (span <= 0.0) span += 2.0 * PI
        val steps = max(2, (ARC_STEPS_FULL * span / (2.0 * PI)).roundToInt())
        val pts = (0..steps).map { k ->
            val t = startP + span * k / steps
            val ex = majorR * cos(t); val ey = minorR * sin(t)
            pt(cx + ex * cos(rot) - ey * sin(rot), cy + ex * sin(rot) + ey * cos(rot), s)
        }
        return Polyline(pts, closed = abs(span - 2.0 * PI) < 1e-6)
    }

    private fun parseSpline(e: List<Pair<Int, String>>, s: Double): List<Polyline> {
        var degree = 3; var flags = 0
        val knots = ArrayList<Double>()
        val ctrlX = ArrayList<Double>(); val ctrlY = ArrayList<Double>()
        val fitX = ArrayList<Double>(); val fitY = ArrayList<Double>()
        for ((c, v) in e) {
            when (c) {
                70 -> flags = v.trim().toIntOrNull() ?: 0
                71 -> degree = (v.trim().toIntOrNull() ?: 3).coerceIn(1, 9)
                40 -> knots.add(v.toDoubleOrNull() ?: 0.0)
                10 -> ctrlX.add(v.toDoubleOrNull() ?: 0.0)
                20 -> ctrlY.add(v.toDoubleOrNull() ?: 0.0)
                11 -> fitX.add(v.toDoubleOrNull() ?: 0.0)
                21 -> fitY.add(v.toDoubleOrNull() ?: 0.0)
            }
        }
        val closed = (flags and 1) != 0  // bit 1: closed/periodic
        val n = minOf(ctrlX.size, ctrlY.size)

        if (n < 2) {
            // No usable control points. Fall back to a polyline through the fit points if we have ≥2.
            val fn = minOf(fitX.size, fitY.size)
            if (fn < 2) return emptyList()
            val pts = (0 until fn).mapTo(ArrayList()) { pt(fitX[it], fitY[it], s) }
            closeIfNeeded(pts, closed)
            return listOf(Polyline(pts, closed))
        }

        val kv = if (knots.size == n + degree + 1) knots.toDoubleArray() else clampedKnots(n, degree)
        val spans = n - degree
        val totalSteps = SPLINE_STEPS * spans
        val xs = ctrlX.toDoubleArray(); val ys = ctrlY.toDoubleArray()
        val pts = (0..totalSteps).mapTo(ArrayList()) { k ->
            val t = kv[degree] + (kv[n] - kv[degree]) * k / totalSteps
            val (x, y) = deBoor(degree, xs, ys, kv, t)
            pt(x, y, s)
        }
        closeIfNeeded(pts, closed)
        return listOf(Polyline(pts, closed))
    }

    /** Ensure a closed polyline's last point equals its first (codebase convention). */
    private fun closeIfNeeded(pts: MutableList<Pt>, closed: Boolean) {
        if (closed && pts.isNotEmpty() && pts.first() != pts.last()) pts.add(pts.first())
    }

    // ─── Geometry helpers ─────────────────────────────────────────────────────────

    /** DXF (x, y) in drawing units → screen Pt in mm with Y flipped (DXF is Y-up, mat is Y-down). */
    private fun pt(x: Double, y: Double, scale: Double) = Pt(x * scale, -y * scale)

    /**
     * Interpolation points for a bulge arc between (x1,y1) and (x2,y2).
     * Bulge = tan(included_angle / 4); positive = CCW, negative = CW.
     * Start and end vertices are excluded (added by the LWPOLYLINE loop).
     */
    private fun bulgeArc(
        x1: Double, y1: Double, x2: Double, y2: Double, bulge: Double, scale: Double
    ): List<Pt> {
        val theta = 4.0 * atan(abs(bulge))      // included angle of arc
        val dx = x2 - x1; val dy = y2 - y1
        val chord = sqrt(dx * dx + dy * dy)
        if (chord < 1e-10) return emptyList()
        val r = chord / (2.0 * sin(theta / 2.0))
        // Center: perpendicular to chord at midpoint, offset by r*cos(θ/2) (the center-to-chord
        // distance, i.e. the apothem) toward the CCW side.
        val sg = sign(bulge)
        val apothem = r * cos(theta / 2.0)
        val cx = (x1 + x2) / 2.0 - sg * apothem * dy / chord
        val cy = (y1 + y2) / 2.0 + sg * apothem * dx / chord
        val startAngle = atan2(y1 - cy, x1 - cx)
        val span = if (bulge > 0.0) theta else -theta
        val steps = max(1, (ARC_STEPS_FULL * theta / (2.0 * PI)).roundToInt())
        return (1 until steps).map { i ->
            val a = startAngle + span * i / steps
            pt(cx + r * cos(a), cy + r * sin(a), scale)
        }
    }

    /** Clamped (open) uniform B-spline knot vector for [n] control points at [degree]. */
    private fun clampedKnots(n: Int, degree: Int): DoubleArray {
        val total = n + degree + 1
        return DoubleArray(total) { i ->
            when {
                i <= degree -> 0.0
                i >= n      -> 1.0
                else        -> (i - degree).toDouble() / (n - degree)
            }
        }
    }

    /**
     * de Boor's algorithm: evaluate a B-spline at parameter [t].
     * Returns the (x, y) point on the curve.
     */
    private fun deBoor(
        degree: Int, xs: DoubleArray, ys: DoubleArray, knots: DoubleArray, t: Double
    ): Pair<Double, Double> {
        val n = minOf(xs.size, ys.size)
        val tc = t.coerceIn(knots[degree], knots[n])
        // Find knot span k: knots[k] <= tc < knots[k+1]
        var k = degree
        for (i in degree until n) {
            if (tc < knots[i + 1]) { k = i; break }
            k = i
        }
        // Initialize working arrays with the relevant control points
        val dX = DoubleArray(degree + 1) { xs[(k - degree + it).coerceIn(0, n - 1)] }
        val dY = DoubleArray(degree + 1) { ys[(k - degree + it).coerceIn(0, n - 1)] }
        // Triangular de Boor reduction
        for (r in 1..degree) {
            for (j in degree downTo r) {
                val gj = k - degree + j
                val lo = knots.getOrElse(gj) { 0.0 }
                val hi = knots.getOrElse(gj + degree + 1 - r) { 1.0 }
                val alpha = if (hi - lo < 1e-10) 0.0 else (tc - lo) / (hi - lo)
                dX[j] = (1.0 - alpha) * dX[j - 1] + alpha * dX[j]
                dY[j] = (1.0 - alpha) * dY[j - 1] + alpha * dY[j]
            }
        }
        return dX[degree] to dY[degree]
    }
}
