package com.neilturner.aerialviews.models.openweather

import com.neilturner.aerialviews.models.enums.TemperatureUnit
import com.neilturner.aerialviews.models.enums.WindSpeedUnit

data class WeatherResult(
    val icon: String = "",
    val city: String = "",
    val tempNow: String = "",
    val tempUnit: TemperatureUnit = TemperatureUnit.METRIC,
    val windSpeed: String = "",
    val windDirection: String = "",
    val windUnit: WindSpeedUnit = WindSpeedUnit.KMH,
    val humidity: String = ""
)
