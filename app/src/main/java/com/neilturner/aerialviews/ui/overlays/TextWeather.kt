@file:Suppress("unused")

package com.neilturner.aerialviews.ui.overlays

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.widget.TextViewCompat
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.models.enums.OverlayType
import com.neilturner.aerialviews.services.WeatherResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch

class TextWeather : AppCompatTextView {

    var type = OverlayType.WEATHER1 // 1=Summary, 2=Forecast?
    var flow: SharedFlow<WeatherResult>? = MutableSharedFlow()
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
            flow?.collect { weather ->
                text = weather.tempNow
                Log.i(TAG, "Item: ${weather.tempNow}")
            }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        flow = null
    }

    companion object {
        private const val TAG = "TextWeather"
    }
}
