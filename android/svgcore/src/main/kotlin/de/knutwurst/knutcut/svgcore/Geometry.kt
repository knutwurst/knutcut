package de.knutwurst.knutcut.svgcore

import kotlin.math.roundToInt

/** A point in millimetres. */
data class Pt(val xMm: Double, val yMm: Double)

/**
 * A connected run of points in millimetres.
 * [closed] means the last point joins back to the first (a filled outline rather than an open line).
 */
data class Polyline(val points: List<Pt>, val closed: Boolean)

/** A named shape from one SVG element (its polylines in mm) — a layer in the editor. */
data class SvgShape(val name: String, val polylines: List<Polyline>)

/** Axis-aligned bounding box in millimetres. */
data class Bounds(val minX: Double, val minY: Double, val maxX: Double, val maxY: Double) {
    val widthMm: Double get() = maxX - minX
    val heightMm: Double get() = maxY - minY

    companion object {
        /** Bounding box, or null when there are no points (so callers never crash on an empty layer). */
        fun ofOrNull(points: List<Pt>): Bounds? = if (points.isEmpty()) null else of(points)

        fun of(points: List<Pt>): Bounds {
            require(points.isNotEmpty()) { "no points" }
            var minX = Double.MAX_VALUE; var minY = Double.MAX_VALUE
            var maxX = -Double.MAX_VALUE; var maxY = -Double.MAX_VALUE
            for (p in points) {
                if (p.xMm < minX) minX = p.xMm
                if (p.yMm < minY) minY = p.yMm
                if (p.xMm > maxX) maxX = p.xMm
                if (p.yMm > maxY) maxY = p.yMm
            }
            return Bounds(minX, minY, maxX, maxY)
        }
    }
}

/** Plotter resolution. The VEVO Smart 1 uses HPGL units of 1/40 mm (40 units per mm). */
const val UNITS_PER_MM = 40

/** Millimetres to plotter units, rounded to the nearest unit. */
fun mmToUnits(mm: Double): Int = (mm * UNITS_PER_MM).roundToInt()

/** Silhouette/Graphtec resolution: GPGL units ("SU") of 1/20 mm (20 units per mm). */
const val SU_PER_MM = 20

/** Millimetres to Silhouette units, rounded to the nearest unit (matches inkscape-silhouette _mm_2_SU). */
fun mmToSu(mm: Double): Int = (mm * SU_PER_MM).roundToInt()
