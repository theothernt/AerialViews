package com.neilturner.aerialviews.ui.overlays

import android.content.Context
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
        private val cellSpacing = 48 // dp between forecast cells

        private var font = ""
        private var size = 0f
        private var weight = ""

        init {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
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
            val spacingPx = (cellSpacing * resources.displayMetrics.density).toInt()

            days.forEachIndexed { index, day ->
                val cell = createForecastCell(day, iconSize)
                addView(cell)

                if (index < days.size - 1) {
                    val spacer =
                        View(context).apply {
                            layoutParams = LayoutParams(spacingPx, 0)
                        }
                    addView(spacer)
                }
            }

            Timber.d("Forecast content updated: ${days.size} cells, iconSize=$iconSize, spacing=$spacingPx")
        }

        private fun createForecastCell(
            day: ForecastDay,
            iconSize: Int,
        ): LinearLayout {
            val cell = LinearLayout(context).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
            }

            if (day.icon > 0) {
                val iconView =
                    SvgImageView(context).apply {
                        setSvgResource(day.icon)
                    }
                val iconParams = LayoutParams(iconSize, iconSize)
                iconParams.gravity = Gravity.CENTER_VERTICAL
                iconView.layoutParams = iconParams
                iconView.scaleType = ImageView.ScaleType.FIT_CENTER
                cell.addView(iconView)
            }

            val tempText = "${day.tempHigh}/${day.tempLow}"
            val textView =
                TextView(context).apply {
                    text = tempText
                }
            TextViewCompat.setTextAppearance(textView, R.style.OverlayText)
            textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
            textView.typeface = FontHelper.getTypeface(context, GeneralPrefs.fontTypeface, weight)

            val textParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
            textParams.gravity = Gravity.CENTER_VERTICAL
            textParams.leftMargin = 8
            textView.layoutParams = textParams
            cell.addView(textView)

            return cell
        }

        private fun calculateIconSize(size: Float): Int {
            val textPaint =
                TextView(context)
                    .apply {
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
                        typeface = FontHelper.getTypeface(context, GeneralPrefs.fontTypeface, weight)
                    }.paint

            val textHeight = textPaint.fontMetrics.let { it.descent - it.ascent }
            val iconSize = textHeight * 1.3f
            return iconSize.toInt()
        }
    }
