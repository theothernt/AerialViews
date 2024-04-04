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
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch

class TextWeather : AppCompatTextView {

    var type = OverlayType.WEATHER1 // 1=Summary, 2=Forecast, 3=Rainfall?
    var weather: SharedFlow<WeatherResult>? = null
    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    init {
        TextViewCompat.setTextAppearance(this, R.style.OverlayText)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        coroutineScope.launch {
            weather?.collect { data ->
                // ${data.windSpeed} ${data.windDirection}
                val weather = "${data.description}, ${data.tempNow}" // (feels like ${data.tempFeelsLike})"
                text = weather
                Log.i(TAG, "Setting weather to: $weather")
            }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        weather = null
    }

    companion object {
        private const val TAG = "TextWeather"
    }
}
