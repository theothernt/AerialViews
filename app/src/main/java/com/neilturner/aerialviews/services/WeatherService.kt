package com.neilturner.aerialviews.services

import android.content.Context
import android.util.Log
import com.neilturner.aerialviews.BuildConfig
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.models.weather.HourlyOneDayForecast
import com.neilturner.aerialviews.models.weather.ThreeHourFiveDayForecast
import com.neilturner.aerialviews.models.weather.WeatherResult
import com.neilturner.aerialviews.utils.WeatherHelper
import com.neilturner.aerialviews.utils.WeatherHelper.convertMeterToKilometer
import com.neilturner.aerialviews.utils.WeatherHelper.supportedLocale
import com.neilturner.aerialviews.utils.WeatherHelper.timestampToLocalTime
import com.neilturner.aerialviews.utils.capitalise
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import retrofit2.awaitResponse
import java.util.TimeZone
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.roundToInt

class WeatherService(private val context: Context, private val prefs: GeneralPrefs) {

    private val coroutineScope = CoroutineScope(Dispatchers.IO) + Job()
    private val _weather = MutableSharedFlow<WeatherResult>(replay = 0)
    val weather
        get() = _weather.asSharedFlow()

    init {
        // Try fetch then emit
        // or retry-backoff

        // Grab 12hr of data, allow refresh after 6hrs

        // Emit every 10 mins?

        // Don't send empty weather data class

        coroutineScope.launch {
            while (isActive) {
                Log.i(TAG, "Running...")
                fetchOpenWeather()?.let {
                    val forecast = processOpenWeatherResponse(it)
                    // _weather.emit(forecast)
                }
                fetchOpenMeteo()?.let {
                    val forecast = processOpenMeteoResponse(it)
                    _weather.emit(forecast)
                }
                delay(30 * 1000)
            }
        }
    }

    fun stop() {
        coroutineScope.cancel()
    }

    private suspend fun fetchOpenWeather(): ThreeHourFiveDayForecast? {
        val units = prefs.weatherUnits.toString()
        // val city = prefs.weatherCityName
        val lat = "53.29"
        val lon = "-6.194"
        val appId =  BuildConfig.OPEN_WEATHER_KEY
        val count = 8
        val lang = supportedLocale()

        // Don't try if KEY is blank
        // Check response code for no key, too many requests, etc

        return try {
            val client = OpenWeatherClient(context).client
            val response = client.threeHourFiveDayForecast(lat, lon, appId, units, count, lang).awaitResponse()
            if (response.raw().networkResponse?.isSuccessful == true) {
                Log.i(TAG, "Network response")
            } else if (response.raw().cacheResponse?.isSuccessful == true) {
                Log.i(TAG, "Cache response")
            }
            if (response.isSuccessful) {
                response.body()
            } else {
                val code = response.code()
                Log.e(TAG, "Response error: $code")
                null
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, e.message.toString())
            null
        }
    }

    private suspend fun fetchOpenMeteo(): HourlyOneDayForecast? {
        // val units = prefs.weatherUnits.toString()
        // val city = prefs.weatherCityLatLng
        val lat = "53.29"
        val lon = "-6.194"
        val timezone = TimeZone.getDefault().id
        val days = 1

        return try {
            val client = OpenMeteoClient(context).client
            val response = client.hourlyOneDayForecast(lat, lon, timezone, forecastDays = days).awaitResponse()
            if (response.raw().networkResponse?.isSuccessful == true) {
                Log.i(TAG, "Network response")
            } else if (response.raw().cacheResponse?.isSuccessful == true) {
                Log.i(TAG, "Cache response")
            }
            if (response.isSuccessful) {
                response.body()
            } else {
                val code = response.code()
                Log.e(TAG, "Response error: $code")
                null
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, e.message.toString())
            null
        }
    }

    private fun processOpenMeteoResponse(data: HourlyOneDayForecast): WeatherResult {
        // Find nearest timestamp to current, get index
        val times = data.hourly.time.map { it.toLong() }
        val nearestTime = WeatherHelper.nearestTimestamp(times)
        val index = data.hourly.time.indexOf(nearestTime.toString())

        val timeDate = timestampToLocalTime(nearestTime)
        Log.i(TAG, "Times: ${times.count()}, $index, $timeDate ($nearestTime)")

        val icon = ""
        val description = WeatherHelper.weatherCodeToDescription(data.hourly.weatherCode[index])
        val tempNow = data.hourly.temperature2m[index].roundToInt().toString() + " °C"
        val tempFeelsLike = data.hourly.apparentTemperature[index].roundToInt().toString() + " °C"
        val windSpeed = data.hourly.windSpeed10m[index].roundToInt().toString() + " km/h"
        val windDirection = data.hourly.windDirection10m[index].toString()

        Log.i(TAG, "OpenMeteo: $description, $tempNow, $windSpeed")
        return WeatherResult(
            icon,
            description,
            tempNow,
            tempFeelsLike,
            prefs.weatherUnits,
            windSpeed,
            windDirection,
            prefs.weatherWindUnits
        )
    }

    private fun processOpenWeatherResponse(data: ThreeHourFiveDayForecast): WeatherResult {
        // Find nearest timestamp to current, pick forecast (group of data) with timestamp
        val times = data.list.map { it.dt.toLong() }
        val nearestTime = WeatherHelper.nearestTimestamp(times)
        val current = data.list.first { it.dt.toLong() == nearestTime }
        val index = data.list.indexOf(current)

        val timeDate = timestampToLocalTime(nearestTime)
        Log.i(TAG, "Times: ${times.count()}, $index, $timeDate ($nearestTime)")

        val icon = ""
        val description = current.weather.first().description.capitalise()
        val tempNow = current.main.temp.roundToInt().toString() + " °C"
        val tempFeelsLike = current.main.feelsLike.roundToInt().toString() + " °C"
        val windSpeed = convertMeterToKilometer(current.wind.speed) + " km/h"
        val windDirection = current.wind.deg.toString() // degreesToCardinal

        Log.i(TAG, "OpenWeather: $description, $tempNow, $windSpeed")
        return WeatherResult(
            icon,
            description,
            tempNow,
            tempFeelsLike,
            prefs.weatherUnits,
            windSpeed,
            windDirection,
            prefs.weatherWindUnits
        )
    }

    companion object {
        private const val TAG = "WeatherService"
    }
}
