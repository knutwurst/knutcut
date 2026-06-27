package de.knutwurst.knutcut.svgcore

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Primitive shapes as closed polylines in millimeters, centered on the origin. */
object Shapes {

    fun rect(widthMm: Double, heightMm: Double): Polyline {
        val w = widthMm / 2; val h = heightMm / 2
        return Polyline(listOf(Pt(-w, -h), Pt(w, -h), Pt(w, h), Pt(-w, h), Pt(-w, -h)), closed = true)
    }

    fun circle(diameterMm: Double, segments: Int = 72): Polyline {
        val r = diameterMm / 2
        val pts = (0..segments).map { i ->
            val a = 2 * PI * i / segments
            Pt(r * cos(a), r * sin(a))
        }
        return Polyline(pts, closed = true)
    }

    /** A regular polygon with [sides] corners, sized to [diameterMm] across its circumscribed circle. */
    fun regularPolygon(sides: Int, diameterMm: Double): Polyline {
        require(sides >= 3)
        val r = diameterMm / 2
        val start = -PI / 2 // first corner at the top
        val pts = (0..sides).map { i ->
            val a = start + 2 * PI * i / sides
            Pt(r * cos(a), r * sin(a))
        }
        return Polyline(pts, closed = true)
    }

    /** A star with [points] tips; [innerRatio] is the inner radius as a fraction of the outer. */
    fun star(points: Int, diameterMm: Double, innerRatio: Double = 0.5): Polyline {
        require(points >= 2)
        val outer = diameterMm / 2
        val inner = outer * innerRatio
        val start = -PI / 2
        val pts = ArrayList<Pt>(points * 2 + 1)
        for (i in 0 until points * 2) {
            val r = if (i % 2 == 0) outer else inner
            val a = start + PI * i / points
            pts.add(Pt(r * cos(a), r * sin(a)))
        }
        pts.add(pts.first())
        return Polyline(pts, closed = true)
    }
}
