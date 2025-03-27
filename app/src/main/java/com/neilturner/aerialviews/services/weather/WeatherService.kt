package com.neilturner.aerialviews.services.weather

import android.content.Context
import com.neilturner.aerialviews.BuildConfig
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.services.weather.NetworkHelpers.buildOkHttpClient
import com.neilturner.aerialviews.services.weather.NetworkHelpers.buildSerializer
import com.neilturner.aerialviews.utils.TimeHelper.calculateTimeAgo
import com.neilturner.aerialviews.utils.capitalise
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import me.kosert.flowbus.GlobalBus
import retrofit2.Retrofit
import timber.log.Timber
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.seconds

class WeatherService(
    val context: Context,
) {
    private var updateJob: Job? = null

    private val forecast by lazy {
        openWeather.create(ForecastApi::class.java)
    }

    private val location by lazy {
        openWeather.create(LocationApi::class.java)
    }

    private val openWeather: Retrofit by lazy {
        Retrofit
            .Builder()
            .baseUrl("https://api.openweathermap.org/")
            .client(buildOkHttpClient(context))
            .addConverterFactory(buildSerializer())
            .build()
    }

    suspend fun lookupLocation(query: String): List<LocationResponse> =
        try {
            val key = BuildConfig.OPEN_WEATHER_KEY
            val locations = location.getLocationByName(query, 10, key)
            delay(1.seconds)
            locations
        } catch (e: Exception) {
            Timber.e(e, "Failed to fetch location data")
            emptyList()
        }

    fun startUpdates() {
        updateJob?.cancel()
        updateJob =
            CoroutineScope(Dispatchers.IO).launch {
                while (isActive) {
                    val forecast = forecastUpdate()
                    GlobalBus.post(WeatherEvent(forecast))
                    delay(30.seconds)
                }
            }
    }

    private suspend fun forecastUpdate(): String {
        return try {
            val key = BuildConfig.OPEN_WEATHER_KEY
            val lat = GeneralPrefs.weatherLocationLat.toDoubleOrNull()
            val lon = GeneralPrefs.weatherLocationLon.toDoubleOrNull()
            val units = if (GeneralPrefs.weatherUnits == null) "metric" else GeneralPrefs.weatherUnits.toString().lowercase()

            if (key.isEmpty() || lat == null || lon == null) {
                Timber.Forest.e("Invalid location coordinates")
                return ""
            }

            val response = forecast.getForecast(lat, lon, key, units)

            val timeAgo = calculateTimeAgo(response.current.dt)
            val description =
                response.current.weather[0]
                    .description
                    .capitalise()

            val unitsString =
                when (units) {
                    "imperial" -> "°F"
                    else -> "°C"
                } // needed?

            val temperature = "${response.current.temp.roundToInt()}°"
            val forecast = "$temperature, $description"
            Timber.Forest.i("Forecast: $forecast ($timeAgo)")
            forecast
        } catch (e: Exception) {
            Timber.Forest.e(e, "Failed to fetch and parse weather data")
            ""
        }
    }

    fun stop() {
        updateJob?.cancel()
        updateJob = null
    }
}

data class WeatherEvent(
    val forecast: String = "",
)
