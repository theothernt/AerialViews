package com.neilturner.aerialviews.utils

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

object WeatherHelper {

    fun timestampToLocalTime(epochTime: Long): String {
        val formatter = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
        val date = Date(epochTime * 1000)
        return formatter.format(date)
    }

    // Given a list of timestamps, pick the nearest to the current time
    fun nearestTimestamp(timestamps: List<Long>): Long {
        val currentTime = System.currentTimeMillis().toString().substring(0, 10).toLong()
        val nearestTimestamp = timestamps.minByOrNull { abs(it - currentTime) }
        return nearestTimestamp ?: timestamps.last()
    }

    // Translate degress to human-readable directions
    fun degreesToCardinal(degrees: Int): String {
        val cardinalDirections = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
        val value = ((degrees / 45) % cardinalDirections.size)
        return cardinalDirections[value]
    }

    // Check if current locale is supported by OpenWeather API, or default to English
    fun supportedLocale(): String {
        val currentLocale = Locale.getDefault().language
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

    fun weatherCodeToDescription(code: Int): String {
        // Needs to be translated!
        val weatherCodes = mapOf(
            0 to "Clear sky",
            1 to "Mainly clear",
            2 to "Partly cloudy",
            3 to "Overcast",
            45 to "Fog",
            48 to "Depositing rime fog",
            51 to "Drizzle: Light intensity",
            53 to "Drizzle: Moderate intensity",
            55 to "Drizzle: Dense intensity",
            56 to "Freezing Drizzle: Light intensity",
            57 to "Freezing Drizzle: Dense intensity",
            61 to "Rain: Slight intensity",
            63 to "Rain: Moderate intensity",
            65 to "Rain: Heavy intensity",
            66 to "Freezing Rain: Light intensity",
            67 to "Freezing Rain: Heavy intensity",
            71 to "Snow fall: Slight intensity",
            73 to "Snow fall: Moderate intensity",
            75 to "Snow fall: Heavy intensity",
            77 to "Snow grains",
            80 to "Rain showers: Slight intensity",
            81 to "Rain showers: Moderate intensity",
            82 to "Rain showers: Violent intensity",
            85 to "Snow showers: Slight intensity",
            86 to "Snow showers: Heavy intensity",
            95 to "Thunderstorm: Slight or moderate",
            96 to "Thunderstorm with slight hail",
            99 to "Thunderstorm with heavy hail"
        )
        return weatherCodes[code].toStringOrEmpty()
    }
}
