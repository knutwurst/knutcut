package de.knutwurst.knutcut.ui

import android.content.Context
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.Rect
import android.graphics.Typeface
import de.knutwurst.knutcut.svgcore.HersheyFont
import de.knutwurst.knutcut.svgcore.Polyline
import de.knutwurst.knutcut.svgcore.Pt
import kotlin.math.max

const val MAX_TEXT_CHARS = 400
private const val MAX_POINTS = 8000   // total sampled points budget for outline text
private const val REF_SIZE = 256f

/** Result of vectorising text: the polylines plus whether it had to be simplified/truncated. */
data class TextResult(val polylines: List<Polyline>, val simplified: Boolean)

/** A selectable font for the text tool. [stroke] = single-line (pen) versus an outline (cut or draw). */
class FontOption(
    val label: String,
    val stroke: Boolean,
    private val renderer: (text: String, heightMm: Double) -> TextResult,
) {
    fun render(text: String, heightMm: Double): TextResult {
        val truncated = text.length > MAX_TEXT_CHARS
        val r = renderer(text.take(MAX_TEXT_CHARS), heightMm)
        return if (truncated) r.copy(simplified = true) else r
    }
}

/** The available fonts: system outline fonts, bundled outline fonts, and Hershey single-stroke fonts. */
class FontRepository(context: Context) {
    private val assets = context.assets

    val options: List<FontOption> = buildList {
        add(outline("Sans", Typeface.SANS_SERIF))
        add(outline("Serif", Typeface.SERIF))
        add(outline("Mono", Typeface.MONOSPACE))
        bundledOutline("Anton", "fonts/Anton-Regular.ttf")
        bundledOutline("Pacifico", "fonts/Pacifico-Regular.ttf")
        bundledOutline("Abril Fatface", "fonts/AbrilFatface-Regular.ttf")
        bundledStroke("Einlinie Sans", "hershey/futural.jhf")
        bundledStroke("Einlinie Serif", "hershey/rowmans.jhf")
        bundledStroke("Einlinie Script", "hershey/scripts.jhf")
    }

    private fun outline(label: String, tf: Typeface) =
        FontOption(label, stroke = false) { t, h -> outlineText(tf, t, h) }

    private fun MutableList<FontOption>.bundledOutline(label: String, asset: String) {
        runCatching { Typeface.createFromAsset(assets, asset) }.getOrNull()?.let { add(outline(label, it)) }
    }

    private fun MutableList<FontOption>.bundledStroke(label: String, asset: String) {
        runCatching { HersheyFont.parse(assets.open(asset).bufferedReader().use { it.readText() }) }
            .getOrNull()?.let { font -> add(FontOption(label, stroke = true) { t, h -> TextResult(font.render(t, h), simplified = false) }) }
    }
}

/** Text as filled-glyph outline polylines (closed contours), scaled so a capital is about [heightMm] tall. */
private fun outlineText(typeface: Typeface, text: String, heightMm: Double): TextResult {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.typeface = typeface
        textSize = REF_SIZE
    }
    val capPx = Rect().also { paint.getTextBounds("H", 0, 1, it) }.height().toFloat().coerceAtLeast(1f)
    val scale = (heightMm / capPx).toDouble()
    val spacing = paint.fontSpacing
    val full = Path()
    text.split('\n').forEachIndexed { i, line ->
        if (line.isEmpty()) return@forEachIndexed
        val p = Path()
        paint.getTextPath(line, 0, line.length, 0f, i * spacing, p)
        full.addPath(p)
    }

    // Pick a sampling step that keeps the total point count within MAX_POINTS, so a long string or a
    // very ornate font can't blow up into hundreds of thousands of points. Coarser than the ideal step
    // means we simplified.
    val fineStep = max(1f, REF_SIZE / 120f)
    var totalLen = 0f
    val lengths = PathMeasure(full, false)
    do { totalLen += lengths.length } while (lengths.nextContour())
    val step = max(fineStep, totalLen / MAX_POINTS)
    val simplified = step > fineStep * 1.001f

    val out = ArrayList<Polyline>()
    val pm = PathMeasure(full, false)
    do {
        val len = pm.length
        if (len <= 0f) continue
        val pts = ArrayList<Pt>()
        val pos = FloatArray(2)
        var d = 0f
        while (d < len) {
            pm.getPosTan(d, pos, null)
            pts.add(Pt(pos[0] * scale, pos[1] * scale))
            d += step
        }
        pm.getPosTan(len, pos, null)
        pts.add(Pt(pos[0] * scale, pos[1] * scale))
        if (pts.size >= 2) out.add(Polyline(pts, closed = true))
    } while (pm.nextContour())
    return TextResult(out, simplified)
}
