package com.neilturner.aerialviews.services

import android.content.Context
import com.neilturner.aerialviews.models.weather.OpenMeteoAPI
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.create

class OpenMeteoClient(context: Context) : WeatherClient(context) {

    val client by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(MoshiConverterFactory.create())
            .client(okHttpClient())
            .build()
            .create<OpenMeteoAPI>()
    }

    companion object {
        private const val BASE_URL = "http://api.open-meteo.com/"
        private const val TAG = "OpenMeteoClient"
    }
}
