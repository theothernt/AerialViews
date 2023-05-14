package com.neilturner.aerialviews.utils

import android.content.Context
import android.content.res.Configuration
import androidx.annotation.RawRes
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

    fun localeFromString(locale: String): Locale {
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
}
