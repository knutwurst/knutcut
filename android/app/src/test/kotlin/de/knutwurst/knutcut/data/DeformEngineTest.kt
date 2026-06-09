package de.knutwurst.knutcut.data

import de.knutwurst.knutcut.svgcore.Polyline
import de.knutwurst.knutcut.svgcore.Pt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import kotlin.math.PI
import kotlin.math.hypot

/** Unit tests for DeformEngine — pure JVM, no Robolectric needed. */
class DeformEngineTest {

    // A horizontal strip spanning one full circle circumference at a given radius.
    private fun stripForCircle(radiusMm: Double): List<Polyline> {
        val circum = 2 * PI * radiusMm
        val n = 20
        return listOf(
            Polyline(
                (0..n).map { i -> Pt(i.toDouble() / n * circum, 5.0) },
                closed = false,
            )
        )
    }

    @Test
    fun circleDeformPlacesPointsNearRadius() {
        val radius = 40.0
        val centerX = 50.0
        val centerY = 50.0
        val spec = CircleDeform(
            centerXMm = centerX,
            centerYMm = centerY,
            radiusMm = radius,
            startAngleDeg = 0.0,
            clockwise = false,
            baseline = DeformBaseline.BOTTOM,   // source y == maxY → v = 0 → lands at radius
        )
        // Source: y-value equals maxY (the strip's only y), so v = 0, result should be at radius.
        val source = stripForCircle(radius)
        val result = DeformEngine.apply(spec, source)

        for (p in result.flatMap { it.points }) {
            val dist = hypot(p.xMm - centerX, p.yMm - centerY)
            assertEquals("point should be ~radius from center", radius, dist, 2.0)
        }
    }

    @Test
    fun emptySourceReturnedUnchanged() {
        val spec = CircleDeform(0.0, 0.0, 30.0, 0.0, clockwise = false, baseline = DeformBaseline.BOTTOM)
        val empty = emptyList<Polyline>()
        assertSame(empty, DeformEngine.apply(spec, empty))
    }

    @Test
    fun sourceWithNoPointsReturnedUnchanged() {
        val spec = CircleDeform(0.0, 0.0, 30.0, 0.0, clockwise = false, baseline = DeformBaseline.BOTTOM)
        // A polyline with empty points list has no points, so no warp should occur.
        val source = listOf(Polyline(emptyList(), closed = false))
        assertSame(source, DeformEngine.apply(spec, source))
    }
}
