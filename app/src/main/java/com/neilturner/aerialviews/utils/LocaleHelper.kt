@file:Suppress("unused")

package com.neilturner.aerialviews.utils

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.annotation.RawRes
import androidx.core.os.ConfigurationCompat
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import java.io.InputStream
import java.text.Bidi
import java.util.Locale

object LocaleHelper {
    // https://stackoverflow.com/a/44145807/247257
    @SuppressLint("AppBundleLocaleChanges")
    fun localeString(
        requestedLocale: Locale?,
        resourceId: Int,
        context: Context,
    ): String {
        val config = Configuration(context.resources.configuration)
        config.setLocale(requestedLocale)
        return context.createConfigurationContext(config).getText(resourceId).toString()
    }

    fun localeResource(
        requestedLocale: Locale?,
        @RawRes res: Int,
        context: Context,
    ): InputStream {
        val config = Configuration(context.resources.configuration)
        config.setLocale(requestedLocale)
        return context.createConfigurationContext(config).resources.openRawResource(res)
    }

    private fun localeFromString(locale: String): Locale {
        val parts = locale.split("-")
        if (parts.size == 1) {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
                Locale.of(parts[0])
            } else {
                @Suppress("DEPRECATION")
                Locale(parts[0])
            }
        }
        if (parts.size == 2) {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
                Locale.of(parts[0], parts[1])
            } else {
                @Suppress("DEPRECATION")
                Locale(parts[0], parts[1])
            }
        }
        if (parts.size == 3) {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
                Locale.of(parts[0], parts[1], parts[2])
            } else {
                @Suppress("DEPRECATION")
                Locale(parts[0], parts[1], parts[2])
            }
        }
        return Locale.ENGLISH
    }

    fun isLtrText(text: String): Boolean = Bidi(text, Bidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT).isLeftToRight

    fun alternateLocale(
        context: Context,
        locale: String,
    ): Context {
        val altLocale =
            if (locale.startsWith("random")) {
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
        locales.removeAt(0)
        locales.removeAt(locales.lastIndex)

        return locales.random()
    }

    fun systemLanguageAndLocale(context: Context): Pair<String, String> {
        // Get display language (what language the UI is shown in)
        val displayLocale = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            // API 24+ - Use ConfigurationCompat for better multi-locale support
            ConfigurationCompat.getLocales(context.resources.configuration)[0]
        } else {
            // API 22-23 - Use deprecated but functional approach
            @Suppress("DEPRECATION")
            context.resources.configuration.locale
        }

        // Get system default locale (regional formatting settings)
        val systemLocale = Locale.getDefault()

        // Format as language-country codes
        val displayLanguage = "${displayLocale?.language}-${displayLocale?.country}"
        val systemRegion = "${systemLocale.language}-${systemLocale.country}"

        return Pair(displayLanguage, systemRegion)
    }

    fun detailedSystemLanguageAndLocale(context: Context): Triple<String, String, Map<String, String?>> {
        val displayLocale = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            ConfigurationCompat.getLocales(context.resources.configuration)[0]
        } else {
            @Suppress("DEPRECATION")
            context.resources.configuration.locale
        }

        val systemLocale = Locale.getDefault()

        val displayLanguage = "${displayLocale?.language}-${displayLocale?.country}"
        val systemRegion = "${systemLocale.language}-${systemLocale.country}"

        val additionalInfo = mapOf(
            "displayLanguageName" to displayLocale?.displayLanguage,
            "displayCountryName" to displayLocale?.displayCountry,
            "systemLanguageName" to systemLocale.displayLanguage,
            "systemCountryName" to systemLocale.displayCountry,
            "apiLevel" to Build.VERSION.SDK_INT.toString(),
            "timestamp" to System.currentTimeMillis().toString()
        )

        return Triple(displayLanguage, systemRegion, additionalInfo)
    }
}
