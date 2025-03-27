package com.neilturner.aerialviews.services.weather

import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Query

interface ForecastApi {
    @GET("data/3.0/onecall")
    suspend fun getForecast(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("appid") apiKey: String,
        @Query("units") units: String = "metric",
    ): ForecastResponse
}

@Serializable
data class ForecastResponse(
    val current: Current,
)

@Serializable
data class Current(
    val dt: Long,
    val temp: Double,
    val weather: List<Weather>,
)

@Serializable
data class Weather(
    val description: String,
)
