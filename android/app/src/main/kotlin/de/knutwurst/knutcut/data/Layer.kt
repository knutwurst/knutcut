package de.knutwurst.knutcut.data

import de.knutwurst.knutcut.svgcore.Polyline
import de.knutwurst.knutcut.svgcore.Pt

/**
 * One shape/layer of the loaded SVG, with its tool, visibility, and its own placement on the mat
 * ([centerMm] = where its centre sits, plus per-layer scale and rotation), so split layers can be
 * arranged independently.
 */
data class Layer(
    val name: String,
    val polylines: List<Polyline>,
    val tool: Tool,
    val visible: Boolean,
    val centerMm: Pt = Pt(0.0, 0.0),
    val scaleX: Double = 1.0,
    val scaleY: Double = 1.0,
    val rotationDeg: Double = 0.0,
    val flipX: Boolean = false,
    val flipY: Boolean = false,
    /** Original SVG colour (packed ARGB), or null when the element carried no colour information. */
    val colorArgb: Int? = null,
    /** Per-polyline colour (packed ARGB) for merged layers that hold shapes of different colours,
     *  aligned 1:1 with [polylines]. Null means every polyline uses [colorArgb]. */
    val polylineColors: List<Int?>? = null,
    /**
     * Active deformation spec, or null when the geometry is not warped.
     * Invariant: when non-null, [polylines] == DeformEngine.apply(deform, deformSource!!).
     */
    val deform: DeformSpec? = null,
    /**
     * Original (pre-warp) geometry preserved so the deformation can be changed or removed without
     * losing the source.  Non-null if and only if [deform] is non-null.
     */
    val deformSource: List<Polyline>? = null,
) {
    /** Colour of each polyline, expanding the single [colorArgb] when no per-polyline list is set. */
    fun colorList(): List<Int?> = polylineColors ?: List(polylines.size) { colorArgb }
}
