package de.knutwurst.knutcut.data

import org.junit.Assert.assertEquals
import org.junit.Test

class DisplayUnitTest {

    @Test
    fun mmIsIdentity() {
        assertEquals(40.0, DisplayUnit.MM.fromMm(40.0), 1e-9)
        assertEquals(40.0, DisplayUnit.MM.toMm(40.0), 1e-9)
    }

    @Test
    fun cmConverts() {
        assertEquals(4.0, DisplayUnit.CM.fromMm(40.0), 1e-9)
        assertEquals(40.0, DisplayUnit.CM.toMm(4.0), 1e-9)
    }

    @Test
    fun inchConverts() {
        assertEquals(1.0, DisplayUnit.INCH.fromMm(25.4), 1e-9)
        assertEquals(25.4, DisplayUnit.INCH.toMm(1.0), 1e-9)
    }
}
