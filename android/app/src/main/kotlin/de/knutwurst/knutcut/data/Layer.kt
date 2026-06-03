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
)
