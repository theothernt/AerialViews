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
                response.isSuccessful -> response.body() ?: emptyList()
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

    fun startUpdates() {
        updateJob?.cancel()
        retryCount = 0
        updateJob =
            CoroutineScope(Dispatchers.IO).launch {
                while (isActive) {
                    val update = forecastUpdate()
                    GlobalBus.post(update)
                    Timber.i("Next weather update in ${updateDelay.inWholeMinutes} minutes")
                    delay(updateDelay)
                }
            }
    }

    private suspend fun forecastUpdate(): WeatherEvent {
        return try {
            val key = BuildConfig.OPEN_WEATHER
            val lat = GeneralPrefs.weatherLocationLat.toDoubleOrNull()
            val lon = GeneralPrefs.weatherLocationLon.toDoubleOrNull()
            val units = if (GeneralPrefs.weatherTemperatureUnits == null) "metric" else GeneralPrefs.weatherTemperatureUnits.toString().lowercase()
            val language = WeatherLanguage.getLanguageCode(context)
            Timber.i("Language: $language")

            if (key.isEmpty() || lat == null || lon == null) {
                Timber.Forest.e("Invalid location coordinates")
                return WeatherEvent()
            }

            val response = openWeatherClient.getCurrentWeather(lat, lon, key, units, language)
            when {
                response.isSuccessful -> {
                    val weatherData = response.body()
                    if (weatherData != null) {
                        retryCount = 0 // Reset retry count on successful response
                        processWeatherResponse(weatherData)
                    } else {
                        Timber.e("Received successful response but body was null")
                        WeatherEvent()
                    }
                }
                response.code() == 401 -> {
                    Timber.e("Unauthorized access to weather API - cancelling weather updates")
                    stop() // Cancel the job for unauthorized access
                    WeatherEvent()
                }
                response.code() in 500..599 -> {
                    Timber.w("Server error (${response.code()}) - attempt ${retryCount + 1}/$maxRetries")
                    if (retryCount < maxRetries) {
                        retryCount++
                        delay(retryDelay) // Wait before retry
                        return forecastUpdate() // Retry
                    } else {
                        Timber.e("Max retries reached for server error, giving up")
                        retryCount = 0
                        WeatherEvent()
                    }
                }
                response.code() == 429 -> {
                    Timber.w("Rate limit exceeded (429) - backing off")
                    delay(rateLimitDelay) // Back off for rate limiting
                    WeatherEvent()
                }
                else -> {
                    Timber.e("Failed to fetch weather data - HTTP ${response.code()}: ${response.message()}")
                    WeatherEvent()
                }
            }
        } catch (e: Exception) {
            Timber.Forest.e(e, "Failed to fetch and parse weather data")
            WeatherEvent()
        }
    }

    private fun processWeatherResponse(response: CurrentWeatherResponse): WeatherEvent {
        val timeAgo = calculateTimeAgo(response.dt)
        Timber.Forest.i("Forecast from $timeAgo")

        val temperature = "${response.main.temp.roundToInt()}°"
        val description =
            response.weather
                .first()
                .description
                .capitalise()
        val wind = round(response.wind.speed)
        val humidity = response.main.humidity
        val code = response.weather.first().id
        val type = response.weather.first().main
        val icon = response.weather.first().icon

        return WeatherEvent(
            temperature = temperature,
            icon = getWeatherIcon(code, type, icon),
            summary = description,
            city = response.name,
            wind = "$wind km/h",
            humidity = "$humidity%",
        )
    }

    fun getWeatherIcon(
        code: Int,
        type: String,
        icon: String,
    ): Int = WeatherIcons.getWeatherIcon(code, type, icon)

    fun stop() {
        updateJob?.cancel()
        updateJob = null
        retryCount = 0
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
