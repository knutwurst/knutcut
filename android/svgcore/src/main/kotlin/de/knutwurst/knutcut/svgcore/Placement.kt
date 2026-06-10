package de.knutwurst.knutcut.svgcore

/** Pure placement maths shared by the editor and the cut path, kept out of the Android view model. */
object Placement {

    /**
     * The matrix that places a shape with [localBounds] so its centre sits at [center], scaled and
     * rotated (and optionally mirrored) about its own centre. Mirroring is a sign flip on the scale,
     * so the magnitude in [scaleX]/[scaleY] stays positive and resize handles keep working.
     *
     * [localCenter] overrides the pivot: pass a fixed local point to pivot about it instead of the
     * live bounds centre. Editable layers use this so editing a node doesn't shift the whole frame.
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
}
