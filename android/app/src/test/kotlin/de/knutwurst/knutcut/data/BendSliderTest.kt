package de.knutwurst.knutcut.data

import de.knutwurst.knutcut.svgcore.Bounds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs

/** Unit tests for [bendToCircleDeform] and [CircleDeform.bendValue] — pure JVM, no Robolectric. */
class BendSliderTest {

    private val bounds = Bounds(0.0, 0.0, 100.0, 20.0)

    // -- snap-to-zero detent --------------------------------------------------

    @Test
    fun value0returnsNull() {
        assertNull(bendToCircleDeform(bounds, 0))
    }

    @Test
    fun valuePlus1returnsNull() {
        assertNull(bendToCircleDeform(bounds, 1))
    }

    @Test
    fun valuePlus2returnsNull() {
        assertNull(bendToCircleDeform(bounds, 2))
    }

    @Test
    fun valueMinus1returnsNull() {
        assertNull(bendToCircleDeform(bounds, -1))
    }

    @Test
    fun valueMinus2returnsNull() {
        assertNull(bendToCircleDeform(bounds, -2))
    }

    // -- |value| = 100: full-circle wrap --------------------------------------

    @Test
    fun value100radiusMatchesCircleDeformDefault() {
        val spec = bendToCircleDeform(bounds, 100)!!
        val expected = bounds.widthMm / (2.0 * PI)
        assertEquals("radius = width/2π at value=100", expected, spec.radiusMm, 1e-9)
    }

    @Test
    fun value100isNotClockwise() {
        val spec = bendToCircleDeform(bounds, 100)!!
        assertTrue("positive value → clockwise=false", !spec.clockwise)
    }

    @Test
    fun valueMinus100radiusSameAsPlus100() {
        val pos = bendToCircleDeform(bounds, 100)!!
        val neg = bendToCircleDeform(bounds, -100)!!
        assertEquals("radius identical for ±100", pos.radiusMm, neg.radiusMm, 1e-9)
    }

    @Test
    fun valueMinus100isClockwise() {
        val spec = bendToCircleDeform(bounds, -100)!!
        assertTrue("negative value → clockwise=true", spec.clockwise)
    }

    // -- value = 50: half-circle (θ = π) -------------------------------------

    @Test
    fun value50radiusIsWidthOverPi() {
        val spec = bendToCircleDeform(bounds, 50)!!
        val expected = bounds.widthMm / PI
        assertEquals("radius = width/π at value=50", expected, spec.radiusMm, 1e-9)
    }

    // -- center equals bounds centre -----------------------------------------

    @Test
    fun centerEqualsBoundsCenter() {
        val b = Bounds(10.0, 20.0, 90.0, 60.0)
        val spec = bendToCircleDeform(b, 75)!!
        assertEquals("centerX at bounds center", 50.0, spec.centerXMm, 1e-9)
        assertEquals("centerY at bounds center", 40.0, spec.centerYMm, 1e-9)
    }

    // -- round-trip: bendValue(bendToCircleDeform(v)) ≈ v --------------------

    private fun roundTrip(v: Int) {
        val spec = bendToCircleDeform(bounds, v)!!
        val recovered = spec.bendValue(bounds)
        assertTrue(
            "round-trip |$v|: expected ≈$v, got $recovered",
            abs(recovered - v) <= 2
        )
    }

    @Test fun roundTripPlus25()  { roundTrip(25) }
    @Test fun roundTripMinus25() { roundTrip(-25) }
    @Test fun roundTripPlus50()  { roundTrip(50) }
    @Test fun roundTripMinus50() { roundTrip(-50) }
    @Test fun roundTripPlus100() { roundTrip(100) }
    @Test fun roundTripMinus100(){ roundTrip(-100) }
}
