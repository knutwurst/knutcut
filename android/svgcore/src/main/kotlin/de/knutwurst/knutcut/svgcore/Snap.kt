package de.knutwurst.knutcut.svgcore

import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Pure snapping/resize maths shared by the mat editor. Kept Android-free so it can be unit-tested
 * (and reused by cricut-export) instead of living in the view model.
 */
object Snap {

    /** Round [value] to the nearest multiple of [stepMm]; returns [value] unchanged when step <= 0. */
    fun toStep(value: Double, stepMm: Double): Double =
        if (stepMm <= 0.0) value else (value / stepMm).roundToLong() * stepMm

    /**
     * New centre for a layer whose placed top-left should land on the [stepMm] grid. [tlOffset] is
     * (top-left − centre), which is invariant under translation, so the caller can pass the running
     * (un-snapped) centre each frame without losing sub-grid movement.
     */
    fun gridCenter(center: Pt, tlOffset: Pt, stepMm: Double): Pt {
        if (stepMm <= 0.0) return center
        val tlx = toStep(center.xMm + tlOffset.xMm, stepMm)
        val tly = toStep(center.yMm + tlOffset.yMm, stepMm)
        return Pt(tlx - tlOffset.xMm, tly - tlOffset.yMm)
    }

    /** The candidate nearest to [value] within [tolMm], or null if none is close enough. */
    fun nearestWithin(value: Double, candidates: List<Double>, tolMm: Double): Double? {
        if (tolMm <= 0.0) return null
        return candidates.filter { abs(it - value) <= tolMm }.minByOrNull { abs(it - value) }
    }

    /**
     * Uniform scale factor for a corner drag that keeps the current aspect ratio: the drag vector
     * ([ux],[uy]) projected onto the start diagonal ([dxs],[dys]), clamped to a small positive minimum.
     */
    fun uniformScaleFactor(ux: Double, uy: Double, dxs: Double, dys: Double, min: Double = 0.02): Double {
        val len2 = dxs * dxs + dys * dys
        if (len2 <= 1e-9) return 1.0
        return ((ux * dxs + uy * dys) / len2).coerceAtLeast(min)
    }
}
