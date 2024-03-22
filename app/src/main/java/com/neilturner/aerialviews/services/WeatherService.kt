package com.neilturner.aerialviews.services

import android.content.Context
import android.util.Log
import com.neilturner.aerialviews.BuildConfig
import com.neilturner.aerialviews.models.openweather.ThreeHourFiveDayForecast
import com.neilturner.aerialviews.models.openweather.WeatherResult
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.utils.WeatherHelper.degreesToCardinal
import com.neilturner.aerialviews.utils.WeatherHelper.supportedLocale
import com.neilturner.aerialviews.utils.toStringOrEmpty
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.awaitResponse

class WeatherService(private val context: Context, private val prefs: GeneralPrefs) {

    private val _weatherFlow = MutableStateFlow(WeatherResult())
    val weatherFlow = _weatherFlow.asStateFlow()

    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    private var weatherData: ThreeHourFiveDayForecast? = null
    private var emitJob: Job? = null

    init {
        coroutineScope.launch {
            while (true) {
                fetchData()
                Log.i(TAG, "Running...")
                _weatherFlow.emit(weather())
                delay(5 * 1000)
            }
        }
    }

    fun stop() {
        emitJob?.cancel()
    }

    private suspend fun fetchData() {
        val units = prefs.weatherUnits.toString()
        val city = prefs.weatherCity
        val appId = BuildConfig.OPEN_WEATHER_KEY
        val count = 4
        val lang = supportedLocale()

        try {
            val client = OpenWeatherClient(context).client
            val response = client.threeHourFiveDayForecast(city, appId, units, count, lang).awaitResponse()
            if (response.isSuccessful) {
                if (response.raw().networkResponse?.isSuccessful == true) {
                    Log.i(TAG, "Network response")
                }
                if (response.raw().cacheResponse?.isSuccessful == true) {
                    Log.i(TAG, "Cache response")
                }
                weatherData = response.body()
            } else {
                Log.e(TAG, "Response error: ${response.code()}")
            }
        } catch (ex: Exception) {
            Log.e(TAG, ex.message.toString())
        }
    }

    private suspend fun emitData() = withContext(Dispatchers.Main) {
        repeat(10) {
            _weatherFlow.emit(weather())
            delay(2000)
        }
    }

    private fun weather(): WeatherResult {
        // Time in UTC!
//        val times = weatherData?.list?.map { it.dt.toLong() }
//        val nearestTime = getNearestTimestamp(times!!)
//
//        val currentWeather = weatherData?.list?.first { it.dt.toLong() == nearestTime }
//        if (currentWeather != null) {
//            Log.i(TAG, currentWeather.dtTxt)
//        }

        val icon = if (prefs.weatherShowIcon) {
            weatherData?.list?.first()?.weather?.first()?.main.toStringOrEmpty()
        } else {
            ""
        }

        val city = if (prefs.weatherShowCity) {
            weatherData?.city?.name.toStringOrEmpty()
        } else {
            ""
        }

        val tempNow = if (prefs.weatherShowTemp) {
            // val temp = data?.list?.first()?.main?.temp
            // temp?.roundToInt().toString()
            (0..30).random().toString()
        } else {
            ""
        }

        var windSpeed = ""
        var windDirection = ""
        if (prefs.weatherShowWind) {
            windSpeed = weatherData?.list?.first()?.wind?.speed.toString()
            // convert to k/mh

            val degree = weatherData?.list?.first()?.wind?.deg
            windDirection = if (degree == null) "" else degreesToCardinal(degree)
        }

        val humidity = if (prefs.weatherShowHumidity) {
            weatherData?.list?.first()?.main?.humidity.toStringOrEmpty()
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

    companion object {
        private const val TAG = "WeatherService"
    }
}
