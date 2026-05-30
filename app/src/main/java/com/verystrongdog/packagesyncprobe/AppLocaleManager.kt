package com.verystrongdog.packagesyncprobe

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

object AppLocaleManager {
    private const val PREFS_NAME = "probe_store"
    private const val KEY_LANGUAGE_TAG = "language_tag"
    private const val ENGLISH_TAG = "en"
    private const val CHINESE_TAG = "zh-Hans"

    fun applySavedLocale(context: Context) {
        val savedTag = prefs(context).getString(KEY_LANGUAGE_TAG, null) ?: return
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(savedTag))
    }

    fun toggleLocale(context: Context) {
        val nextTag = if (isChinese(context)) ENGLISH_TAG else CHINESE_TAG
        prefs(context).edit().putString(KEY_LANGUAGE_TAG, nextTag).apply()
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(nextTag))
    }

    fun isChinese(context: Context): Boolean {
        val appLocales = AppCompatDelegate.getApplicationLocales().toLanguageTags()
        val activeTag = if (appLocales.isNotBlank()) {
            appLocales
        } else {
            context.resources.configuration.locales[0]?.toLanguageTag().orEmpty()
        }
        return activeTag.startsWith("zh")
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
