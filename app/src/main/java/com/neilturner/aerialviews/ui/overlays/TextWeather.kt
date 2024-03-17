@file:Suppress("unused")

package com.neilturner.aerialviews.ui.overlays

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.widget.TextViewCompat
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.models.enums.OverlayType

class TextWeather : AppCompatTextView {

    var type = OverlayType.WEATHER1 // 1=Summary, 2=Forecast?
    private var refreshWeatherHandler: (() -> Unit)? = null

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    init {
        TextViewCompat.setTextAppearance(this, R.style.OverlayText)
        refreshWeather()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        refreshWeatherHandler = null
    }

    private fun refreshWeather() {
        var counter = 0
        refreshWeatherHandler = {
            counter++
            this.text = "Weather Data $counter"
            this.postDelayed({
                refreshWeatherHandler?.let { it() }
            }, 5 * 1000)
        }
        this.postDelayed({
            refreshWeatherHandler?.let { it() }
        }, 500)
    }

    companion object {
        private const val TAG = "TextWeather"
        private const val REFRESH_DELAY: Long = 30 * 1000
    }
}
