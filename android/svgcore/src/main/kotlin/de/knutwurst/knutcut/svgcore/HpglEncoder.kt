package de.knutwurst.knutcut.svgcore

/**
 * Turns polylines (in millimetres) into the plotter's pen commands: "PU<x>,<y>" to lift and travel
 * to the start of a contour, then "PD<x>,<y>" to cut to each following point. Coordinates are
 * integer plotter units (see [mmToUnits]). Consecutive points that round to the same unit are
 * dropped so we never emit a zero-length move.
 */
object HpglEncoder {

    fun encode(polylines: List<Polyline>): List<String> {
        val out = ArrayList<String>()
        for (pl in polylines) {
            val pts = pl.points
            if (pts.isEmpty()) continue
            val fx = mmToUnits(pts.first().xMm)
            val fy = mmToUnits(pts.first().yMm)
            out.add("PU$fx,$fy")
            var prevX = fx; var prevY = fy
            for (k in 1 until pts.size) {
                val x = mmToUnits(pts[k].xMm)
                val y = mmToUnits(pts[k].yMm)
                if (x == prevX && y == prevY) continue
                out.add("PD$x,$y")
                prevX = x; prevY = y
            }
            if (pl.closed && (prevX != fx || prevY != fy)) {
                out.add("PD$fx,$fy")
            }
        }
        return out
    }
}
