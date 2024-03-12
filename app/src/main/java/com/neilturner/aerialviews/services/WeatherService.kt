package com.neilturner.aerialviews.services

import android.content.Context
import android.util.Log
import com.neilturner.aerialviews.BuildConfig
import com.neilturner.aerialviews.models.openweather.FiveDayForecast
import com.neilturner.aerialviews.utils.NetworkHelper.isInternetAvailable
import com.neilturner.aerialviews.utils.ToastHelper
import okhttp3.Cache
import okhttp3.CacheControl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.awaitResponse
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.create
import retrofit2.http.GET
import retrofit2.http.Query
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

class WeatherService(val context: Context) {

    suspend fun update() {
        val city = "Dublin, Ireland"
        val appId = BuildConfig.OPEN_WEATHER_KEY
        val units = "metric"
        val count = 10
        val lang = "EN"

        try {
            val client = OpenWeather(context).client
            val response = client.fiveDayForecast(city, appId, units, count, lang).awaitResponse()
            if (response.isSuccessful) {
                val cached = if (response.raw().networkResponse != null) "" else "cached"
                val temp = response.body()?.list?.first()?.main?.temp?.roundToInt()
                val feelsLike = response.body()?.list?.first()?.main?.temp?.roundToInt()
                ToastHelper.show(context, "Temp: ${temp}c (Feels like ${feelsLike}c) $cached")
            } else {
                ToastHelper.show(context, response.message())
            }
        } catch (ex: Exception) {
            ToastHelper.show(context, ex.message.orEmpty())
            Log.i("", ex.message.orEmpty())
        }
    }
}

class OpenWeather(private val context: Context) {

    // Create 1MB cache for HTTP responses
    private fun cache(): Cache {
        val cacheSize = 1 * 1024 * 1024 // 1 MB
        val cacheFolder = File(context.cacheDir, "http-cache")
        return Cache(cacheFolder, cacheSize.toLong())
    }

    // Cache online request, cache offline for longer
    private val offlineInterceptor = Interceptor { chain ->
        var request = chain.request()
        val cache = CacheControl.Builder()

        if (!isInternetAvailable(context)) {
            cache.onlyIfCached()
            if (BuildConfig.DEBUG) {
                cache.maxStale(5, TimeUnit.MINUTES)
            } else {
                cache.maxStale(2, TimeUnit.DAYS)
            }
        } else {
            if (BuildConfig.DEBUG) {
                cache.maxStale(1, TimeUnit.MINUTES)
            } else {
                cache.maxStale(12, TimeUnit.HOURS)
            }
        }
        request = request.newBuilder()
            .header(HEADER_CACHE_CONTROL, cache.build().toString())
            .build()
        chain.proceed(request)
    }

    private fun okHttpClient(): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor()
            .setLevel(HttpLoggingInterceptor.Level.BASIC)

        val client = OkHttpClient
            .Builder()
            .addInterceptor(offlineInterceptor)
            .cache(cache())

        if (BuildConfig.DEBUG) {
            client.addInterceptor(loggingInterceptor)
        }

        return client.build()
    }

    val client by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .client(okHttpClient())
            .build()
            .create<OpenWeatherApi>()
    }

    companion object {
        private const val BASE_URL = "https://api.openweathermap.org/"
        private const val HEADER_CACHE_CONTROL = "Cache-Control"
    }
}

interface OpenWeatherApi {
    // 5 day / 3 hour / City name
    @GET("/data/2.5/forecast")
    fun fiveDayForecast(
        @Query("q") city: String,
        @Query("appId") appId: String,
        @Query("units") units: String,
        @Query("count") count: Int,
        @Query("lang") lang: String
    ): Call<FiveDayForecast>

    // 5 day / 3 hour / Lat,Lon
    @GET("/data/2.5/forecast")
    fun fiveDayForecast(
        @Query("lat") lat: Float,
        @Query("lon") lon: Float,
        @Query("appId") appId: String,
        @Query("units") units: String,
        @Query("count") count: Int,
        @Query("lang") lang: String
    ): Call<FiveDayForecast>
}
