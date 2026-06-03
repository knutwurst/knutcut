package de.knutwurst.knutcut.data

import de.knutwurst.knutcut.svgcore.Polyline

/** One shape/layer of the loaded SVG, with its tool assignment and visibility. */
data class Layer(
    val name: String,
    val polylines: List<Polyline>,
    val tool: Tool,
    val visible: Boolean,
)
