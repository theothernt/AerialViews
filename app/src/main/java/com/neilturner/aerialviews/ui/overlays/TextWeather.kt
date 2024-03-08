@file:Suppress("unused")

package com.neilturner.aerialviews.ui.overlays

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.widget.TextViewCompat
import com.neilturner.aerialviews.R

class TextWeather : AppCompatTextView {

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
        refreshWeatherHandler = {
            this.text = "Weather Data"
            this.postDelayed({
                refreshWeatherHandler?.let { it() }
            }, 1000)
        }
        this.postDelayed({
            refreshWeatherHandler?.let { it() }
        }, 1000)
    }

    companion object {
        private const val TAG = "TextWeather"
    }
}
