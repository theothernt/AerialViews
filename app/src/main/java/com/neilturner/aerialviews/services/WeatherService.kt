package com.neilturner.aerialviews.services

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Cache
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import timber.log.Timber
import java.io.File
import java.util.concurrent.TimeUnit

class WeatherService(private val context: Context) {
    private val api: WeatherApi

    init {
        Timber.i("WeatherService: init()")

        val cacheSize = 10 * 1024 * 1024 // 10 MB
        val cache = Cache(File(context.cacheDir, "http_cache"), cacheSize.toLong())

        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

        val client = OkHttpClient.Builder()
            .cache(cache)
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        val contentType = "application/json".toMediaType()
        val jsonConvertor = Json {
            ignoreUnknownKeys = true
        }.asConverterFactory(contentType)

        val retrofit = Retrofit.Builder()
            .baseUrl("https://api.openweathermap.org/data/2.5/")
            .client(client)
            .addConverterFactory(jsonConvertor)
            .build()

        api = retrofit.create(WeatherApi::class.java)
    }

    suspend fun update(lat: Double, lon: Double, apiKey: String) {
        Timber.i("WeatherService: update()")
        try {
            val response = api.getWeather(lat, lon, apiKey)
            Timber.i("WeatherService: Weather data: $response")
        } catch (e: Exception) {
            Timber.e(e, "WeatherService: Failed to fetch weather data")
        }
    }

    interface WeatherApi {
        @GET("weather")
        suspend fun getWeather(
            @Query("lat") lat: Double,
            @Query("lon") lon: Double,
            @Query("appid") apiKey: String
        ): WeatherResponse
    }

    @Serializable
    data class WeatherResponse(
        val weather: List<Weather>,
        val main: Main,
        val wind: Wind,
        val sys: Sys,
        val name: String
    )

    @Serializable
    data class Weather(val description: String)

    @Serializable
    data class Main(val temp: Double, val pressure: Int, val humidity: Int)

    @Serializable
    data class Wind(val speed: Double)

    @Serializable
    data class Sys(val country: String)
}
