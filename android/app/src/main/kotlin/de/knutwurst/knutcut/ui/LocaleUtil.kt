package de.knutwurst.knutcut.ui

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/** Applies the user's language choice ("system"/"de"/"en") by wrapping a context with the locale. */
object LocaleUtil {
    fun wrap(base: Context, lang: String): Context {
        if (lang == "system") return base
        val locale = Locale(lang)
        Locale.setDefault(locale)
        val cfg = Configuration(base.resources.configuration)
        cfg.setLocale(locale)
        return base.createConfigurationContext(cfg)
    }
}
