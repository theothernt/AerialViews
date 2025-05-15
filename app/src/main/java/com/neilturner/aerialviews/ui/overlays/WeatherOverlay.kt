package com.neilturner.aerialviews.ui.overlays

import android.content.Context
import android.util.AttributeSet
import android.util.TypedValue
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.core.view.isNotEmpty
import androidx.core.widget.TextViewCompat
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.models.enums.OverlayType
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.services.weather.WeatherEvent
import com.neilturner.aerialviews.services.weather.WeatherInfo
import com.neilturner.aerialviews.ui.overlays.SvgImageView
import com.neilturner.aerialviews.utils.FontHelper
import me.kosert.flowbus.EventsReceiver
import me.kosert.flowbus.subscribe
import timber.log.Timber

class WeatherOverlay
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0,
    ) : LinearLayout(context, attrs, defStyleAttr) {
        var type = OverlayType.WEATHER1
        private val receiver = EventsReceiver()
        private var overlayItems: List<OverlayItem> = emptyList()
        private var layout = ""

        private var font = ""
        private var size = 0f
        private var weight = ""

        sealed class OverlayItem {
            data class TextItem(
                val text: String,
            ) : OverlayItem()

            data class ImageItem(
                @DrawableRes val imageResId: Int,
            ) : OverlayItem()
        }

        init {
            orientation = HORIZONTAL
        }

        fun style(
            font: String,
            size: Float,
            weight: String,
        ) {
            this.font = font
            this.size = size
            this.weight = weight
        }

        fun layout(layout: String) {
            // TEST
            this.layout = "TEMPERATURE, ICON, SUMMARY"
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
            if (weather.temperature.isEmpty()) return

            overlayItems = emptyList()

            layout.split(",").forEach { item ->
                val trimmedItem = item.trim()
                try {
                    val weatherInfo = WeatherInfo.valueOf(trimmedItem)
                    when (weatherInfo) {
                        WeatherInfo.TEMPERATURE -> overlayItems = overlayItems + OverlayItem.TextItem(weather.temperature)
                        WeatherInfo.ICON -> overlayItems = overlayItems + OverlayItem.ImageItem(weather.icon)
                        WeatherInfo.SUMMARY -> overlayItems = overlayItems + OverlayItem.TextItem(weather.summary)
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

        private fun calculateIconSize(size: Float): Int {
            // Get text metrics for the given size
            val textPaint =
                TextView(context)
                    .apply {
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
                        typeface = FontHelper.getTypeface(context, GeneralPrefs.fontTypeface, GeneralPrefs.messageWeight)
                    }.paint

            // Calculate approximate text height based on text metrics
            val textHeight = textPaint.fontMetrics.let { it.descent - it.ascent }

            // Use text height directly for icon size to maintain visual balance
            val iconSize = textHeight * 0.9f
            Timber.d("Text size: ${size}sp, Text height: $textHeight, Icon size: $iconSize")
            return iconSize.toInt()
        }

        private fun setupViews() {
            removeAllViews()

            val iconSize = calculateIconSize(size)

            overlayItems.forEach { item ->
                when (item) {
                    is OverlayItem.TextItem -> {
                        val textView =
                            TextView(context).apply {
                                text = item.text
                            }
                        TextViewCompat.setTextAppearance(textView, R.style.OverlayText)

                        val params = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
                        params.gravity = android.view.Gravity.CENTER_VERTICAL
                        if (isNotEmpty()) {
                            params.leftMargin = 10
                        }

                        textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
                        textView.typeface = FontHelper.getTypeface(context, GeneralPrefs.fontTypeface, GeneralPrefs.messageWeight)
                        textView.layoutParams = params

                        addView(textView)
                    }

                    is OverlayItem.ImageItem -> {
                        // Use our custom SvgImageView instead of regular ImageView
                        val imageView =
                            SvgImageView(context).apply {
                                setSvgResource(item.imageResId)
                            }

                        val params = LayoutParams(iconSize, iconSize)
                        params.gravity = android.view.Gravity.BOTTOM
                        if (isNotEmpty()) {
                            params.leftMargin = 10
                        }

                        imageView.layoutParams = params
                        imageView.scaleType = ImageView.ScaleType.FIT_CENTER
                        addView(imageView)
                    }
                }
            }
        }
    }
