package com.neilturner.aerialviews.services

import android.content.Context
import android.util.Log
import com.neilturner.aerialviews.BuildConfig
import com.neilturner.aerialviews.models.openweather.ThreeHourFiveDayForecast
import com.neilturner.aerialviews.models.openweather.WeatherResult
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.utils.WeatherHelper.convertMeterToKilometer
import com.neilturner.aerialviews.utils.WeatherHelper.degreesToCardinal
import com.neilturner.aerialviews.utils.WeatherHelper.nearestTimestamp
import com.neilturner.aerialviews.utils.WeatherHelper.supportedLocale
import com.neilturner.aerialviews.utils.toStringOrEmpty
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import retrofit2.awaitResponse
import java.util.Locale
import kotlin.math.roundToInt

class WeatherService(private val context: Context, private val prefs: GeneralPrefs) {

    private val _weather = MutableSharedFlow<WeatherResult>(replay = 0)
    val weather
        get() = _weather.asSharedFlow()

    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    private var weatherData: ThreeHourFiveDayForecast? = null
    private var job: Job? = null

    init {
        // Try fetch then emit
        // or retry-backoff

        // Grab 12hr of data, allow refresh after 6hrs

        // Emit every 10 mins?

        // Don't send empty weather data class

        job = coroutineScope.launch {
            while (isActive) {
                Log.i(TAG, "Running...")
                fetchData()?.let {
                    weatherData = it
                }
                emitData()
            }
        }
    }

    private suspend fun emitData() {
        _weather.emit(weather())
        delay(30 * 1000)
    }

    fun stop() {
        job?.cancel()
    }

    private suspend fun fetchData(): ThreeHourFiveDayForecast? {
        val units = prefs.weatherUnits.toString()
        val city = prefs.weatherCity
        val appId = BuildConfig.OPEN_WEATHER_KEY
        val count = 4
        val lang = supportedLocale()

        // Don't try if KEY is blank
        // Check response code for no key, too many requests, etc

        val client = OpenWeatherClient(context).client
        val response = client.threeHourFiveDayForecast(city, appId, units, count, lang).awaitResponse()
        return if (response.isSuccessful) {
            if (response.raw().networkResponse?.isSuccessful == true) {
                // ToastHelper.show(context, "Network response", Toast.LENGTH_SHORT)
            } else if (response.raw().cacheResponse?.isSuccessful == true) {
                // ToastHelper.show(context, "Cache response", Toast.LENGTH_SHORT)
            }
            response.body()
        } else {
            val code = response.code()
            Log.e(TAG, "Response error: $code")
            null
        }
    }

    private fun weather(): WeatherResult {
        if (weatherData == null) {
            return WeatherResult()
        }

        // Pick the forecast closest to the current time
        val times = weatherData?.list?.map { it.dt.toLong() } ?: return WeatherResult()
        val nearestTime = nearestTimestamp(times)
        val current = weatherData?.list?.first { it.dt.toLong() == nearestTime } ?: return WeatherResult()

        val icon = if (prefs.weatherShowIcon) {
            current.main.toStringOrEmpty()
        } else {
            ""
        }

        val description = if (true) {
            current.weather.first().description.replaceFirstChar {
                if (it.isLowerCase()) {
                    it.titlecase(
                        Locale.getDefault()
                    )
                } else {
                    it.toString()
                }
            }
        } else {
            ""
        }

        val city = if (prefs.weatherShowCity) {
            weatherData?.city?.name.toStringOrEmpty()
        } else {
            ""
        }

        val tempNow = if (prefs.weatherShowTemp) {
            val temp = current.main.temp
            temp.roundToInt().toString() + " °C"
        } else {
            ""
        }

        val tempFeelsLike = if (prefs.weatherShowTemp) {
            val temp = current.main.feelsLike
            temp.roundToInt().toString() + " °C"
        } else {
            ""
        }

        var windSpeed = ""
        var windDirection = ""
        if (true) {
            // TO FIX
            windSpeed = convertMeterToKilometer(current.wind.speed) + " km/h"

            val degree = current.wind.deg
            windDirection = degreesToCardinal(degree)
        }

        val humidity = if (prefs.weatherShowHumidity) {
            weatherData?.list?.first()?.main?.humidity.toStringOrEmpty()
        } else {
            ""
        }

        return WeatherResult(
            icon,
            city,
            description,
            tempNow,
            tempFeelsLike,
            prefs.weatherUnits,
            windSpeed,
            windDirection,
            prefs.weatherWindUnits,
            humidity,
            current.dtTxt
        )
    }

    companion object {
        private const val TAG = "WeatherService"
    }
}
