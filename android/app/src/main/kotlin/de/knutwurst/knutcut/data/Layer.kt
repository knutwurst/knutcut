package de.knutwurst.knutcut.data

import de.knutwurst.knutcut.svgcore.EditablePath
import de.knutwurst.knutcut.svgcore.Polyline
import de.knutwurst.knutcut.svgcore.Pt

/**
 * One shape/layer of the loaded SVG, with its tool, visibility, and its own placement on the mat
 * ([centerMm] = where its center sits, plus per-layer scale and rotation), so split layers can be
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
    /** Original SVG color (packed ARGB), or null when the element carried no color information. */
    val colorArgb: Int? = null,
    /** Per-polyline color (packed ARGB) for merged layers that hold shapes of different colors,
     *  aligned 1:1 with [polylines]. Null means every polyline uses [colorArgb]. */
    val polylineColors: List<Int?>? = null,
    /**
     * Editable Bézier path for freehand-drawn layers.  When non-null,
     * [polylines] == listOf(editPath.toPolyline()); [polylines] remains the single source of truth
     * for rendering and cutting.
     */
    val editPath: EditablePath? = null,
    /**
     * Stable local-frame pivot for an editable layer, captured when [editPath] is created.
     *
     * The placement matrix normally pivots about the live bounding-box center of [polylines]. While
     * node-editing that center moves as the geometry changes, which would re-center the whole shape
     * and make the other nodes drift (and a drag feed back on itself). Freezing the pivot here keeps
     * the editing frame fixed, so dragging a node moves only that node. Non-null iff [editPath] is.
     */
    val editOriginMm: Pt? = null,
    /**
     * Source text and font parameters for text layers.  Non-null only on layers created by the
     * text tool; used by the curve-text feature to re-render glyphs at a new arc value without
     * losing the original text.
     */
    val textSpec: TextSpec? = null,
) {
    /** Color of each polyline, expanding the single [colorArgb] when no per-polyline list is set. */
    fun colorList(): List<Int?> = polylineColors ?: List(polylines.size) { colorArgb }
}
