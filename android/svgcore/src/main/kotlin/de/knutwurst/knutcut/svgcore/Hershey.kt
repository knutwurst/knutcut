package de.knutwurst.knutcut.svgcore

/**
 * A parsed Hershey single-stroke vector font (the public-domain ".jhf" format). Glyphs are centre-line
 * strokes, so a pen draws clean letters instead of the hollow outlines an outline font produces.
 * Android-free so it can be unit-tested; the app reads the .jhf asset into a String and passes it here.
 *
 * Coordinates are encoded two chars per vertex, each value being `char - 'R'`. The first vertex pair of
 * a glyph is its left/right spacing bounds; an x of ' ' (−50) is a pen-up that breaks the stroke. The
 * coordinate system already matches the editor (y increases downward), so glyphs come out upright.
 */
class HersheyFont private constructor(private val glyphs: Map<Char, Glyph>) {

    private class Glyph(val left: Double, val right: Double, val strokes: List<List<Pt>>)

    /**
     * Render [text] as open stroke polylines, scaled so a capital is about [heightMm] tall. Newlines
     * start a new line below the previous one.
     */
    fun render(text: String, heightMm: Double): List<Polyline> {
        val scale = heightMm / CAP_UNITS
        val out = ArrayList<Polyline>()
        var penX = 0.0
        var penY = 0.0
        for (ch in text) {
            if (ch == '\n') { penX = 0.0; penY += LINE_UNITS; continue }
            val g = glyphs[ch] ?: glyphs[' '] ?: continue
            val dx = penX - g.left
            for (stroke in g.strokes) {
                out.add(Polyline(stroke.map { Pt((it.xMm + dx) * scale, (it.yMm + penY) * scale) }, closed = false))
            }
            penX += (g.right - g.left)
        }
        return out
    }

    /**
     * Render [text] as a list of per-glyph [GlyphRun]s, scaled so a capital is about [heightMm]
     * tall. Newlines are stripped; only single-line text is supported here (curved text is always
     * single-line). Each glyph's strokes are in glyph-local coordinates (x=0 at the left bound,
     * baseline y=0), and [GlyphRun.advanceMm] is the distance to advance the pen after drawing.
     * A space glyph produces an empty polyline list with a non-zero advance.
     */
    fun renderGlyphs(text: String, heightMm: Double): List<GlyphRun> {
        val scale = heightMm / CAP_UNITS
        val result = ArrayList<GlyphRun>()
        for (ch in text) {
            if (ch == '\n') continue   // curved text is single-line
            val g = glyphs[ch] ?: glyphs[' '] ?: continue
            val advance = (g.right - g.left) * scale
            // Shift strokes so g.left maps to x=0, keeping baseline at y=0.
            val dx = -g.left
            val polylines = g.strokes.map { stroke ->
                Polyline(stroke.map { Pt((it.xMm + dx) * scale, it.yMm * scale) }, closed = false)
            }
            result.add(GlyphRun(polylines, advance))
        }
        return result
    }

    companion object {
        private const val CAP_UNITS = 21.0   // Hershey capitals span ~21 units (top -12 .. baseline +9)
        private const val LINE_UNITS = 32.0  // line-to-line advance
        private const val ORIGIN = 'R'.code  // coordinate values are encoded as (char - 'R')

        /** Parse a .jhf font. Glyphs are taken in file order and mapped to ASCII 32 ('space') upward. */
        fun parse(jhf: String): HersheyFont {
            val glyphs = HashMap<Char, Glyph>()
            var glyphIndex = 0
            for (raw in jhf.split('\n')) {
                val line = raw.trimEnd('\r')
                if (line.length < 8) continue
                val count = line.substring(5, 8).trim().toIntOrNull() ?: continue
                val data = line.substring(8)
                val ch = (32 + glyphIndex++).toChar()
                if (count < 1 || data.length < count * 2) continue
                val left = (data[0].code - ORIGIN).toDouble()
                val right = (data[1].code - ORIGIN).toDouble()
                val strokes = ArrayList<List<Pt>>()
                var cur = ArrayList<Pt>()
                for (k in 1 until count) {
                    if (data[k * 2] == ' ') {                       // pen up: end the current stroke
                        if (cur.size >= 2) strokes.add(cur)
                        cur = ArrayList()
                    } else {
                        cur.add(Pt((data[k * 2].code - ORIGIN).toDouble(), (data[k * 2 + 1].code - ORIGIN).toDouble()))
                    }
                }
                if (cur.size >= 2) strokes.add(cur)
                glyphs[ch] = Glyph(left, right, strokes)
            }
            return HersheyFont(glyphs)
        }
    }
}
