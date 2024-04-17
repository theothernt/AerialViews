package com.neilturner.aerialviews.models.weather

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ThreeHourFiveDayForecast(
    @Json(name = "city")
    val city: City,
    @Json(name = "cnt")
    val cnt: Int,
    @Json(name = "cod")
    val cod: String,
    @Json(name = "list")
    val list: List<Forecast>,
    @Json(name = "message")
    val message: Int
) {
    @JsonClass(generateAdapter = true)
    data class City(
        @Json(name = "coord")
        val coord: Coord,
        @Json(name = "country")
        val country: String,
        @Json(name = "id")
        val id: Int,
        @Json(name = "name")
        val name: String,
        @Json(name = "population")
        val population: Int,
        @Json(name = "sunrise")
        val sunrise: Int,
        @Json(name = "sunset")
        val sunset: Int,
        @Json(name = "timezone")
        val timezone: Int
    ) {
        @JsonClass(generateAdapter = true)
        data class Coord(
            @Json(name = "lat")
            val lat: Double,
            @Json(name = "lon")
            val lon: Double
        )
    }

    @JsonClass(generateAdapter = true)
    data class Forecast(
        @Json(name = "clouds")
        val clouds: Clouds,
        @Json(name = "dt")
        val dt: Int,
        @Json(name = "dt_txt")
        val dtTxt: String,
        @Json(name = "main")
        val main: Main,
        @Json(name = "pop")
        val pop: Double,
//        @Json(name = "rain")
//        val rain: Rain?,
        @Json(name = "sys")
        val sys: Sys,
//        @Json(name = "visibility")
//        val visibility: Int?,
        @Json(name = "weather")
        val weather: List<Weather>,
        @Json(name = "wind")
        val wind: Wind
    ) {
        @JsonClass(generateAdapter = true)
        data class Clouds(
            @Json(name = "all")
            val all: Int
        )

        @JsonClass(generateAdapter = true)
        data class Main(
            @Json(name = "feels_like")
            val feelsLike: Double,
            @Json(name = "grnd_level")
            val grndLevel: Int,
            @Json(name = "humidity")
            val humidity: Int,
            @Json(name = "pressure")
            val pressure: Int,
            @Json(name = "sea_level")
            val seaLevel: Int,
            @Json(name = "temp")
            val temp: Double,
            @Json(name = "temp_kf")
            val tempKf: Double,
            @Json(name = "temp_max")
            val tempMax: Double,
            @Json(name = "temp_min")
            val tempMin: Double
        )

        @JsonClass(generateAdapter = true)
        data class Rain(
            @Json(name = "3h")
            val h: Double
        )

        @JsonClass(generateAdapter = true)
        data class Sys(
            @Json(name = "pod")
            val pod: String
        )

        @JsonClass(generateAdapter = true)
        data class Weather(
            @Json(name = "description")
            val description: String,
            @Json(name = "icon")
            val icon: String,
            @Json(name = "id")
            val id: Int,
            @Json(name = "main")
            val main: String
        )

        @JsonClass(generateAdapter = true)
        data class Wind(
            @Json(name = "deg")
            val deg: Int,
            @Json(name = "gust")
            val gust: Double,
            @Json(name = "speed")
            val speed: Double
        )
    }
}
