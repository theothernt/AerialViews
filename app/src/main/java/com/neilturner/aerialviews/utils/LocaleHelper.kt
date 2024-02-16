@file:Suppress("unused")

package com.neilturner.aerialviews.utils

import android.content.Context
import android.content.res.Configuration
import androidx.annotation.RawRes
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import java.io.InputStream
import java.text.Bidi
import java.util.Locale

object LocaleHelper {

    // https://stackoverflow.com/a/44145807/247257
    fun localeString(
        requestedLocale: Locale?,
        resourceId: Int,
        context: Context
    ): String {
        val config = Configuration(context.resources.configuration)
        config.setLocale(requestedLocale)
        return context.createConfigurationContext(config).getText(resourceId).toString()
    }

    fun localeResource(
        requestedLocale: Locale?,
        @RawRes res: Int,
        context: Context
    ): InputStream {
        val config = Configuration(context.resources.configuration)
        config.setLocale(requestedLocale)
        return context.createConfigurationContext(config).resources.openRawResource(res)
    }

    private fun localeFromString(locale: String): Locale {
        val parts = locale.split("-")
        if (parts.size == 1) {
            return Locale(parts[0])
        }
        if (parts.size == 2) {
            return Locale(parts[0], parts[1])
        }
        if (parts.size == 3) {
            return Locale(parts[0], parts[1], parts[2])
        }
        return Locale.ENGLISH
    }

    fun isLtrText(text: String): Boolean {
        return Bidi(text, Bidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT).isLeftToRight
    }

    fun alternateLocale(context: Context, locale: String): Context {
        val altLocale = if (locale.startsWith("random")) {
            localeFromString(randomLocale(context))
        } else {
            localeFromString(locale)
        }

        if (GeneralPrefs.clockForceLatinDigits) {
            Locale.setDefault(Locale.UK)
        } else {
            Locale.setDefault(altLocale)
        }

        val config = Configuration(context.resources.configuration)
        config.setLocale(altLocale)
        return context.createConfigurationContext(config)
    }

    private fun randomLocale(context: Context): String {
        val res = context.resources
        val locales = res.getStringArray(R.array.locale_screensaver_values).toMutableList()

        // Remove 'default' and 'random' values leaving only valid locales
        locales.removeFirst()
        locales.removeLast()

        return locales.random()
    }
}
