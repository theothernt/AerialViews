package com.neilturner.aerialviews.ui.overlays

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.util.TypedValue
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.models.enums.OverlayType
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.services.weather.WeatherEvent
import com.neilturner.aerialviews.utils.FontHelper
import me.kosert.flowbus.EventsReceiver
import me.kosert.flowbus.subscribe
import timber.log.Timber
import androidx.core.view.isNotEmpty
import com.neilturner.aerialviews.services.weather.WeatherInfo

class WeatherOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {
    var type = OverlayType.WEATHER1
    private val receiver = EventsReceiver()
    private var overlayItems: List<OverlayItem> = emptyList()
    private var layout = ""

    sealed class OverlayItem {
        data class TextItem(val text: String) : OverlayItem()
        data class ImageItem(@DrawableRes val imageResId: Int) : OverlayItem()
    }

    init {
        orientation = HORIZONTAL
    }

    fun style(font: String, size: String, weight: String) {
        // Set font properties
    }

    fun layout(layout: String) {
        // TEST
        this.layout = "ICON, TEMPERATURE"
        // this.layout = layout
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        receiver.subscribe { weather: WeatherEvent ->
            Timber.i("$weather")
            updateWeather(weather)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        receiver.unsubscribe()
    }

    private fun updateWeather(weather: WeatherEvent) {
        if (layout.isEmpty()) return

        layout.split(",").forEach { item ->
            val trimmedItem = item.trim()
            try {
                val weatherInfo = WeatherInfo.valueOf(trimmedItem)
                when (weatherInfo) {
                    WeatherInfo.TEMPERATURE -> overlayItems = overlayItems + OverlayItem.TextItem(weather.temperature)
                    WeatherInfo.ICON -> overlayItems = overlayItems + OverlayItem.ImageItem(R.drawable.sun)
                    WeatherInfo.CITY -> overlayItems = overlayItems + OverlayItem.TextItem(weather.city)
                    WeatherInfo.WIND -> overlayItems = overlayItems + OverlayItem.TextItem(weather.wind)
                    WeatherInfo.HUMIDITY -> overlayItems = overlayItems + OverlayItem.TextItem(weather.humidity)
                }
            } catch (e: IllegalArgumentException) {
                Timber.e("Invalid weather info item: $trimmedItem")
            }
        }

        // Check if visual update is needed
        // Hide overlay if needed
        setupViews()
        // Show overlay
    }

    private fun setupViews() {
        removeAllViews()

        val textSizePx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            18f,
            resources.displayMetrics
        )
        
        // Add a multiplier to make the icon slightly larger than the text for better visibility
        // Typically icons look better when they're about 1.2-1.5x the text size
        val iconSizePx = (textSizePx * 1.2).toInt()

        overlayItems.forEach { item ->
            when (item) {
                is OverlayItem.TextItem -> {
                    Timber.i("Adding text item: ${item.text}")
                    val textView = TextView(context).apply {
                        text = item.text
                        setTextColor(Color.WHITE) // Set text color to white
                    }
                    val params = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
                    params.gravity = android.view.Gravity.CENTER_VERTICAL
                    if (isNotEmpty()) {
                        params.leftMargin = 8
                    }

                    textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                    textView.typeface = FontHelper.getTypeface(context, GeneralPrefs.fontTypeface, GeneralPrefs.messageWeight)

                    textView.layoutParams = params
                    addView(textView)
                }
                
                is OverlayItem.ImageItem -> {
                    Timber.i("Adding image item: ${item.imageResId}")
                    val imageView = ImageView(context).apply {
                        val drawable = ContextCompat.getDrawable(context, item.imageResId)
                        if (drawable != null) {
                            DrawableCompat.setTint(drawable, Color.WHITE)
                        }
                        setImageDrawable(drawable)
                    }
                    
                    val params = LayoutParams(iconSizePx, iconSizePx)
                    params.gravity = android.view.Gravity.CENTER_VERTICAL
                    if (isNotEmpty()) {
                        params.leftMargin = 8
                    }

                    imageView.layoutParams = params
                    imageView.scaleType = ImageView.ScaleType.FIT_CENTER
                    addView(imageView)
                }
            }
        }
    }
}
