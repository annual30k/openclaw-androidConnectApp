package com.rethinkingstudio.clawlink.core.state

import java.util.Locale

object LocalizedText {
    fun choose(en: String, zhHans: String): String {
        return if (isChinese()) zhHans else en
    }

    fun isChinese(): Boolean {
        val appLocales = androidx.appcompat.app.AppCompatDelegate.getApplicationLocales()
        val language = if (appLocales.isEmpty) {
            Locale.getDefault().language
        } else {
            appLocales[0]?.language
        }
        return language.equals(Locale.CHINESE.language, ignoreCase = true)
    }
}
