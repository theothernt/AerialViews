package com.neilturner.aerialviews.services.weather

import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Query

interface OpenWeatherApi {
//    @GET("data/3.0/onecall")
//    suspend fun getForecast(
//        @Query("lat") lat: Double,
//        @Query("lon") lon: Double,
//        @Query("appid") apiKey: String,
//        @Query("units") units: String = "metric",
//    ): OneCallResponse

    @GET("data/2.5/weather")
    suspend fun getCurrentWeather(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("appid") apiKey: String,
        @Query("units") units: String = "metric",
        @Query("lang") language: String = "en",
    ): CurrentWeatherResponse

    @GET("data/2.5/forecast")
    suspend fun getFiveDayForecast(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("appid") apiKey: String,
        @Query("units") units: String = "metric",
        @Query("lang") language: String = "en",
    ): FiveDayForecastResponse

    @GET("geo/1.0/direct")
    suspend fun getLocationByName(
        @Query("q") locationName: String,
        @Query("limit") limit: Int = 10,
        @Query("appid") apiKey: String,
        @Query("lang") language: String = "en",
    ): List<LocationResponse>
}

// Location
@Serializable
data class LocationResponse(
    val name: String,
    val lat: Double,
    val lon: Double,
    val country: String,
    val state: String? = null,
) {
    fun getDisplayName(): String =
        if (state != null) {
            "$name, $state, $country"
        } else {
            "$name, $country"
        }
}

// Current Weather
@Serializable
data class CurrentWeatherResponse(
    val weather: List<Weather>,
    val main: MainWeatherData,
    val dt: Long,
    val name: String,
    val sys: SysInfo,
    val wind: Wind,
)

@Serializable
data class MainWeatherData(
    val temp: Double,
    val feels_like: Double,
    val temp_min: Double,
    val temp_max: Double,
    val pressure: Int,
    val humidity: Int,
)

@Serializable
data class Weather(
    val id: Int,
    val main: String,
    val description: String,
    val icon: String,
)

@Serializable
data class SysInfo(
    val country: String,
    val sunrise: Long,
    val sunset: Long,
)

@Serializable
data class Wind(
    val speed: Double,
    val deg: Int,
    // val gust: Double,
)

// 5 Day Forecast
@Serializable
data class FiveDayForecastResponse(
    val list: List<ForecastItem>,
    val city: City,
)

@Serializable
data class ForecastItem(
    val dt: Long,
    val main: MainWeatherData,
    val weather: List<Weather>,
    val dt_txt: String,
)

@Serializable
data class City(
    val name: String,
    val country: String,
)
