package de.knutwurst.knutcut.ui

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/** Applies the user's language choice ("system"/"de"/"en") by wrapping a context with the locale. */
object LocaleUtil {
    // The real system locale, captured at process start (before any override is applied), so picking
    // "system" can restore it instead of leaving a previously chosen locale as the default.
    private val systemLocale: Locale = Locale.getDefault()

    private fun localeFor(lang: String): Locale = when (lang) {
        "de", "en" -> Locale(lang)
        else -> systemLocale
    }

    /** A context whose resources resolve in the chosen language. No global state is touched. */
    fun wrap(base: Context, lang: String): Context {
        if (lang != "de" && lang != "en") return base
        val cfg = Configuration(base.resources.configuration)
        cfg.setLocale(localeFor(lang))
        return base.createConfigurationContext(cfg)
    }

    /** Set the JVM default locale to match the choice (call once per activity create, on the UI thread). */
    fun applyDefault(lang: String) { Locale.setDefault(localeFor(lang)) }
}
