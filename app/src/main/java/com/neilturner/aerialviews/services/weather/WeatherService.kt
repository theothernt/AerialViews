package com.neilturner.aerialviews.services.weather

import android.content.Context
import com.neilturner.aerialviews.BuildConfig
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.services.weather.NetworkHelpers.buildOkHttpClient
import com.neilturner.aerialviews.utils.JsonHelper.buildSerializer
import com.neilturner.aerialviews.utils.TimeHelper.calculateTimeAgo
import com.neilturner.aerialviews.utils.capitaliseEachWord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import me.kosert.flowbus.GlobalBus
import retrofit2.Retrofit
import timber.log.Timber
import kotlin.math.round
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.minutes
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
            val language = WeatherLanguage.getLanguageCode(context)
            val locations = openWeatherClient.getLocationByName(query, 10, key, language)
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
                    val update = forecastUpdate()
                    GlobalBus.post(update)
                    delay(5.minutes)
                }
            }
    }

    private suspend fun forecastUpdate(): WeatherEvent {
        return try {
            val key = BuildConfig.OPEN_WEATHER
            val lat = GeneralPrefs.weatherLocationLat.toDoubleOrNull()
            val lon = GeneralPrefs.weatherLocationLon.toDoubleOrNull()
            val units = if (GeneralPrefs.weatherUnits == null) "metric" else GeneralPrefs.weatherUnits.toString().lowercase()
            val language = WeatherLanguage.getLanguageCode(context)
            Timber.i("Language: $language")

            if (key.isEmpty() || lat == null || lon == null) {
                Timber.Forest.e("Invalid location coordinates")
                return WeatherEvent()
            }

            val response = openWeatherClient.getCurrentWeather(lat, lon, key, units, language)

            val timeAgo = calculateTimeAgo(response.dt)
            Timber.Forest.i("Forecast from $timeAgo")

            val temperature = "${response.main.temp.roundToInt()}°"
            val description =
                response.weather
                    .first()
                    .description
                    .capitaliseEachWord()
            val wind = round(response.wind.speed)
            val humidity = response.main.humidity
            val code = response.weather.first().id
            val type = response.weather.first().main
            val icon = response.weather.first().icon

            val unitsString =
                when (units) {
                    "imperial" -> "°F"
                    else -> "°C"
                } // needed?

            WeatherEvent(
                temperature = temperature,
                icon = getWeatherIcon(code, type, icon),
                summary = description,
                city = response.name,
                wind = "$wind km/h",
                humidity = "$humidity%",
            )
        } catch (e: Exception) {
            Timber.Forest.e(e, "Failed to fetch and parse weather data")
            WeatherEvent()
        }
    }

    fun getWeatherIcon(
        code: Int,
        type: String,
        icon: String,
    ): Int = R.drawable.sun

    fun stop() {
        updateJob?.cancel()
        updateJob = null
    }
}

data class WeatherEvent(
    val temperature: String = "",
    val icon: Int = -1,
    val summary: String = "",
    val city: String = "",
    val wind: String = "",
    val humidity: String = "",
)
