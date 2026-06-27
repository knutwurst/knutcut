package de.knutwurst.knutcut.svgcore

/**
 * Turns polylines (in millimeters) into Silhouette GPGL pen commands: "M<y>,<x>" to lift and travel
 * to the start of a contour, then "D<y>,<x>" to cut to each following point. Coordinates are integer
 * Silhouette units (see [mmToSu]) and — matching inkscape-silhouette's Graphtec.py — the editor's
 * (x, y) is emitted swapped as (y, x); the cutter's native axes are the transpose of the editor's.
 * Consecutive points that round to the same unit are dropped so we never emit a zero-length move.
 */
object GpglEncoder {

    fun encode(polylines: List<Polyline>): List<String> {
        val out = ArrayList<String>()
        for (pl in polylines) {
            val pts = pl.points
            if (pts.isEmpty()) continue
            val fx = mmToSu(pts.first().xMm)
            val fy = mmToSu(pts.first().yMm)
            out.add("M$fy,$fx")
            var prevX = fx; var prevY = fy
            for (k in 1 until pts.size) {
                val x = mmToSu(pts[k].xMm)
                val y = mmToSu(pts[k].yMm)
                if (x == prevX && y == prevY) continue
                out.add("D$y,$x")
                prevX = x; prevY = y
            }
            if (pl.closed && (prevX != fx || prevY != fy)) {
                out.add("D$fy,$fx")
            }
        }
        return out
    }
}
