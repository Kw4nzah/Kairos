package com.kairos.app.system

import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.kairos.app.data.AppLanguage
import java.util.Locale

object Locales {
    /** Applies the language to the UI live (per-app locale API; the Activity is recreated). */
    fun apply(lang: AppLanguage) {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(lang.tag))
    }

    /** The language currently set through the per-app locale API (also reflects system settings changes). */
    fun current(): AppLanguage = AppLanguage.fromTags(AppCompatDelegate.getApplicationLocales().toLanguageTags())

    /**
     * Context whose resources use the chosen language. Needed outside the Activity (alarm receivers,
     * calendar sync), where the per-app locale is not guaranteed to be applied on every API level.
     */
    fun localized(ctx: Context, lang: AppLanguage): Context {
        if (lang == AppLanguage.SYSTEM) return ctx
        val cfg = Configuration(ctx.resources.configuration)
        cfg.setLocale(Locale.forLanguageTag(lang.tag))
        return ctx.createConfigurationContext(cfg)
    }
}
