package com.neilturner.aerialviews.utils

import java.util.Locale
import kotlin.math.abs

object WeatherHelper {

    // Given a list of timestamps, pick the nearest to the current time
    fun nearestTimestamp(timestamps: List<Long>): Long {
        val currentTime = System.currentTimeMillis().toString().substring(0, 10).toLong()
        val nearestTimestamp = timestamps.minByOrNull { abs(it - currentTime) }
        return nearestTimestamp ?: timestamps.first()
    }

    // Translate degress to human-readable directions
    fun degreesToCardinal(degrees: Int): String {
        val cardinalDirections = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
        val value = ((degrees / 45) % cardinalDirections.size)
        return cardinalDirections[value]
    }

    // Check if current locale is supported by OpenWeather API, or default to English
    fun supportedLocale(): String {
        val currentLocale = Locale.getDefault().country.lowercase()
        val supportedLocales = listOf(
            "af", "al", "ar", "az", "bg", "ca", "cz", "da", "de", "el", "en", "eu", "fa", "fi",
            "fr", "gl", "he", "hi", "hr", "hu", "id", "it", "ja", "kr", "la", "lt", "mk", "no",
            "nl", "pl", "pt", "pt_br", "ro", "ru", "sv, se", "sk", "sl", "sp, es", "sr", "th",
            "tr", "ua, uk", "vi", "zh_cn", "zh_tw", "zu"
        )
        return if (supportedLocales.contains(currentLocale)) currentLocale else "en"
    }

    // Convert meters per second (m/s) to kilometers per hour (km/h)
    fun convertMeterToKilometer(speedInMps: Double): String {
        val conversionFactor = 3600.0 / 1000.0
        return (Math.round(speedInMps * conversionFactor * 10.0) / 10.0).toString()
    }
}
