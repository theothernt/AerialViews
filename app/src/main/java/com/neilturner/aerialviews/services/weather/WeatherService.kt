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
    private val apiOverride: OpenWeatherApi? = null,
) {
    private var updateJob: Job? = null
    private val maxRetries = 3
    private var totalUpdates = 0

    private val lookupDelay = 1.seconds
    private val errorDelay = 3.seconds // Slow down response when there is an error
    private val updateDelay = 61.minutes // Delay between full weather data updates
    private val rateLimitDelay = 1.minutes // Delay for rate limiting
    private val retryDelay = 30.seconds // Delay before retrying after an error

    private val openWeatherClient by lazy {
        apiOverride
            ?: Retrofit
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

    fun startUpdates(
        fetchCurrentWeather: Boolean,
        fetchForecast: Boolean,
    ) {
        updateJob?.cancel()
        if (!fetchCurrentWeather && !fetchForecast) {
            Timber.i("Weather updates not started because no weather overlays are active")
            return
        }

        updateJob =
            CoroutineScope(Dispatchers.IO).launch {
                while (isActive) {
                    val config = buildRequestConfig()
                    val result =
                        if (config == null) {
                            WeatherResult()
                        } else {
                            fetchWeatherData(
                                requests =
                                    WeatherRequests(
                                        fetchCurrentWeather = fetchCurrentWeather,
                                        fetchForecast = fetchForecast,
                                    ),
                                config = config,
                                displayConfig = buildDisplayConfig(),
                            )
                        }
                    if (result.weather != null) GlobalBus.post(result.weather)
                    if (result.forecast != null) GlobalBus.post(result.forecast)
                    Timber.i("Next weather update in ${updateDelay.inWholeMinutes} minutes")
                    delay(updateDelay)
                }
            }
    }

    internal fun buildRequestConfig(): WeatherRequestConfig? {
        val key = BuildConfig.OPEN_WEATHER
        val lat = GeneralPrefs.weatherLocationLat.toDoubleOrNull()
        val lon = GeneralPrefs.weatherLocationLon.toDoubleOrNull()
        val units = GeneralPrefs.weatherTemperatureUnits?.toString()?.lowercase() ?: "metric"
        val language = WeatherLanguage.getLanguageCode(context)

        if (key.isEmpty() || lat == null || lon == null) {
            Timber.e("Invalid location coordinates")
            return null
        }

        return WeatherRequestConfig(
            apiKey = key,
            lat = lat,
            lon = lon,
            units = units,
            language = language,
        )
    }

    internal fun buildDisplayConfig(): WeatherDisplayConfig =
        WeatherDisplayConfig(
            currentWeatherCity = GeneralPrefs.weatherLocationCustomName,
            forecastCity = GeneralPrefs.weatherLocationCustomName,
            forecastDays = GeneralPrefs.weatherLine2Days.toIntOrNull() ?: 5,
        )

    internal suspend fun fetchWeatherData(
        requests: WeatherRequests,
        config: WeatherRequestConfig,
        displayConfig: WeatherDisplayConfig,
    ): WeatherResult {
        var weatherEvent: WeatherEvent? = null
        var forecastEvent: ForecastEvent? = null

        if (requests.fetchCurrentWeather) {
            val response =
                fetchCurrentWeatherResponse(
                    config = config,
                    attempt = 0,
                )
            weatherEvent =
                response?.let {
                    mapCurrentWeatherResponse(
                        response = it,
                        city = displayConfig.currentWeatherCity.ifEmpty { it.name },
                    )
                }
        }

        if (requests.fetchForecast) {
            val response =
                fetchForecastResponse(
                    config = config,
                    attempt = 0,
                )
            forecastEvent =
                response?.let {
                    val timezoneOffset = it.city.timezone.toLong()
                    val today =
                        ZonedDateTime
                            .now(ZoneId.ofOffset("UTC", java.time.ZoneOffset.ofTotalSeconds(timezoneOffset.toInt())))
                            .toLocalDate()

                    mapForecastResponse(
                        response = it,
                        today = today,
                        city = displayConfig.forecastCity.ifEmpty { it.city.name },
                        maxDays = displayConfig.forecastDays,
                    )
                }
        }

        if (weatherEvent != null || forecastEvent != null) {
            totalUpdates++
        }

        return WeatherResult(
            weather = weatherEvent,
            forecast = forecastEvent,
        )
    }

    private suspend fun fetchCurrentWeatherResponse(
        config: WeatherRequestConfig,
        attempt: Int,
    ): CurrentWeatherResponse? =
        try {
            val response =
                openWeatherClient.getCurrentWeather(
                    lat = config.lat,
                    lon = config.lon,
                    apiKey = config.apiKey,
                    units = config.units,
                    language = config.language,
                )

            when {
                response.isSuccessful -> {
                    response.body().also { body ->
                        if (body == null) {
                            Timber.e("Received successful current weather response but body was null")
                        }
                    }
                }

                response.code() == 401 -> {
                    val error = "Unauthorized access to current weather API - cancelling weather updates"
                    Timber.e(error)
                    stop()
                    FirebaseHelper.crashlyticsLogMessage(error)
                    null
                }

                response.code() in 500..599 -> {
                    Timber.w("Current weather server error (${response.code()}) - attempt ${attempt + 1}/$maxRetries")
                    retryCurrentWeather(config, attempt)
                }

                response.code() == 429 -> {
                    val error = "Current weather rate limit exceeded - backing off"
                    Timber.w(error)
                    FirebaseHelper.crashlyticsLogMessage(error)
                    delay(rateLimitDelay)
                    null
                }

                else -> {
                    val error = "Failed to fetch current weather - HTTP ${response.code()}: ${response.message()}"
                    Timber.e(error)
                    FirebaseHelper.crashlyticsLogMessage(error)
                    null
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to fetch and parse current weather data")
            FirebaseHelper.crashlyticsException(e)
            null
        }

    private suspend fun retryCurrentWeather(
        config: WeatherRequestConfig,
        attempt: Int,
    ): CurrentWeatherResponse? {
        if (attempt >= maxRetries) {
            val error = "Max retries reached for current weather server errors - giving up"
            Timber.e(error)
            FirebaseHelper.crashlyticsLogMessage(error)
            return null
        }

        delay(retryDelay)
        return fetchCurrentWeatherResponse(config, attempt + 1)
    }

    private suspend fun fetchForecastResponse(
        config: WeatherRequestConfig,
        attempt: Int,
    ): FiveDayForecastResponse? =
        try {
            val response =
                openWeatherClient.getForecast(
                    lat = config.lat,
                    lon = config.lon,
                    apiKey = config.apiKey,
                    units = config.units,
                    language = config.language,
                )

            when {
                response.isSuccessful -> {
                    response.body().also { body ->
                        if (body == null) {
                            Timber.e("Received successful forecast response but body was null")
                        }
                    }
                }

                response.code() == 401 -> {
                    val error = "Unauthorized access to forecast API - cancelling weather updates"
                    Timber.e(error)
                    stop()
                    FirebaseHelper.crashlyticsLogMessage(error)
                    null
                }

                response.code() in 500..599 -> {
                    Timber.w("Forecast server error (${response.code()}) - attempt ${attempt + 1}/$maxRetries")
                    retryForecast(config, attempt)
                }

                response.code() == 429 -> {
                    val error = "Forecast rate limit exceeded - backing off"
                    Timber.w(error)
                    FirebaseHelper.crashlyticsLogMessage(error)
                    delay(rateLimitDelay)
                    null
                }

                else -> {
                    val error = "Failed to fetch forecast - HTTP ${response.code()}: ${response.message()}"
                    Timber.e(error)
                    FirebaseHelper.crashlyticsLogMessage(error)
                    null
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to fetch and parse forecast data")
            FirebaseHelper.crashlyticsException(e)
            null
        }

    private suspend fun retryForecast(
        config: WeatherRequestConfig,
        attempt: Int,
    ): FiveDayForecastResponse? {
        if (attempt >= maxRetries) {
            val error = "Max retries reached for forecast server errors - giving up"
            Timber.e(error)
            FirebaseHelper.crashlyticsLogMessage(error)
            return null
        }

        delay(retryDelay)
        return fetchForecastResponse(config, attempt + 1)
    }

    internal fun processCurrentWeatherResponse(response: CurrentWeatherResponse): WeatherEvent {
        val city = GeneralPrefs.weatherLocationCustomName.ifEmpty { response.name }
        return mapCurrentWeatherResponse(response, city)
    }

    internal fun processForecastResponse(response: FiveDayForecastResponse): ForecastEvent {
        val timezoneOffset = response.city.timezone.toLong()
        val today = ZonedDateTime.now(ZoneId.ofOffset("UTC", java.time.ZoneOffset.ofTotalSeconds(timezoneOffset.toInt()))).toLocalDate()
        val city = GeneralPrefs.weatherLocationCustomName.ifEmpty { response.city.name }
        val maxDays = GeneralPrefs.weatherLine2Days.toIntOrNull() ?: 5
        return mapForecastResponse(response, today, city, maxDays)
    }

    internal fun mapCurrentWeatherResponse(
        response: CurrentWeatherResponse,
        city: String,
    ): WeatherEvent {
        val weatherInfo = response.weather.firstOrNull() ?: return WeatherEvent()
        val wind = round(response.wind.speed)

        return WeatherEvent(
            temperature = "${response.main.temp.roundToInt()}°",
            icon = getWeatherIcon(weatherInfo.id, weatherInfo.main, weatherInfo.icon),
            summary = weatherInfo.description.capitalise(),
            city = city,
            wind = "$wind km/h",
            humidity = "${response.main.humidity}%",
        )
    }

    internal fun mapForecastResponse(
        response: FiveDayForecastResponse,
        today: java.time.LocalDate,
        city: String,
        maxDays: Int,
    ): ForecastEvent {
        val timezoneOffset = response.city.timezone.toLong()
        val forecastDays = aggregateForecastByDay(response.list, today, timezoneOffset)
        val limitedDays = forecastDays.take(maxDays)

        Timber.i("Processed forecast: ${limitedDays.size} days for ${response.city.name}")
        return ForecastEvent(days = limitedDays, city = city)
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
            }.filterKeys { !it.isBefore(today) }
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

data class WeatherRequests(
    val fetchCurrentWeather: Boolean = false,
    val fetchForecast: Boolean = false,
)

data class WeatherRequestConfig(
    val apiKey: String,
    val lat: Double,
    val lon: Double,
    val units: String,
    val language: String,
)

data class WeatherDisplayConfig(
    val currentWeatherCity: String = "",
    val forecastCity: String = "",
    val forecastDays: Int = 5,
)
