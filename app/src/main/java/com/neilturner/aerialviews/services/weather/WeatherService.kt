package com.neilturner.aerialviews.services.weather

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import com.neilturner.aerialviews.BuildConfig
import com.neilturner.aerialviews.utils.capitalise
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import me.kosert.flowbus.GlobalBus
import okhttp3.Cache
import okhttp3.CacheControl
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
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.seconds

class WeatherService(
    val context: Context,
) {
    private lateinit var weather: WeatherApi
    private val cacheSize = 1 * 1024 * 1024 // 1MB
    private val contentType = "application/json".toMediaType()

    private var updateJob: Job? = null

    val json by lazy {
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }
    }

    private val jsonConvertor by lazy { json.asConverterFactory(contentType) }

    private val cache by lazy { Cache(File(context.cacheDir, "weather_cache"), cacheSize.toLong()) }

    private val logging by lazy {
        HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.NONE
        }
    }

    val cacheStatusInterceptor by lazy {
        object : Interceptor {
            override fun intercept(chain: Interceptor.Chain): Response {
                val response = chain.proceed(chain.request())
                val isFromCache = response.cacheResponse != null
                Timber.Forest.i("Cache status: ${if (isFromCache) "from cache" else "from network"}")
                return response
            }
        }
    }

    val client by lazy {
        OkHttpClient
            .Builder()
            .cache(cache)
            .addInterceptor(logging)
            .addInterceptor(cacheStatusInterceptor)
            .addInterceptor(offlineCacheInterceptor(context))
            .addNetworkInterceptor(onlineCacheInterceptor())
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    val retrofit: Retrofit by lazy {
        Retrofit
            .Builder()
            .baseUrl("https://api.openweathermap.org/data/3.0/")
            .client(client)
            .addConverterFactory(jsonConvertor)
            .build()
    }

    init {
        // Nothing
    }

    fun startUpdates() {
        weather = retrofit.create(WeatherApi::class.java)
        updateJob =
            CoroutineScope(Dispatchers.IO).launch {
                while (isActive) {
                    val forecast = forecastUpdate()
                    GlobalBus.post(WeatherEvent(forecast))
                    delay(30.seconds)
                }
            }
    }

    private suspend fun forecastUpdate(): String =
        try {
            val key = BuildConfig.OPEN_WEATHER_KEY
            val lat = 53.35
            val lon = -6.26
            val response = weather.getWeather(lat, lon, key)
            val timeAgo = getTimeAgo(response.current.dt)
            val description =
                response.current.weather[0]
                    .description
                    .capitalise()
            val temperature = "${response.current.temp.roundToInt()}°C"
            val forecast = "$temperature, $description"
            Timber.Forest.i("Forecast: $forecast ($timeAgo)")
            forecast
        } catch (e: Exception) {
            Timber.Forest.e(e, "Failed to fetch and parse weather data")
            ""
        }

    fun stop() {
        updateJob?.cancel()
        updateJob = null
    }

    @Suppress("DEPRECATION")
    private fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } else {
            val networkInfo = connectivityManager.activeNetworkInfo
            return networkInfo != null && networkInfo.isConnected
        }
    }

    fun offlineCacheInterceptor(context: Context) =
        Interceptor { chain ->
            var request = chain.request()
            if (!isNetworkAvailable(context)) {
                Timber.Forest.i("Using offline cache...")
                request =
                    request
                        .newBuilder()
                        .header("Cache-Control", "public, only-if-cached, max-stale=86400") // 1 Day
                        .build()
            }
            chain.proceed(request)
        }

    fun onlineCacheInterceptor() =
        Interceptor { chain ->
            val cacheControlHeader = "Cache-Control"
            val cacheControlNoCache = "no-cache"

            val request = chain.request()
            val originalResponse = chain.proceed(request)

            val shouldUseCache = request.header(cacheControlHeader) != cacheControlNoCache
            if (!shouldUseCache) return@Interceptor originalResponse

            val cacheControl =
                CacheControl
                    .Builder()
                    .maxAge(5, TimeUnit.MINUTES)
                    .build()

            return@Interceptor originalResponse
                .newBuilder()
                .header(cacheControlHeader, cacheControl.toString())
                .build()
        }

    fun getTimeAgo(unixTimestamp: Long): String {
        val currentTimeMillis = System.currentTimeMillis()
        val timestampMillis = unixTimestamp * 1000

        val diffMillis = abs(currentTimeMillis - timestampMillis)

        // Convert to appropriate time units
        val seconds = TimeUnit.MILLISECONDS.toSeconds(diffMillis)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(diffMillis)
        val hours = TimeUnit.MILLISECONDS.toHours(diffMillis)
        val days = TimeUnit.MILLISECONDS.toDays(diffMillis)

        return when {
            seconds < 60 -> "$seconds seconds ago"
            minutes < 60 -> "$minutes minutes ago"
            hours < 24 -> "$hours hours ago"
            days < 30 -> "$days days ago"
            days < 365 -> "${days / 30} months ago"
            else -> "${days / 365} years ago"
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
}

data class WeatherEvent(
    val forecast: String = "",
)
