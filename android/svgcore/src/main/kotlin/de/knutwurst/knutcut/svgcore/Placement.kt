package de.knutwurst.knutcut.svgcore

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** Pure placement maths shared by the editor and the cut path, kept out of the Android view model. */
object Placement {

    /**
     * The matrix that places a shape with [localBounds] so its center sits at [center], scaled and
     * rotated (and optionally mirrored) about its own center. Mirroring is a sign flip on the scale,
     * so the magnitude in [scaleX]/[scaleY] stays positive and resize handles keep working.
     *
     * [localCenter] overrides the pivot: pass a fixed local point to pivot about it instead of the
     * live bounds center. Editable layers use this so editing a node doesn't shift the whole frame.
     */
    fun matrix(
        localBounds: Bounds,
        center: Pt,
        scaleX: Double,
        scaleY: Double,
        rotationDeg: Double,
        flipX: Boolean = false,
        flipY: Boolean = false,
        localCenter: Pt? = null,
    ): Matrix {
        val lcx = localCenter?.xMm ?: ((localBounds.minX + localBounds.maxX) / 2)
        val lcy = localCenter?.yMm ?: ((localBounds.minY + localBounds.maxY) / 2)
        val sx = scaleX * if (flipX) -1.0 else 1.0
        val sy = scaleY * if (flipY) -1.0 else 1.0
        return Matrix.translate(center.xMm, center.yMm) *
            Matrix.rotate(rotationDeg) *
            Matrix.scale(sx, sy) *
            Matrix.translate(-lcx, -lcy)
    }

    /** Scale factor that turns a bounding-box length of [current] into [target] (1.0 if degenerate). */
    fun scaleFor(current: Double, target: Double): Double = if (current > 1e-6) target / current else 1.0

    /** New scale magnitudes and placement center produced by a resize-handle drag. */
    data class Resize(val scaleX: Double, val scaleY: Double, val center: Pt)

    /**
     * New scale + center for dragging a resize handle to [dragWorld] (world mm) while the opposite
     * handle at [anchorWorld] stays fixed.
     *
     * [handle]: 0..3 = a corner (uniform/proportional scale); 5 or 7 = a left/right side handle
     * (x only); any other = a top/bottom side handle (y only). The *Local points are in the layer's
     * local (pre-transform) frame; [rotationDeg]/[flipX]/[flipY] describe its placement.
     *
     * The placement matrix folds mirroring into the scale sign (see [matrix]), so the drag delta —
     * once un-rotated — lives in a flipped frame. The projection therefore carries the same flip sign;
     * without it, resizing a mirrored layer projects against the wrong sign and collapses to [minScale].
     */
    fun resize(
        handle: Int,
        dragWorld: Pt,
        anchorWorld: Pt,
        anchorLocal: Pt,
        draggedLocal: Pt,
        centerLocal: Pt,
        startScaleX: Double,
        startScaleY: Double,
        rotationDeg: Double,
        flipX: Boolean = false,
        flipY: Boolean = false,
        minScale: Double = 0.02,
    ): Resize {
        val rot = rotationDeg * PI / 180.0
        // Bring the world drag delta into the layer's unrotated (still scaled + flipped) frame.
        val dxw = dragWorld.xMm - anchorWorld.xMm
        val dyw = dragWorld.yMm - anchorWorld.yMm
        val cn = cos(-rot); val sn = sin(-rot)
        val ux = dxw * cn - dyw * sn
        val uy = dxw * sn + dyw * cn
        val fx = if (flipX) -1.0 else 1.0
        val fy = if (flipY) -1.0 else 1.0
        val ldx = draggedLocal.xMm - anchorLocal.xMm
        val ldy = draggedLocal.yMm - anchorLocal.yMm
        var sx = startScaleX
        var sy = startScaleY
        when {
            handle < 4 -> {
                val dxs = fx * ldx * startScaleX
                val dys = fy * ldy * startScaleY
                val len2 = dxs * dxs + dys * dys
                val k = if (len2 > 1e-9) ((ux * dxs + uy * dys) / len2).coerceAtLeast(minScale) else 1.0
                sx = startScaleX * k
                sy = startScaleY * k
            }
            handle == 5 || handle == 7 ->
                if (abs(ldx) > 1e-6) sx = (fx * ux / ldx).coerceAtLeast(minScale)
            else ->
                if (abs(ldy) > 1e-6) sy = (fy * uy / ldy).coerceAtLeast(minScale)
        }
        // Keep the anchor fixed: center = anchor + R(θ)·F·S·(centerLocal − anchorLocal).
        val ax = (centerLocal.xMm - anchorLocal.xMm) * sx * fx
        val ay = (centerLocal.yMm - anchorLocal.yMm) * sy * fy
        val cp = cos(rot); val sp = sin(rot)
        val center = Pt(anchorWorld.xMm + (ax * cp - ay * sp), anchorWorld.yMm + (ax * sp + ay * cp))
        return Resize(sx, sy, center)
    }
}
