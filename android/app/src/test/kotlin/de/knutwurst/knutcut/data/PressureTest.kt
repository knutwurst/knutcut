package de.knutwurst.knutcut.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PressureTest {

    @Test
    fun mapsTheVevorRangeOntoTheSilhouetteScale() {
        assertEquals("min force -> min pressure", 1, Pressure.silhouette(Materials.FORCE_MIN))
        assertEquals("max force -> max pressure", 33, Pressure.silhouette(Materials.FORCE_MAX))
        // Midpoint of 10..500 is 255 -> middle of 1..33.
        assertEquals(17, Pressure.silhouette(255))
    }

    @Test
    fun clampsOutOfRangeForce() {
        assertEquals("below min clamps to 1", 1, Pressure.silhouette(0))
        assertEquals("above max clamps to 33", 33, Pressure.silhouette(9999))
    }

    @Test
    fun isMonotonicNonDecreasing() {
        var prev = 0
        for (f in Materials.FORCE_MIN..Materials.FORCE_MAX step 7) {
            val p = Pressure.silhouette(f)
            assertTrue("pressure must never decrease as force rises (f=$f)", p >= prev)
            assertTrue("pressure stays within 1..33", p in 1..33)
            prev = p
        }
    }
}
