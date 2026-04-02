package com.neilturner.aerialviews.ui.overlays

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.widget.TextViewCompat
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.models.enums.OverlayType
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.services.weather.ForecastDay
import com.neilturner.aerialviews.ui.overlays.state.ForecastOverlayState
import com.neilturner.aerialviews.utils.FontHelper
import timber.log.Timber

class WeatherForecastOverlay
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0,
    ) : LinearLayout(context, attrs, defStyleAttr) {
        var type = OverlayType.WEATHER_FORECAST
        private var previousDays: List<ForecastDay>? = null
        private val fadeAnimationDuration = 300L
        private val minVisibleAlphaForFade = 0.95f
        private val cellSpacing = 24 // dp between day columns

        private var font = ""
        private var size = 0f
        private var weight = ""

        init {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER
            alpha = 1f
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

        fun render(state: ForecastOverlayState) {
            val days = state.event.days
            if (days.isEmpty()) {
                Timber.d("Forecast render: no days data yet")
                return
            }
            Timber.d("Forecast render: ${days.size} days")

            if (previousDays == days) {
                Timber.d("Forecast data unchanged, skipping UI update")
                return
            }

            val allowFadeAnimation = alpha >= minVisibleAlphaForFade

            if (previousDays == null) {
                previousDays = days
                updateForecastContent(days)
                if (allowFadeAnimation) {
                    animate().alpha(1f).setDuration(fadeAnimationDuration).start()
                }
                return
            }

            previousDays = days

            if (!allowFadeAnimation) {
                updateForecastContent(days)
                return
            }

            animate()
                .alpha(0f)
                .setDuration(fadeAnimationDuration)
                .withEndAction {
                    updateForecastContent(days)
                    animate().alpha(1f).setDuration(fadeAnimationDuration).start()
                }.start()
        }

        private fun updateForecastContent(days: List<ForecastDay>) {
            removeAllViews()

            val iconSize = calculateIconSize(size)
            val density = resources.displayMetrics.density
            val spacingPx = (cellSpacing * density).toInt()

            days.forEachIndexed { index, day ->
                val column = createDayColumn(day, iconSize)
                addView(column)

                if (index < days.size - 1) {
                    val spacer =
                        View(context).apply {
                            layoutParams = LayoutParams(spacingPx, 0)
                        }
                    addView(spacer)
                }
            }

            Timber.d("Forecast content updated: ${days.size} columns, iconSize=$iconSize")
        }

        private fun createDayColumn(
            day: ForecastDay,
            iconSize: Int,
        ): LinearLayout {
            val column =
                LinearLayout(context).apply {
                    orientation = VERTICAL
                    gravity = Gravity.CENTER_HORIZONTAL
                    layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
                }

            val dayLabel =
                TextView(context).apply {
                    text = day.dayName
                    gravity = Gravity.CENTER
                }
            TextViewCompat.setTextAppearance(dayLabel, R.style.OverlayText)
            dayLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, size * 0.7f)
            dayLabel.typeface = FontHelper.getTypeface(context, GeneralPrefs.fontTypeface, weight)
            dayLabel.setTextColor(Color.argb(200, 255, 255, 255))
            val labelParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
            labelParams.bottomMargin = 4
            dayLabel.layoutParams = labelParams
            column.addView(dayLabel)

            if (day.icon > 0) {
                val iconView =
                    SvgImageView(context).apply {
                        setSvgResource(day.icon)
                    }
                val iconParams = LayoutParams(iconSize, iconSize)
                iconParams.gravity = Gravity.CENTER_HORIZONTAL
                iconParams.bottomMargin = 4
                iconView.layoutParams = iconParams
                iconView.scaleType = ImageView.ScaleType.FIT_CENTER
                column.addView(iconView)
            }

            val tempContainer =
                LinearLayout(context).apply {
                    orientation = HORIZONTAL
                    gravity = Gravity.CENTER
                    layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
                }

            val highTemp =
                TextView(context).apply {
                    text = day.tempHigh
                    gravity = Gravity.CENTER
                }
            TextViewCompat.setTextAppearance(highTemp, R.style.OverlayText)
            highTemp.setTextSize(TypedValue.COMPLEX_UNIT_SP, size * 0.8f)
            highTemp.typeface = FontHelper.getTypeface(context, GeneralPrefs.fontTypeface, weight)
            highTemp.setTextColor(Color.argb(230, 255, 255, 255))
            tempContainer.addView(highTemp)

            val separator =
                TextView(context).apply {
                    text = " "
                    gravity = Gravity.CENTER
                }
            tempContainer.addView(separator)

            val lowTemp =
                TextView(context).apply {
                    text = day.tempLow
                    gravity = Gravity.CENTER
                }
            TextViewCompat.setTextAppearance(lowTemp, R.style.OverlayText)
            lowTemp.setTextSize(TypedValue.COMPLEX_UNIT_SP, size * 0.8f)
            lowTemp.typeface = FontHelper.getTypeface(context, GeneralPrefs.fontTypeface, weight)
            lowTemp.setTextColor(Color.argb(140, 255, 255, 255))
            tempContainer.addView(lowTemp)

            column.addView(tempContainer)

            return column
        }

        private fun calculateIconSize(size: Float): Int {
            val textPaint =
                TextView(context)
                    .apply {
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
                        typeface = FontHelper.getTypeface(context, GeneralPrefs.fontTypeface, weight)
                    }.paint

            val textHeight = textPaint.fontMetrics.let { it.descent - it.ascent }
            val iconSize = textHeight * 1.1f
            return iconSize.toInt()
        }
    }
