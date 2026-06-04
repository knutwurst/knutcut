package de.knutwurst.knutcut.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MaterialsTest {

    @Test
    fun displayPrefersGermanNameThenFallsBack() {
        assertEquals("A4-Druckpapier", Material("id", "A4 printing paper", 180, "A4-Druckpapier").display())
        assertEquals("Eigenes", Material("custom-1", "Eigenes", 120).display())
    }

    @Test
    fun everyPresetHasAGermanName() {
        assertTrue(Materials.presets.all { it.nameDe != null })
    }
}
