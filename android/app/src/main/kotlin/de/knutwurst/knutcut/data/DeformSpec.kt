package de.knutwurst.knutcut.data

import de.knutwurst.knutcut.svgcore.Bounds
import de.knutwurst.knutcut.svgcore.PathNode
import de.knutwurst.knutcut.svgcore.Pt
import kotlin.math.PI

// ---------------------------------------------------------------------------
// EnvelopeDeform (Phase 5: 4-corner cage / bilinear warp)
// ---------------------------------------------------------------------------

/**
 * Warp the source geometry by dragging the four corners of a bounding quad.
 *
 * Corners are stored in the layer's local (pre-transform) coordinate space.
 * Corner order: [tl] top-left, [tr] top-right, [br] bottom-right, [bl] bottom-left.
 * When the four corners match the source bounding box the mapping is the identity.
 */
data class EnvelopeDeform(
    val tl: Pt,
    val tr: Pt,
    val br: Pt,
    val bl: Pt,
) : DeformSpec

/**
 * Build an [EnvelopeDeform] whose corners equal the bounding box of [bounds].
 * Applying this via [DeformEngine] returns geometry identical to the source (identity warp).
 */
fun envelopeDeformDefault(bounds: Bounds): EnvelopeDeform = EnvelopeDeform(
    tl = Pt(bounds.minX, bounds.minY),
    tr = Pt(bounds.maxX, bounds.minY),
    br = Pt(bounds.maxX, bounds.maxY),
    bl = Pt(bounds.minX, bounds.maxY),
)

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

// ---------------------------------------------------------------------------
// PathDeform
// ---------------------------------------------------------------------------

/**
 * Warp the source geometry onto an arbitrary Bézier guide path.
 *
 * The guide is stored as a list of [PathNode]s (same type as svgcore uses). On each engine run the
 * nodes are flattened to a polyline, arc-length parameterised, and used by [PathWarp.alongPath].
 * [closed] is normally false for a bend guide.
 */
data class PathDeform(
    val guide: List<PathNode>,
    val closed: Boolean = false,
    val baseline: DeformBaseline,
) : DeformSpec

/**
 * Build a [PathDeform] that bends the content along a smooth symmetric arc.
 *
 * The guide spans the source width horizontally, starting at the source's left edge and ending at
 * the right, at the Y coordinate dictated by [baseline]. The midpoint of the guide is shifted
 * perpendicular to the guide direction by [curvatureMm]:
 *
 * - [curvatureMm] = 0 → straight line → identity warp up to arc-length reparameterisation.
 * - [curvatureMm] > 0 → the guide bows UPWARD (negative Y in screen/plotter coordinates where Y
 *   increases downward), so the centre of the content lifts while the ends stay in place.
 * - [curvatureMm] < 0 → the guide bows downward.
 *
 * The guide is a 3-node cubic Bézier path: start anchor, mid anchor (the apex of the arc), end
 * anchor. Symmetric cubic handles on the start and end anchors produce a smooth, kink-free arc.
 * The handle length is set to 1/3 of the guide width, which gives a good approximation of a
 * circular arc for moderate curvature values.
 */
fun bendDeformDefault(
    bounds: Bounds,
    curvatureMm: Double,
    baseline: DeformBaseline = DeformBaseline.CENTER,
): PathDeform {
    val baselineY = when (baseline) {
        DeformBaseline.TOP    -> bounds.minY
        DeformBaseline.CENTER -> (bounds.minY + bounds.maxY) / 2.0
        DeformBaseline.BOTTOM -> bounds.maxY
    }
    val x0 = bounds.minX
    val x1 = bounds.maxX
    val xMid = (x0 + x1) / 2.0
    val width = bounds.widthMm.coerceAtLeast(1.0)
    // Handle length: 1/3 of the half-width gives a smooth arc without over-shooting.
    val handleLen = width / 3.0

    // Start node: anchor at (x0, baselineY), handle pointing right.
    val start = PathNode(
        anchor = Pt(x0, baselineY),
        handleOut = Pt(x0 + handleLen, baselineY),
    )
    // Mid node: apex shifted by -curvatureMm in Y (negative because Y-down means up is negative).
    val mid = PathNode(
        anchor = Pt(xMid, baselineY - curvatureMm),
        handleIn  = Pt(xMid - handleLen, baselineY - curvatureMm),
        handleOut = Pt(xMid + handleLen, baselineY - curvatureMm),
    )
    // End node: anchor at (x1, baselineY), handle pointing left (mirror of start).
    val end = PathNode(
        anchor = Pt(x1, baselineY),
        handleIn = Pt(x1 - handleLen, baselineY),
    )
    return PathDeform(guide = listOf(start, mid, end), closed = false, baseline = baseline)
}
