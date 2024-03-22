@file:Suppress("unused")

package com.neilturner.aerialviews.ui.overlays

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.widget.TextViewCompat
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.models.enums.OverlayType
import com.neilturner.aerialviews.models.openweather.WeatherResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class TextWeather : AppCompatTextView {

    var type = OverlayType.WEATHER1 // 1=Summary, 2=Forecast, 3=Rainfall?
    var weatherFlow: StateFlow<WeatherResult>? = null
    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    init {
        TextViewCompat.setTextAppearance(this, R.style.OverlayText)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        Log.i(TAG, "Waiting for items...")
        coroutineScope.launch {
            weatherFlow?.collect { data ->
                if (data.tempNow.isEmpty()) return@collect
                val weather = "${data.description}, ${data.tempNow}, ${data.windSpeed} ${data.windDirection}"
                text = weather
                Log.i(TAG, "Setting weather to: $weather")
            }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        weatherFlow = null
    }

    companion object {
        private const val TAG = "TextWeather"
    }
}
