package com.neilturner.aerialviews.services.weather

import android.content.Context
import com.neilturner.aerialviews.BuildConfig
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.services.weather.NetworkHelpers.buildOkHttpClient
import com.neilturner.aerialviews.utils.JsonHelper.buildSerializer
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

    private val openWeatherClient by lazy {
        Retrofit
            .Builder()
            .baseUrl("https://api.openweathermap.org/")
            .client(buildOkHttpClient(context))
            .addConverterFactory(buildSerializer())
            .build()
            .create(OpenWeatherApi::class.java)
    }

    suspend fun lookupLocation(query: String): List<LocationResponse> =
        try {
            val key = BuildConfig.OPEN_WEATHER
            val locations = openWeatherClient.getLocationByName(query, 10, key)
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
            val key = BuildConfig.OPEN_WEATHER
            val lat = GeneralPrefs.weatherLocationLat.toDoubleOrNull()
            val lon = GeneralPrefs.weatherLocationLon.toDoubleOrNull()
            val units = if (GeneralPrefs.weatherUnits == null) "metric" else GeneralPrefs.weatherUnits.toString().lowercase()

            if (key.isEmpty() || lat == null || lon == null) {
                Timber.Forest.e("Invalid location coordinates")
                return ""
            }

            val response = openWeatherClient.getCurrentWeather(lat, lon, key, units)

            val timeAgo = calculateTimeAgo(response.dt)
            val description =
                response.weather
                    .first()
                    .description
                    .capitalise()

            val unitsString =
                when (units) {
                    "imperial" -> "°F"
                    else -> "°C"
                } // needed?

            val temperature = "${response.main.temp.roundToInt()}°"
            val forecast = "$temperature, $description"
            Timber.Forest.i("Forecast: $forecast ($timeAgo)")
            temperature
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
    val temperature: String = "",
    val icon: String = "",
    val city: String = "",
    val wind: String = "",
    val humidity: String = "",
)
