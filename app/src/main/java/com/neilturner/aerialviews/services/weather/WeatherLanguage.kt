package com.neilturner.aerialviews.services.weather

import android.content.Context
import android.os.Build
import timber.log.Timber
import java.util.Locale

object WeatherLanguage {
    // List of language codes supported by OpenWeather API
    // https://openweathermap.org/current#multi
    private val supportedLanguages = setOf(
        "af", // Afrikaans
        "al", // Albanian
        "ar", // Arabic
        "az", // Azerbaijani
        "bg", // Bulgarian
        "ca", // Catalan
        "cz", // Czech
        "da", // Danish
        "de", // German
        "el", // Greek
        "en", // English
        "eu", // Basque
        "fa", // Persian (Farsi)
        "fi", // Finnish
        "fr", // French
        "gl", // Galician
        "he", // Hebrew
        "hi", // Hindi
        "hr", // Croatian
        "hu", // Hungarian
        "id", // Indonesian
        "it", // Italian
        "ja", // Japanese
        "kr", // Korean
        "la", // Latvian
        "lt", // Lithuanian
        "mk", // Macedonian
        "no", // Norwegian
        "nl", // Dutch
        "pl", // Polish
        "pt", // Portuguese
        "pt_br", // Português Brasil
        "ro", // Romanian
        "ru", // Russian
        "sv", // Swedish
        "sk", // Slovak
        "sl", // Slovenian
        "sp", // Spanish
        "es", // Spanish (alternative)
        "sr", // Serbian
        "th", // Thai
        "tr", // Turkish
        "ua", // Ukrainian
        "uk", // Ukrainian (alternative)
        "vi", // Vietnamese
        "zh_cn", // Chinese Simplified
        "zh_tw", // Chinese Traditional
        "zu", // Zulu
    )

     // Gets the appropriate language code for the OpenWeather API based on device locale
     // Returns English (en) if the device language is not supported
    fun getLanguageCode(context: Context): String {
        val deviceLocale = getDeviceLocale(context)
        val langCode = deviceLocale.language.lowercase()
        val countryCode = deviceLocale.country.lowercase()
        
        // Check for special cases with country-specific variants
        when {
            langCode == "zh" && countryCode == "cn" -> return "zh_cn"
            langCode == "zh" && countryCode == "tw" -> return "zh_tw"
            langCode == "pt" && countryCode == "br" -> return "pt_br"
            langCode == "iw" -> return "he" // Use he instead of iw
            langCode == "es" -> return "es" // Use es instead of sp
            langCode == "uk" -> return "uk" // Use uk instead of ua
            langCode == "es" && supportedLanguages.contains("sp") -> return "sp"
            langCode == "uk" && supportedLanguages.contains("ua") -> return "ua"
        }

        // Return the language code if supported, or English as fallback
         Timber.i("Weather: device language: $langCode, Country: $countryCode")
        return if (supportedLanguages.contains(langCode)) langCode else "en"
    }

    // Get the current device locale
    private fun getDeviceLocale(context: Context): Locale {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            context.resources.configuration.locales.get(0)
        } else {
            @Suppress("DEPRECATION")
            context.resources.configuration.locale
        }
    }
}
