package de.knutwurst.knutcut.data

/** Maps 1:1 to [de.knutwurst.knutcut.svgcore.PathWarp.Baseline]. */
enum class DeformBaseline { TOP, CENTER, BOTTOM }

/**
 * Describes a non-destructive geometric deformation applied to a layer's source geometry.
 * The layer stores both the original [Layer.deformSource] and the result in [Layer.polylines];
 * [DeformEngine.apply] re-derives the result whenever the spec changes.
 *
 * Sealed so the compiler enforces exhaustive when-expressions. New variants can be added here
 * without breaking existing callers that handle [CircleDeform].
 */
sealed interface DeformSpec

/**
 * Warp the source geometry onto a circle.
 *
 * The source width spans the full circumference ([radiusMm] * 2π).
 * [startAngleDeg] is measured counter-clockwise from the positive x-axis.
 */
data class CircleDeform(
    val centerXMm: Double,
    val centerYMm: Double,
    val radiusMm: Double,
    val startAngleDeg: Double,
    val clockwise: Boolean,
    val baseline: DeformBaseline,
) : DeformSpec
