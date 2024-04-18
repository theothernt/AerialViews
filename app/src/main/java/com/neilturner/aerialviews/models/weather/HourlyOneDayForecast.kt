package com.neilturner.aerialviews.models.weather

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class HourlyOneDayForecast(
    @Json(name = "elevation")
    val elevation: Double,
    @Json(name = "hourly")
    val hourly: Hourly,
    @Json(name = "hourly_units")
    val hourlyUnits: HourlyUnits,
    @Json(name = "latitude")
    val latitude: Double,
    @Json(name = "longitude")
    val longitude: Double,
    @Json(name = "timezone")
    val timezone: String,
    @Json(name = "timezone_abbreviation")
    val timezoneAbbreviation: String,
    @Json(name = "utc_offset_seconds")
    val utcOffsetSeconds: Int
) {
    @JsonClass(generateAdapter = true)
    data class Hourly(
        @Json(name = "apparent_temperature")
        val apparentTemperature: List<Double>,
        @Json(name = "temperature_2m")
        val temperature2m: List<Double>,
        @Json(name = "time")
        val time: List<String>,
        @Json(name = "weather_code")
        val weatherCode: List<Int>,
        @Json(name = "wind_speed_10m")
        val windSpeed10m: List<Double>,
        @Json(name = "wind_direction_10m")
        val windDirection10m: List<Int>
    )

    @JsonClass(generateAdapter = true)
    data class HourlyUnits(
        @Json(name = "apparent_temperature")
        val apparentTemperature: String,
        @Json(name = "temperature_2m")
        val temperature2m: String,
        @Json(name = "time")
        val time: String,
        @Json(name = "weather_code")
        val weatherCode: String,
        @Json(name = "wind_speed_10m")
        val windSpeed10m: String,
        @Json(name = "wind_direction_10m")
        val windDirection10m: String
    )
}
