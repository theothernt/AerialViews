package com.neilturner.aerialviews.services

import android.content.Context
import android.util.Log
import com.neilturner.aerialviews.BuildConfig
import com.neilturner.aerialviews.models.enums.TemperatureUnit
import com.neilturner.aerialviews.models.enums.WindSpeedUnit
import com.neilturner.aerialviews.models.openweather.FiveDayForecast
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.utils.NetworkHelper.isInternetAvailable
import com.neilturner.aerialviews.utils.toStringOrEmpty
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import okhttp3.Cache
import okhttp3.CacheControl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.awaitResponse
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.create
import retrofit2.http.GET
import retrofit2.http.Query
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

class WeatherService(private val context: Context, private val prefs: GeneralPrefs) {

    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    private var data: FiveDayForecast? = null
    private lateinit var fetchJob: Job

    init {
        coroutineScope.launch {
            fetchJob = async { fetchData() }
        }
    }

    private suspend fun fetchData() {
        val units = prefs.weatherUnits.toString()
        val city = prefs.weatherCity
        val appId = BuildConfig.OPEN_WEATHER_KEY
        val count = 40
        val lang = supportedLocale()

        try {
            val client = OpenWeather(context).client
            val response = client.fiveDayForecast(city, appId, units, count, lang).awaitResponse()
            if (response.isSuccessful) {
                data = response.body()
            } else {
                // Error logic
            }
        } catch (ex: Exception) {
            Log.e(TAG, ex.message.toString())
        }
    }

    fun weather(): WeatherResult? {
        if (fetchJob.isActive ||
            data == null
        ) {
            return null
        }

        // validate weather data first

        val icon = if (prefs.weatherShowIcon) {
            data?.list?.first()?.weather?.first()?.main.toStringOrEmpty()
        } else {
            ""
        }

        val city = if (prefs.weatherShowCity) {
            data?.city?.name.toStringOrEmpty()
        } else {
            ""
        }

        val tempNow = if (prefs.weatherShowTemp) {
            val temp = data?.list?.first()?.main?.temp
            temp?.roundToInt().toString()
        } else {
            ""
        }

        var windSpeed = ""
        var windDirection = ""
        if (prefs.weatherShowWind) {
            windSpeed = data?.list?.first()?.wind?.speed.toString()
            // convert to k/mh

            val degree = data?.list?.first()?.wind?.deg
            windDirection = if (degree == null) "" else degreesToCardinal(degree)
        }

        val humidity = if (prefs.weatherShowHumidity) {
            data?.list?.first()?.main?.humidity.toStringOrEmpty()
        } else {
            ""
        }

        return WeatherResult(
            icon,
            city,
            tempNow,
            prefs.weatherUnits,
            windSpeed,
            windDirection,
            prefs.weatherWindUnits,
            humidity
        )
    }

    fun degreesToCardinal(degrees: Int): String {
        val cardinalDirections = arrayOf("↑ N", "↗ NE", "→ E", "↘ SE", "↓ S", "↙ SW", "← W", "↖ NW")
        val value = ((degrees / 45) % cardinalDirections.size)
        return cardinalDirections[value]
    }

    private fun supportedLocale(): String {
        val currentLocale = Locale.getDefault().country.lowercase()
        val supportedLocales = listOf(
            "af", "al", "ar", "az", "bg", "ca", "cz", "da", "de", "el", "en", "eu", "fa", "fi",
            "fr", "gl", "he", "hi", "hr", "hu", "id", "it", "ja", "kr", "la", "lt", "mk", "no",
            "nl", "pl", "pt", "pt_br", "ro", "ru", "sv, se", "sk", "sl", "sp, es", "sr", "th",
            "tr", "ua, uk", "vi", "zh_cn", "zh_tw", "zu"
        )
        return if (supportedLocales.contains(currentLocale)) currentLocale else "en"
    }

    companion object {
        private const val TAG = "WeatherService"
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
            .addConverterFactory(MoshiConverterFactory.create())
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

data class WeatherResult(
    val icon: String,
    val city: String,
    val tempNow: String,
    val tempUnit: TemperatureUnit,
    val windSpeed: String,
    val windDirection: String,
    val windUnit: WindSpeedUnit,
    val humidity: String
)
