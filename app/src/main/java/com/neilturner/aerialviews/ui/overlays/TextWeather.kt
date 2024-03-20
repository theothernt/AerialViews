@file:Suppress("unused")

package com.neilturner.aerialviews.ui.overlays

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.widget.TextViewCompat
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.models.enums.OverlayType
import com.neilturner.aerialviews.services.WeatherService

class TextWeather : AppCompatTextView {

    var type = OverlayType.WEATHER1 // 1=Summary, 2=Forecast?
    var service: WeatherService? = null

    private var refreshWeatherHandler: (() -> Unit)? = null

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    init {
        TextViewCompat.setTextAppearance(this, R.style.OverlayText)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        refreshWeatherHandler = null
        service = null
    }

    fun update() {
        // await weather refresh
        // then schedule to run every minute
        refreshWeather()
    }

    private fun refreshWeather() {
        var counter = 0
        refreshWeatherHandler = {
            counter++
            val weather = service?.weather()
            var refreshDelay = REFRESH_SHORT_DELAY
            if (weather != null) {
                refreshDelay = REFRESH_LONG_DELAY
                this.text = "${weather.tempNow} ($counter)"
            }
            this.postDelayed({ refreshWeatherHandler?.let { it() } }, refreshDelay)
        }
        this.postDelayed({ refreshWeatherHandler?.let { it() } }, REFRESH_SHORT_DELAY)
    }

    companion object {
        private const val TAG = "TextWeather"
        private const val REFRESH_SHORT_DELAY: Long = 1000
        private const val REFRESH_LONG_DELAY: Long = 30 * 1000
    }
}
