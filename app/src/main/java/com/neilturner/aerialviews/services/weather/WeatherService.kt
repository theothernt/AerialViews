package com.neilturner.aerialviews.services.weather

import android.content.Context
import android.os.Bundle
import com.neilturner.aerialviews.BuildConfig
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.services.weather.NetworkHelpers.buildOkHttpClient
import com.neilturner.aerialviews.utils.FirebaseHelper
import com.neilturner.aerialviews.utils.JsonHelper.buildSerializer
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
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.round
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class WeatherService(
    val context: Context,
) {
    private var updateJob: Job? = null
    private var retryCount = 0
    private val maxRetries = 3
    private var totalUpdates = 0

    private val lookupDelay = 1.seconds
    private val errorDelay = 3.seconds // Slow down response when there is an error
    private val updateDelay = 61.minutes // Delay between full weather data updates
    private val rateLimitDelay = 1.minutes // Delay for rate limiting
    private val retryDelay = 30.seconds // Delay before retrying after an error

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
            val response = openWeatherClient.getLocationByName(query, 10, key, language)
            delay(lookupDelay)

            when {
                response.isSuccessful -> {
                    response.body() ?: emptyList()
                }

                response.code() == 401 -> {
                    Timber.e("Unauthorized access to weather API - invalid API key")
                    delay(errorDelay)
                    emptyList()
                }

                response.code() in 500..599 -> {
                    Timber.e("Server error (${response.code()}) while fetching location data")
                    delay(errorDelay)
                    emptyList()
                }

                else -> {
                    Timber.e("Failed to fetch location data - HTTP ${response.code()}: ${response.message()}")
                    delay(errorDelay)
                    emptyList()
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to fetch location data")
            delay(errorDelay)
            emptyList()
        }

    suspend fun lookupLocationByCoordinates(
        lat: Double,
        lon: Double,
    ): List<LocationResponse> =
        try {
            val key = BuildConfig.OPEN_WEATHER
            val language = WeatherLanguage.getLanguageCode(context)
            val response = openWeatherClient.getLocationByCoordinates(lat, lon, 5, key, language)
            delay(lookupDelay)

            when {
                response.isSuccessful -> {
                    response.body() ?: emptyList()
                }

                response.code() == 401 -> {
                    Timber.e("Unauthorized access to weather API - invalid API key")
                    delay(errorDelay)
                    emptyList()
                }

                response.code() in 500..599 -> {
                    Timber.e("Server error (${response.code()}) while fetching location data")
                    delay(errorDelay)
                    emptyList()
                }

                else -> {
                    Timber.e("Failed to fetch location data - HTTP ${response.code()}: ${response.message()}")
                    delay(errorDelay)
                    emptyList()
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to fetch location data by coordinates")
            delay(errorDelay)
            emptyList()
        }

    fun startUpdates() {
        updateJob?.cancel()
        retryCount = 0
        updateJob =
            CoroutineScope(Dispatchers.IO).launch {
                while (isActive) {
                    val result = fetchWeatherAndForecast()
                    if (result.weather != null) GlobalBus.post(result.weather)
                    if (result.forecast != null) GlobalBus.post(result.forecast)
                    Timber.i("Next weather update in ${updateDelay.inWholeMinutes} minutes")
                    delay(updateDelay)
                }
            }
    }

    private suspend fun fetchWeatherAndForecast(): WeatherResult {
        return try {
            val key = BuildConfig.OPEN_WEATHER
            val lat = GeneralPrefs.weatherLocationLat.toDoubleOrNull()
            val lon = GeneralPrefs.weatherLocationLon.toDoubleOrNull()
            val units =
                if (GeneralPrefs.weatherTemperatureUnits == null) {
                    "metric"
                } else {
                    GeneralPrefs.weatherTemperatureUnits.toString().lowercase()
                }
            val language = WeatherLanguage.getLanguageCode(context)

            if (key.isEmpty() || lat == null || lon == null) {
                Timber.e("Invalid location coordinates")
                return WeatherResult()
            }

            val response = openWeatherClient.getForecast(lat, lon, key, units, language)
            when {
                response.isSuccessful -> {
                    val forecastData = response.body()
                    if (forecastData != null) {
                        totalUpdates++
                        retryCount = 0
                        processForecastResponse(forecastData)
                    } else {
                        Timber.e("Received successful forecast response but body was null")
                        WeatherResult()
                    }
                }

                response.code() == 401 -> {
                    val error = "Unauthorized access to forecast API - cancelling weather updates"
                    Timber.e(error)
                    stop()
                    FirebaseHelper.crashlyticsLogMessage(error)
                    WeatherResult()
                }

                response.code() in 500..599 -> {
                    Timber.w("Server error (${response.code()}) - attempt ${retryCount + 1}/$maxRetries")
                    if (retryCount < maxRetries) {
                        retryCount++
                        delay(retryDelay)
                        return fetchWeatherAndForecast()
                    } else {
                        val error = "Max retries reached for server error - giving up"
                        Timber.e(error)
                        FirebaseHelper.crashlyticsLogMessage(error)
                        retryCount = 0
                        WeatherResult()
                    }
                }

                response.code() == 429 -> {
                    val error = "Rate limit exceeded - backing off"
                    Timber.w(error)
                    FirebaseHelper.crashlyticsLogMessage(error)
                    delay(rateLimitDelay)
                    WeatherResult()
                }

                else -> {
                    val error = "Failed to fetch forecast - HTTP ${response.code()}: ${response.message()}"
                    Timber.e(error)
                    FirebaseHelper.crashlyticsLogMessage(error)
                    WeatherResult()
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to fetch and parse weather data")
            FirebaseHelper.crashlyticsException(e)
            WeatherResult()
        }
    }

    private fun processForecastResponse(response: FiveDayForecastResponse): WeatherResult {
        val timezoneOffset = response.city.timezone.toLong()
        val today = ZonedDateTime.now(ZoneId.ofOffset("UTC", java.time.ZoneOffset.ofTotalSeconds(timezoneOffset.toInt()))).toLocalDate()

        val city = GeneralPrefs.weatherLocationCustomName.ifEmpty { response.city.name }

        val weatherEvent = buildCurrentWeather(response.list, city)
        val forecastDays = aggregateForecastByDay(response.list, today, timezoneOffset)
        val maxDays = GeneralPrefs.weatherLine2Days.toIntOrNull() ?: 5
        val limitedDays = forecastDays.take(maxDays)

        Timber.i("Processed forecast: ${limitedDays.size} days for ${response.city.name}")
        return WeatherResult(
            weather = weatherEvent,
            forecast = ForecastEvent(days = limitedDays, city = city),
        )
    }

    private fun buildCurrentWeather(
        items: List<ForecastItem>,
        city: String,
    ): WeatherEvent {
        val nearest = items.firstOrNull() ?: return WeatherEvent()
        val temperature = "${nearest.main.temp.roundToInt()}°"
        val description = nearest.weather.first().description.capitalise()
        val wind = round(nearest.wind.speed)
        val humidity = nearest.main.humidity
        val code = nearest.weather.first().id
        val type = nearest.weather.first().main
        val icon = nearest.weather.first().icon

        return WeatherEvent(
            temperature = temperature,
            icon = getWeatherIcon(code, type, icon),
            summary = description,
            city = city,
            wind = "$wind km/h",
            humidity = "$humidity%",
        )
    }

    private fun aggregateForecastByDay(
        items: List<ForecastItem>,
        today: java.time.LocalDate,
        timezoneOffset: Long,
    ): List<ForecastDay> {
        val zoneOffset = java.time.ZoneOffset.ofTotalSeconds(timezoneOffset.toInt())

        return items
            .groupBy { item ->
                Instant.ofEpochSecond(item.dt).atOffset(zoneOffset).toLocalDate()
            }
            .filterKeys { !it.isBefore(today) }
            .toSortedMap()
            .map { (date, dayItems) ->
                val tempHigh = dayItems.maxOf { it.main.tempMax }.roundToInt()
                val tempLow = dayItems.minOf { it.main.tempMin }.roundToInt()

                val middayItem =
                    dayItems.minByOrNull { item ->
                        val hour = Instant.ofEpochSecond(item.dt).atOffset(zoneOffset).hour
                        kotlin.math.abs(hour - 12)
                    } ?: dayItems.first()

                val weatherInfo = middayItem.weather.first()
                val icon = getWeatherIcon(weatherInfo.id, weatherInfo.main, weatherInfo.icon)
                val dayName = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())

                ForecastDay(
                    dayName = dayName,
                    icon = icon,
                    tempHigh = "$tempHigh°",
                    tempLow = "$tempLow°",
                )
            }
    }

    fun getWeatherIcon(
        code: Int,
        type: String,
        icon: String,
    ): Int = WeatherIcons.getWeatherIcon(code, type, icon)

    fun stop() {
        updateJob?.cancel()
        updateJob = null
        FirebaseHelper.analyticsEvent(
            "weather_updates",
            Bundle().apply {
                putInt("per_session", totalUpdates)
            },
        )
        Timber.i("Weather updates stopped, total updates for session: $totalUpdates")
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

data class ForecastDay(
    val dayName: String = "",
    val icon: Int = -1,
    val tempHigh: String = "",
    val tempLow: String = "",
)

data class ForecastEvent(
    val days: List<ForecastDay> = emptyList(),
    val city: String = "",
)

data class WeatherResult(
    val weather: WeatherEvent? = null,
    val forecast: ForecastEvent? = null,
)
