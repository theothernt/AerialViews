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
import com.neilturner.aerialviews.services.weather.ForecastType
import com.neilturner.aerialviews.services.weather.WeatherEvent
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
        private var previousWeather: WeatherEvent? = null
        private val fadeAnimationDuration = 300L

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
            alpha = 0f
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
            this.layout = layout
        }

        override fun onAttachedToWindow() {
            super.onAttachedToWindow()
            receiver.subscribe { weather: WeatherEvent ->
                Timber.d("$weather")
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

            // Check if the new weather data is the same as the previous data
            if (previousWeather == weather) {
                Timber.d("Weather data unchanged, skipping UI update")
                return
            }

            // If this is the first weather update, fade in directly
            if (previousWeather == null) {
                previousWeather = weather
                updateOverlayContent(weather)
                animate()
                    .alpha(1f)
                    .setDuration(fadeAnimationDuration)
                    .start()
                return
            }

            // Store current weather for next comparison
            previousWeather = weather

            // Fade out
            animate()
                .alpha(0f)
                .setDuration(fadeAnimationDuration)
                .withEndAction {
                    // Update content when fade out is complete
                    updateOverlayContent(weather)

                    // Fade back in
                    animate()
                        .alpha(1f)
                        .setDuration(fadeAnimationDuration)
                        .start()
                }.start()
        }

        private fun updateOverlayContent(weather: WeatherEvent) {
            overlayItems = emptyList()

            layout.split(",").forEach { item ->
                val trimmedItem = item.trim()
                try {
                    val forecastType = ForecastType.valueOf(trimmedItem)
                    when (forecastType) {
                        ForecastType.CITY -> overlayItems = overlayItems + OverlayItem.TextItem(weather.city)
                        ForecastType.TEMPERATURE -> overlayItems = overlayItems + OverlayItem.TextItem(weather.temperature)
                        ForecastType.ICON -> overlayItems = overlayItems + OverlayItem.ImageItem(weather.icon)
                        ForecastType.SUMMARY -> overlayItems = overlayItems + OverlayItem.TextItem(weather.summary)
                        ForecastType.EMPTY -> { /* Do nothing */ }
                    }
                } catch (e: IllegalArgumentException) {
                    Timber.e("Invalid weather info item: $trimmedItem")
                }
            }

            setupViews()
        }

        private fun setupViews() {
            removeAllViews()

            val iconSize = calculateIconSize(size)
            val itemMargin = 16

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

                        // Check if this item should have a margin
                        // No margin if previous item is an image or if this is the first item
                        val previousItemIsImage =
                            overlayItems.indexOf(item) > 0 &&
                                overlayItems[overlayItems.indexOf(item) - 1] is OverlayItem.ImageItem

                        if (isNotEmpty() && !previousItemIsImage) {
                            Timber.d("Adding margin to text view")
                            params.leftMargin = itemMargin
                        } else {
                            Timber.d("No margin needed for text view")
                        }

                        textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
                        textView.typeface = FontHelper.getTypeface(context, GeneralPrefs.fontTypeface, weight)
                        textView.layoutParams = params

                        Timber.d("Adding text view with text: ${item.text}")
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

                        imageView.layoutParams = params
                        imageView.scaleType = ImageView.ScaleType.FIT_CENTER

                        Timber.d("Adding image view with resource ID: ${item.imageResId}")
                        addView(imageView)
                    }
                }
            }
        }

        private fun calculateIconSize(size: Float): Int {
            // Get text metrics for the given size
            val textPaint =
                TextView(context)
                    .apply {
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
                        typeface = FontHelper.getTypeface(context, GeneralPrefs.fontTypeface, weight)
                    }.paint

            // Calculate approximate text height based on text metrics
            val textHeight = textPaint.fontMetrics.let { it.descent - it.ascent }

            // Use text height directly for icon size to maintain visual balance
            val iconSize = textHeight * 1.5f
            Timber.d("Text size: ${size}sp, Text height: $textHeight, Icon size: $iconSize")
            return iconSize.toInt()
        }
    }
