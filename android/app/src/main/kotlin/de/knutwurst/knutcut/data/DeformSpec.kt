package de.knutwurst.knutcut.data

import de.knutwurst.knutcut.svgcore.Bounds
import kotlin.math.PI

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

/**
 * Produce a [CircleDeform] with sensible defaults for [bounds]:
 *
 * - Center at the bounds center.
 * - Radius = widthMm / (2π), so the source width wraps exactly once around the circle.
 * - startAngleDeg = 90 (positive y-axis, which is the bottom of the screen/mat in plotter space).
 * - clockwise = true so that content reads upright when viewed from outside the circle on the
 *   top side (outer arc on top). In plotter/screen space Y increases downward; a CW circle
 *   starting at 90° (bottom of the coordinate system, top of the visual circle when viewed
 *   with Y-down) traces leftward across the top, which is the direction text reads.
 *   Pairing this with baseline = BOTTOM places the text baseline on the guide circle and
 *   pushes glyphs outward (away from center).
 * - clockwise = true: the tangent at each guide point faces counter to the CCW direction, so the
 *   normal (tangent rotated +90°) points inward in the CCW sense — i.e. OUTWARD from the center
 *   in screen/plotter space (Y increases downward). With baseline = BOTTOM the above-baseline
 *   content (letter bodies, v < 0) gets a positive outward offset, so it sits OUTSIDE the circle
 *   and is readable on the outer/top arc.
 */
fun circleDeformDefault(
    bounds: Bounds,
    baseline: DeformBaseline = DeformBaseline.BOTTOM,
): CircleDeform {
    val cx = (bounds.minX + bounds.maxX) / 2.0
    val cy = (bounds.minY + bounds.maxY) / 2.0
    val radius = bounds.widthMm / (2.0 * PI)
    return CircleDeform(
        centerXMm = cx,
        centerYMm = cy,
        radiusMm = radius.coerceAtLeast(5.0),
        startAngleDeg = 90.0,
        clockwise = true,
        baseline = baseline,
    )
}
