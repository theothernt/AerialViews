package com.neilturner.aerialviews.services.weather

import com.neilturner.aerialviews.R
import timber.log.Timber

/**
 * Helper class for mapping OpenWeather condition codes to drawable resources
 * OpenWeather condition codes reference: https://openweathermap.org/weather-conditions
 */
object WeatherIcons {
    
    // Weather condition code groups (first digit of the code)
    private const val GROUP_THUNDERSTORM = 2 // 200-299
    private const val GROUP_DRIZZLE = 3      // 300-399
    private const val GROUP_RAIN = 5         // 500-599
    private const val GROUP_SNOW = 6         // 600-699
    private const val GROUP_ATMOSPHERE = 7   // 700-799
    private const val GROUP_CLEAR = 800      // 800
    private const val GROUP_CLOUDS = 8       // 801-899
    
    // Time of day indicators from OpenWeather icon codes
    private const val ICON_DAY = 'd'
    private const val ICON_NIGHT = 'n'
    
    /**
     * Maps OpenWeather condition codes to appropriate drawable resources
     * 
     * @param conditionCode The weather condition code from OpenWeather API
     * @param conditionType The main weather type (e.g., "Rain", "Snow", "Clear")
     * @param iconCode The icon code from OpenWeather (e.g., "01d", "02n")
     * @return The resource ID for the appropriate weather icon
     */
    fun getWeatherIcon(conditionCode: Int, conditionType: String, iconCode: String): Int {
        // Log the inputs for debugging
        Timber.d("Weather condition: code=$conditionCode, type=$conditionType, icon=$iconCode")
        
        // Default fallback icon
        var iconResource = R.drawable.sun
        
        // Determine if it's day or night from the icon code
        val isNight = iconCode.lastOrNull() == ICON_NIGHT
        
        // Map the condition code to the appropriate icon
        when {
            // Thunderstorm group (200-299)
            conditionCode / 100 == GROUP_THUNDERSTORM -> {
                iconResource = if (isNight) {
                    R.drawable.weather_thunderstorm_night
                } else {
                    R.drawable.weather_thunderstorm
                }
            }
            
            // Drizzle group (300-399)
            conditionCode / 100 == GROUP_DRIZZLE -> {
                iconResource = if (isNight) {
                    R.drawable.weather_drizzle_night
                } else {
                    R.drawable.weather_drizzle
                }
            }
            
            // Rain group (500-599)
            conditionCode / 100 == GROUP_RAIN -> {
                // Handle different types of rain
                iconResource = when (conditionCode) {
                    in 500..504 -> { // Light to heavy rain
                        if (isNight) R.drawable.weather_rain_night else R.drawable.weather_rain
                    }
                    511 -> { // Freezing rain
                        if (isNight) R.drawable.weather_freezing_rain_night else R.drawable.weather_freezing_rain
                    }
                    else -> { // Shower rain
                        if (isNight) R.drawable.weather_shower_night else R.drawable.weather_shower
                    }
                }
            }
            
            // Snow group (600-699)
            conditionCode / 100 == GROUP_SNOW -> {
                iconResource = if (isNight) {
                    R.drawable.weather_snow_night
                } else {
                    R.drawable.weather_snow
                }
            }
            
            // Atmosphere group (700-799): fog, mist, etc.
            conditionCode / 100 == GROUP_ATMOSPHERE -> {
                iconResource = if (isNight) {
                    R.drawable.weather_fog_night
                } else {
                    R.drawable.weather_fog
                }
            }
            
            // Clear sky (800)
            conditionCode == GROUP_CLEAR -> {
                iconResource = if (isNight) {
                    R.drawable.weather_clear_night
                } else {
                    R.drawable.weather_clear
                }
            }
            
            // Clouds group (801-899)
            conditionCode / 100 == GROUP_CLOUDS -> {
                // Different levels of cloudiness
                iconResource = when (conditionCode) {
                    801 -> { // Few clouds
                        if (isNight) R.drawable.weather_few_clouds_night else R.drawable.weather_few_clouds
                    }
                    802 -> { // Scattered clouds
                        if (isNight) R.drawable.weather_scattered_clouds_night else R.drawable.weather_scattered_clouds
                    }
                    else -> { // Broken or overcast clouds
                        if (isNight) R.drawable.weather_broken_clouds_night else R.drawable.weather_broken_clouds
                    }
                }
            }
            
            // Fallback for unknown conditions
            else -> {
                Timber.w("Unknown weather condition code: $conditionCode")
                iconResource = R.drawable.sun
            }
        }
        
        return iconResource
    }
}
