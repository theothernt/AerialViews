package com.neilturner.aerialviews.services

import android.content.Context
import com.neilturner.aerialviews.BuildConfig
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
            isLenient = true
        }.asConverterFactory(contentType)

        val retrofit = Retrofit.Builder()
            .baseUrl("https://api.openweathermap.org/data/3.0/")
            .client(client)
            .addConverterFactory(jsonConvertor)
            .build()

        api = retrofit.create(WeatherApi::class.java)
    }

    suspend fun update(lat: Double, lon: Double) {
        Timber.i("WeatherService: update()")
        try {
            val key = BuildConfig.OPEN_WEATHER_KEY
            val response = api.getWeather(lat, lon, key)
            Timber.i("WeatherService: Weather data: $response")
            val weather = "${response.current.temp}°C, ${response.current.weather[0].description}"
            Timber.i("Weather: $weather")
        } catch (e: Exception) {
            Timber.e(e, "WeatherService: Failed to fetch weather data")
        }
    }

    interface WeatherApi {
        @GET("onecall")
        suspend fun getWeather(
            @Query("lat") lat: Double,
            @Query("lon") lon: Double,
            @Query("appid") apiKey: String,
            @Query("units") units: String = "metric",
        ): WeatherResponse
    }

    @Serializable
    data class WeatherResponse(
        val current: Current
    )

    @Serializable
    data class Current(
        val temp: Double,
        val weather: List<Weather>
    )

    @Serializable
    data class Weather(val description: String)
}
