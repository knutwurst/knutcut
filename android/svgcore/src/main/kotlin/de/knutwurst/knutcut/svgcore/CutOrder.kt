package de.knutwurst.knutcut.svgcore

/**
 * Greedy nearest-neighbor reordering of polylines to shorten the pen-up travel between contours:
 * start with the first, then always cut the contour whose start point is closest to where the last
 * one ended. Order-only — geometry is untouched. Off by default in the app because it changes the
 * emitted path and wants a real-device check before being trusted.
 */
object CutOrder {
    fun optimize(pls: List<Polyline>): List<Polyline> {
        if (pls.size < 3) return pls
        val remaining = pls.toMutableList()
        val out = ArrayList<Polyline>(pls.size)
        var cur = remaining.removeAt(0)
        out.add(cur)
        var end = cur.points.lastOrNull() ?: Pt(0.0, 0.0)
        while (remaining.isNotEmpty()) {
            var best = 0
            var bestD = Double.MAX_VALUE
            for (i in remaining.indices) {
                val s = remaining[i].points.firstOrNull() ?: continue
                val dx = s.xMm - end.xMm; val dy = s.yMm - end.yMm
                val d = dx * dx + dy * dy
                if (d < bestD) { bestD = d; best = i }
            }
            cur = remaining.removeAt(best)
            out.add(cur)
            end = cur.points.lastOrNull() ?: end
        }
        return out
    }
}
