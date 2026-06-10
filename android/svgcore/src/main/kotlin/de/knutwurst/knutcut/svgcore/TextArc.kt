package de.knutwurst.knutcut.svgcore

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * One glyph to be laid out: its outline/stroke polylines in glyph-local coordinates.
 * The pen origin is at x = 0, the baseline is at y = 0, and ink above the baseline has
 * negative y (SVG/screen convention where y grows downward).
 *
 * [advanceMm] is the horizontal distance the pen moves after drawing this glyph.
 */
data class GlyphRun(val polylines: List<Polyline>, val advanceMm: Double)

/**
 * Places a sequence of glyphs along a circular arc, matching the "Curve" feature found in
 * consumer cutting-machine software.
 *
 * Each glyph is moved and rotated as a rigid body — no per-point warping — so letter shapes
 * are preserved exactly.
 */
object TextArc {

    /**
     * Lay [glyphs] left-to-right, then bend the baseline into an arc determined by [curve].
     *
     * [curve] is in [-1, 1]:
     *   -  0  → straight row, no transformation applied.
     *   - +1  → full circle, text sweeps 2π upward (middle glyph is at the top, ends at the bottom).
     *   - -1  → full circle, text sweeps 2π downward.
     *   - positive values produce an upward arc (rainbow/smile): the middle glyph has a smaller y
     *     (higher on screen) than the end glyphs, because y grows downward.
     *
     * Circle-center convention for positive curve:
     *   The center is placed at y = +R relative to the straight baseline (i.e. BELOW the text in
     *   screen coordinates).  The mid-point of the text sits at the top of the arc (smallest y),
     *   and the ends curve down toward the center.  For negative curve the center is above the
     *   baseline (y = -|R|), inverting the bow.
     *
     * Returns the combined polylines from all glyphs with their closed flags preserved.
     */
    fun layoutOnArc(glyphs: List<GlyphRun>, curve: Double): List<Polyline> {
        if (glyphs.isEmpty()) return emptyList()

        val totalWidth = glyphs.sumOf { it.advanceMm }
        val theta = curve * 2.0 * PI   // signed total angular sweep

        // Straight layout when curve is negligible or the text has no width.
        if (abs(theta) < 1e-6 || totalWidth < 1e-12) {
            return straightLayout(glyphs)
        }

        // R is signed: positive means the center is below the baseline (curve > 0 → upward arc).
        val R = totalWidth / theta

        // Circle center in the coordinate frame where the straight baseline starts at x = 0.
        // The mid-point of the text (x = W/2) maps to the apex of the arc.
        // For R > 0 the center is at (W/2, R) — below the baseline.
        val cx = totalWidth / 2.0
        val cy = R   // positive R → center below baseline → upward arc

        val result = mutableListOf<Polyline>()
        var cumulative = 0.0

        for (glyph in glyphs) {
            // Arc-length position of this glyph's baseline center.
            val s = cumulative + glyph.advanceMm / 2.0

            // Angle from the apex (text midpoint is at φ = 0, i.e. directly above the center).
            // φ is measured along the arc; positive φ rotates clockwise (to the right).
            val phi = (s - totalWidth / 2.0) / R

            // Position of the glyph's baseline origin on the arc.
            // A point at arc-length offset d from the apex of a circle of radius R sits at:
            //   x = cx + R * sin(φ)
            //   y = cy - R * cos(φ)   ← subtracting because the apex is the top (smallest y)
            val glyphOriginX = cx + R * sin(phi)
            val glyphOriginY = cy - R * cos(phi)

            // Each point in glyph-local space is rotated by φ around (0, 0) then translated.
            // Rotation by φ (clockwise positive, matching screen y-down):
            //   x' =  x·cos(φ) + y·sin(φ)   (wait — need correct sign convention)
            //
            // In screen coordinates (y down), a clockwise rotation by angle φ is:
            //   x' =  x·cos(φ) - y·sin(φ)   ← standard 2-D clockwise rotation
            //   y' =  x·sin(φ) + y·cos(φ)
            //
            // But the glyph must follow the tangent, which for a point at arc angle φ on a
            // standard circle points in direction (cos φ, sin φ).  The original glyph baseline
            // is horizontal (+x direction), so we rotate by φ so the glyph's +x aligns with
            // the tangent.  A rotation that maps +x → (cos φ, sin φ) is:
            //   x' =  x·cos(φ) - y·sin(φ)
            //   y' =  x·sin(φ) + y·cos(φ)
            // (This is a standard counter-clockwise rotation by φ in a right-handed frame,
            //  which corresponds to a clockwise tilt as φ increases going right on the arc.)
            val cosP = cos(phi)
            val sinP = sin(phi)

            for (poly in glyph.polylines) {
                val transformed = poly.points.map { p ->
                    val rx = p.xMm * cosP - p.yMm * sinP
                    val ry = p.xMm * sinP + p.yMm * cosP
                    Pt(glyphOriginX + rx, glyphOriginY + ry)
                }
                result.add(Polyline(transformed, poly.closed))
            }

            cumulative += glyph.advanceMm
        }

        return result
    }

    // ---------------------------------------------------------------------------
    // Internal helpers
    // ---------------------------------------------------------------------------

    private fun straightLayout(glyphs: List<GlyphRun>): List<Polyline> {
        val result = mutableListOf<Polyline>()
        var x = 0.0
        for (glyph in glyphs) {
            for (poly in glyph.polylines) {
                val shifted = poly.points.map { p -> Pt(p.xMm + x, p.yMm) }
                result.add(Polyline(shifted, poly.closed))
            }
            x += glyph.advanceMm
        }
        return result
    }
}
