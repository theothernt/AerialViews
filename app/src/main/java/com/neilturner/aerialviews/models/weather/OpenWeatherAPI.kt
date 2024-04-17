package com.neilturner.aerialviews.models.weather

import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query

interface OpenWeatherAPI {

    // 5 day / 3 hour / City name
    @GET("/data/2.5/forecast")
    fun threeHourFiveDayForecast(
        @Query("q") city: String,
        @Query("appId") appId: String,
        @Query("units") units: String,
        @Query("cnt") count: Int,
        @Query("lang") lang: String
    ): Call<ThreeHourFiveDayForecast>

    // 4 day / 1 hour / City name
    @GET("/data/2.5/forecast")
    fun hourlyFourDayForecast(
        @Query("q") city: String,
        @Query("appId") appId: String,
        @Query("units") units: String,
        @Query("cnt") count: Int,
        @Query("lang") lang: String
    ): Call<ThreeHourFiveDayForecast>
}
