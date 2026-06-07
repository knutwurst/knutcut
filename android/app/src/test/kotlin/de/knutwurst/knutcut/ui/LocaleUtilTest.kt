package de.knutwurst.knutcut.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LocaleUtilTest {

    @Test
    fun systemReturnsTheSameContextUntouched() {
        val base = RuntimeEnvironment.getApplication()
        assertSame(base, LocaleUtil.wrap(base, "system"))
    }

    @Test
    fun wrapAppliesTheChosenLanguageToResources() {
        val base = RuntimeEnvironment.getApplication()
        assertEquals("de", LocaleUtil.wrap(base, "de").resources.configuration.locales[0].language)
        assertEquals("en", LocaleUtil.wrap(base, "en").resources.configuration.locales[0].language)
    }

    @Test
    fun applyDefaultSetsForcedLocaleAndSystemRestoresIt() {
        val saved = Locale.getDefault()
        try {
            // Capture whatever "system" maps to, then force German and switch back to system.
            LocaleUtil.applyDefault("system")
            val systemLocale = Locale.getDefault()

            LocaleUtil.applyDefault("de")
            assertEquals("de", Locale.getDefault().language)

            LocaleUtil.applyDefault("system")
            assertEquals("picking System must restore the system locale, not keep German",
                systemLocale, Locale.getDefault())
        } finally {
            Locale.setDefault(saved)
        }
    }
}
