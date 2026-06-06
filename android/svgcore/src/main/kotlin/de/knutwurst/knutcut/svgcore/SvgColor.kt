package de.knutwurst.knutcut.svgcore

import kotlin.math.roundToInt

/**
 * Parses CSS/SVG colour strings into a packed ARGB int (0xAARRGGBB). Kept Android-free so the
 * editor can show the original colours and the planned multicolour wizard can group layers by them.
 * Returns null for "none", "transparent", or anything it can't read.
 */
object SvgColor {

    fun parse(raw: String?): Int? {
        val s = raw?.trim()?.lowercase() ?: return null
        if (s.isEmpty() || s == "none" || s == "transparent" || s == "currentcolor") return null
        return when {
            s.startsWith("#") -> hex(s.substring(1))
            s.startsWith("rgba(") && s.endsWith(")") -> rgbFunc(s.substring(5, s.length - 1), hasAlpha = true)
            s.startsWith("rgb(") && s.endsWith(")") -> rgbFunc(s.substring(4, s.length - 1), hasAlpha = false)
            else -> NAMED[s]
        }
    }

    private fun hex(h: String): Int? = when (h.length) {
        3 -> argb(255, dup(h[0]), dup(h[1]), dup(h[2]))
        4 -> argb(dup(h[3]), dup(h[0]), dup(h[1]), dup(h[2]))
        6 -> argb(255, byte(h, 0), byte(h, 2), byte(h, 4))
        8 -> argb(byte(h, 6), byte(h, 0), byte(h, 2), byte(h, 4))
        else -> null
    }

    private fun dup(c: Char): Int? = c.digitToIntOrNull(16)?.let { it * 16 + it }
    private fun byte(h: String, at: Int): Int? = h.substring(at, at + 2).toIntOrNull(16)

    private fun rgbFunc(body: String, hasAlpha: Boolean): Int? {
        val parts = body.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.size < 3) return null
        val r = channel(parts[0]); val g = channel(parts[1]); val b = channel(parts[2])
        val a = if (hasAlpha && parts.size >= 4) alpha(parts[3]) else 255
        return argb(a, r, g, b)
    }

    /** A colour channel: an int 0..255 or a percentage. */
    private fun channel(v: String): Int? {
        val n = if (v.endsWith("%")) v.dropLast(1).toDoubleOrNull()?.let { it / 100.0 * 255.0 } else v.toDoubleOrNull()
        return n?.roundToInt()?.coerceIn(0, 255)
    }

    /** The alpha component of rgba(): a 0..1 fraction or a percentage. */
    private fun alpha(v: String): Int? {
        val n = if (v.endsWith("%")) v.dropLast(1).toDoubleOrNull()?.let { it / 100.0 } else v.toDoubleOrNull()
        return n?.let { (it * 255.0).roundToInt().coerceIn(0, 255) }
    }

    private fun argb(a: Int?, r: Int?, g: Int?, b: Int?): Int? {
        if (a == null || r == null || g == null || b == null) return null
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }

    // The CSS basic + a few common named colours. Enough for hand-authored and exported SVGs.
    private val NAMED: Map<String, Int> = mapOf(
        "black" to 0xFF000000.toInt(), "white" to 0xFFFFFFFF.toInt(), "red" to 0xFFFF0000.toInt(),
        "green" to 0xFF008000.toInt(), "lime" to 0xFF00FF00.toInt(), "blue" to 0xFF0000FF.toInt(),
        "yellow" to 0xFFFFFF00.toInt(), "cyan" to 0xFF00FFFF.toInt(), "aqua" to 0xFF00FFFF.toInt(),
        "magenta" to 0xFFFF00FF.toInt(), "fuchsia" to 0xFFFF00FF.toInt(), "gray" to 0xFF808080.toInt(),
        "grey" to 0xFF808080.toInt(), "silver" to 0xFFC0C0C0.toInt(), "maroon" to 0xFF800000.toInt(),
        "olive" to 0xFF808000.toInt(), "purple" to 0xFF800080.toInt(), "teal" to 0xFF008080.toInt(),
        "navy" to 0xFF000080.toInt(), "orange" to 0xFFFFA500.toInt(), "pink" to 0xFFFFC0CB.toInt(),
        "brown" to 0xFFA52A2A.toInt(), "gold" to 0xFFFFD700.toInt(),
    )
}
