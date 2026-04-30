package com.rethinkingstudio.clawlink.core.state

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

enum class LanguagePreference(val tag: String, val displayName: String) {
    SYSTEM("system", "System"),
    ZH_HANS("zh-Hans", "简体中文"),
    EN("en", "English");

    companion object {
        fun fromTag(tag: String?): LanguagePreference {
            return entries.find { it.tag == tag } ?: SYSTEM
        }
    }
}

object LanguageManager {
    fun setLanguage(preference: LanguagePreference) {
        val appLocale: LocaleListCompat = if (preference == LanguagePreference.SYSTEM) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(preference.tag)
        }
        AppCompatDelegate.setApplicationLocales(appLocale)
    }

    fun getCurrentPreference(): LanguagePreference {
        val locales = AppCompatDelegate.getApplicationLocales()
        if (locales.isEmpty) return LanguagePreference.SYSTEM
        val tag = locales.toLanguageTags()
        return if (tag.startsWith("zh")) LanguagePreference.ZH_HANS else LanguagePreference.EN
    }
}
