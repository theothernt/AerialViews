package com.neilturner.aerialviews.services

import android.content.Context
import timber.log.Timber

class WeatherService(private val context: Context) {
    init {
        Timber.i("WeatherService: init()")
    }

    fun update ()
    {
        Timber.i("WeatherService: update()")
    }
}