package de.knutwurst.knutcut.data

import de.knutwurst.knutcut.svgcore.ArcLengthPath
import de.knutwurst.knutcut.svgcore.Bounds
import de.knutwurst.knutcut.svgcore.EditablePath
import de.knutwurst.knutcut.svgcore.PathWarp
import de.knutwurst.knutcut.svgcore.Polyline
import de.knutwurst.knutcut.svgcore.Pt

/** Bridges app-level [DeformSpec] to the svgcore warp routines. */
object DeformEngine {

    /**
     * Apply [spec] to [source] and return the warped polylines.
     * Returns [source] unchanged when it contains no points.
     */
    fun apply(spec: DeformSpec, source: List<Polyline>): List<Polyline> {
        val pts = source.flatMap { it.points }
        if (pts.isEmpty()) return source

        return when (spec) {
            is CircleDeform -> {
                val bounds = Bounds.of(pts)
                PathWarp.onCircle(
                    source = source,
                    sourceBounds = bounds,
                    center = Pt(spec.centerXMm, spec.centerYMm),
                    radiusMm = spec.radiusMm,
                    startAngleDeg = spec.startAngleDeg,
                    clockwise = spec.clockwise,
                    baseline = spec.baseline.toSvgcore(),
                )
            }
            is PathDeform -> {
                val bounds = Bounds.of(pts)
                val guidePoly = EditablePath(spec.guide, spec.closed).toPolyline()
                val guide = ArcLengthPath(guidePoly)
                PathWarp.alongPath(
                    source = source,
                    sourceBounds = bounds,
                    guide = guide,
                    baseline = spec.baseline.toSvgcore(),
                )
            }
        }
    }

    // ------------------------------------------------------------------

    private fun DeformBaseline.toSvgcore(): PathWarp.Baseline = when (this) {
        DeformBaseline.TOP -> PathWarp.Baseline.TOP
        DeformBaseline.CENTER -> PathWarp.Baseline.CENTER
        DeformBaseline.BOTTOM -> PathWarp.Baseline.BOTTOM
    }
}
