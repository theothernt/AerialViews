package com.neilturner.aerialviews.models.weather

import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query

interface OpenMeteoAPI {

    // 1 day / 1 hour / Lat, Lng
    // latitude=53.2905&longitude=-6.1949&hourly=temperature_2m,apparent_temperature,precipitation_probability,weather_code,wind_speed_10m&temperature_unit=fahrenheit&wind_speed_unit=mph&timeformat=unixtime&timezone=auto&forecast_days=1
    @GET("/v1/forecast")
    fun hourlyOneDayForecast(
        @Query("latitude") latitude: String,
        @Query("longitude") longitude: String,
        @Query("timezone") timezone: String = "auto", // TimeZone.getDefault()
        @Query("timeformat") timeformat: String = "unixtime",
        // temperature_unit=fahrenheit
        // wind_speed_unit=mph
        @Query("hourly") hourly: String = "temperature_2m,apparent_temperature,weather_code,wind_speed_10m",
        @Query("forecast_days") forecastDays: Int = 1
    ): Call<HourlyOneDayForecast>
}
