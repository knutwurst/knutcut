package de.knutwurst.knutcut.svgcore

/**
 * Bilinear (4-corner cage) warp.
 *
 * Given a quad defined by corners TL, TR, BR, BL the source geometry is mapped so that each
 * source point at normalized position (u, v) — where u goes left-to-right and v goes top-to-bottom
 * across the source bounding box — lands at the bilinear interpolation of the four corners:
 *
 *   mapped = (1-u)(1-v)*TL + u(1-v)*TR + uv*BR + (1-u)v*BL
 *
 * When the four corners equal the source bounding box corners the mapping is the identity.
 */
object EnvelopeWarp {

    /**
     * Apply a 4-corner bilinear warp to [source].
     *
     * [sourceBounds] defines the normalization domain; it should be the bounding box of the
     * original (pre-warp) geometry.  Zero-width or zero-height bounds are handled gracefully:
     * the corresponding axis is pinned to u=0 or v=0.
     *
     * Corner order: [tl] = top-left, [tr] = top-right, [br] = bottom-right, [bl] = bottom-left.
     * Each polyline's [Polyline.closed] flag is preserved in the output.
     */
    fun bilinear(
        source: List<Polyline>,
        sourceBounds: Bounds,
        tl: Pt,
        tr: Pt,
        br: Pt,
        bl: Pt,
    ): List<Polyline> {
        val w = sourceBounds.widthMm
        val h = sourceBounds.heightMm
        val minX = sourceBounds.minX
        val minY = sourceBounds.minY

        return source.map { poly ->
            val mapped = poly.points.map { p ->
                val u = if (w > 1e-12) (p.xMm - minX) / w else 0.0
                val v = if (h > 1e-12) (p.yMm - minY) / h else 0.0
                val wTL = (1 - u) * (1 - v)
                val wTR = u * (1 - v)
                val wBR = u * v
                val wBL = (1 - u) * v
                Pt(
                    xMm = wTL * tl.xMm + wTR * tr.xMm + wBR * br.xMm + wBL * bl.xMm,
                    yMm = wTL * tl.yMm + wTR * tr.yMm + wBR * br.yMm + wBL * bl.yMm,
                )
            }
            Polyline(mapped, poly.closed)
        }
    }
}
