package de.knutwurst.knutcut.svgcore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import kotlin.math.hypot
import org.junit.Test

class CutOrderTest {

    private fun seg(x0: Double, x1: Double) = Polyline(listOf(Pt(x0, 0.0), Pt(x1, 0.0)), false)

    /** Total pen-up travel: the gap from each contour's end to the next contour's start. */
    private fun travel(pls: List<Polyline>): Double =
        pls.zipWithNext().sumOf { (a, b) -> hypot(b.points.first().xMm - a.points.last().xMm, b.points.first().yMm - a.points.last().yMm) }

    @Test
    fun shortensTravelByVisitingTheNearestContourNext() {
        // Naive order A,B,C jumps far and back; nearest-neighbour should visit A,C,B.
        val a = seg(0.0, 1.0)
        val b = seg(10.0, 11.0)
        val c = seg(2.0, 3.0)
        val naive = listOf(a, b, c)
        val opt = CutOrder.optimize(naive)

        assertEquals("keeps every contour", 3, opt.size)
        assertEquals("starts from the first contour", a, opt.first())
        assertEquals("visits the nearer contour (C) before the far one (B)", listOf(a, c, b), opt)
        assertTrue("optimised travel must not be longer", travel(opt) <= travel(naive))
        assertEquals(18.0, travel(naive), 1e-9)
        assertEquals(8.0, travel(opt), 1e-9)
    }

    @Test
    fun keepsShortInputUnchanged() {
        val pls = listOf(seg(0.0, 1.0), seg(5.0, 6.0))
        assertEquals(pls, CutOrder.optimize(pls))
    }

    @Test
    fun preservesEveryContourAsAPermutation() {
        val pls = (0..9).map { seg(it.toDouble() * 3, it.toDouble() * 3 + 1) }.shuffledStable()
        val opt = CutOrder.optimize(pls)
        assertEquals(pls.size, opt.size)
        assertEquals(pls.toSet(), opt.toSet())
    }

    // Deterministic reordering (no RNG): reverse, so the input isn't already optimal.
    private fun <T> List<T>.shuffledStable(): List<T> = this.reversed()
}
