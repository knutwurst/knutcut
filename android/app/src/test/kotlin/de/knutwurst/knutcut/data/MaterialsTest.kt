package de.knutwurst.knutcut.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class MaterialsTest {

    private inline fun withLocale(lang: String, block: () -> Unit) {
        val prev = Locale.getDefault()
        Locale.setDefault(Locale(lang))
        try { block() } finally { Locale.setDefault(prev) }
    }

    @Test
    fun displayUsesGermanNameOnlyInGerman() {
        val preset = Material("id", "A4 printing paper", 180, "A4-Druckpapier")
        withLocale("de") { assertEquals("A4-Druckpapier", preset.display()) }
        withLocale("en") { assertEquals("A4 printing paper", preset.display()) }
    }

    @Test
    fun customMaterialAlwaysShowsItsRawName() {
        val custom = Material("custom-1", "Eigenes", 120)
        withLocale("de") { assertEquals("Eigenes", custom.display()) }
        withLocale("en") { assertEquals("Eigenes", custom.display()) }
    }

    @Test
    fun everyPresetHasAGermanName() {
        assertTrue(Materials.presets.all { it.nameDe != null })
    }
}
