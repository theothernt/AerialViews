package com.neilturner.aerialviews.services

import android.content.Context
import com.neilturner.aerialviews.BuildConfig
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Cache
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import timber.log.Timber
import java.io.File
import java.util.concurrent.TimeUnit

class WeatherService(val context: Context) {
    private val api: WeatherApi

    init {
        //Timber.i("WeatherService: init()")

        val cacheSize = 10 * 1024 * 1024 // 10 MB
        val cache = Cache(File(context.cacheDir, "http_cache"), cacheSize.toLong())

        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

        val cacheStatusInterceptor = object : Interceptor {
            override fun intercept(chain: Interceptor.Chain): Response {
                val response = chain.proceed(chain.request())
                val isFromCache = response.cacheResponse != null
                Timber.i("Cache status: ${if (isFromCache) "from cache" else "from network"}")
                return response
            }
        }

        val client = OkHttpClient.Builder()
            .cache(cache)
            .addInterceptor(logging)
            .addInterceptor(cacheStatusInterceptor)
            //.addInterceptor(offlineCacheInterceptor(context))
            .addNetworkInterceptor(onlineCacheInterceptor())
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
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

    fun offlineCacheInterceptor(context: Context) = Interceptor { chain ->
        var request = chain.request()
        //if (!isNetworkAvailable(context)) {
        if (false) { // TODO: Replace with actual network check
            request = request.newBuilder()
                .header("Cache-Control", "public, only-if-cached, max-stale=604800") // 7 days cache
                .build()
        }
        chain.proceed(request)
    }

    fun onlineCacheInterceptor() = Interceptor { chain ->
        val response = chain.proceed(chain.request())
        response.newBuilder()
            .header("Cache-Control", "public, max-age=60") // Cache data for 60 seconds
            .build()
    }

    suspend fun update(lat: Double, lon: Double) {
        //Timber.i("WeatherService: update()")
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
