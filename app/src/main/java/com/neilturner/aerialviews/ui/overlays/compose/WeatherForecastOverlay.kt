package com.neilturner.aerialviews.ui.overlays.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.neilturner.aerialviews.ui.overlays.compose.components.OverlayIcon
import com.neilturner.aerialviews.ui.overlays.compose.components.OverlayText
import com.neilturner.aerialviews.ui.overlays.compose.components.overlayColour
import com.neilturner.aerialviews.ui.overlays.compose.components.overlayTextStyle
import com.neilturner.aerialviews.ui.overlays.state.ForecastOverlayState

private val cellSpacing = 24.dp
private val elementMargin = 4.dp

private const val ICON_SCALE = 1.1f
private const val LABEL_SIZE_RATIO = 0.8f
private const val TEMP_SIZE_RATIO = 0.8f

/** Per-element dimming, the same alphas the View version applies on top of the overlay colour. */
private const val LABEL_ALPHA = 200
private const val HIGH_TEMP_ALPHA = 230
private const val LOW_TEMP_ALPHA = 140

/**
 * Multi-day forecast: a column per day with the day name, icon and high/low temperatures.
 *
 * The number of days comes from the forecast service, which already limits the result to the
 * weatherLine2Days preference.
 */
@Composable
fun WeatherForecastOverlay(
    state: ForecastOverlayState,
    sizeSp: Float,
    weight: String,
) {
    val days = state.event.days
    if (days.isEmpty()) return

    val colour = overlayColour()
    val labelColour = colour.copy(alpha = LABEL_ALPHA / 255f)
    val highColour = colour.copy(alpha = HIGH_TEMP_ALPHA / 255f)
    val lowColour = colour.copy(alpha = LOW_TEMP_ALPHA / 255f)

    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val iconSize =
        with(density) {
            measurer
                .measure(
                    AnnotatedString("Ag"),
                    overlayTextStyle(sizeSp * TEMP_SIZE_RATIO, weight),
                ).size.height
                .toFloat()
                .toDp() * ICON_SCALE
        }

    Row(verticalAlignment = Alignment.CenterVertically) {
        days.forEachIndexed { index, day ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                OverlayText(
                    text = day.dayName,
                    sizeSp = sizeSp * LABEL_SIZE_RATIO,
                    weight = weight,
                    modifier = Modifier.padding(bottom = elementMargin),
                    colour = labelColour,
                )

                if (day.icon > 0) {
                    OverlayIcon(
                        resId = day.icon,
                        size = iconSize,
                        modifier = Modifier.padding(bottom = elementMargin),
                        colour = colour,
                    )
                }

                Row {
                    OverlayText(
                        text = day.tempHigh,
                        sizeSp = sizeSp * TEMP_SIZE_RATIO,
                        weight = weight,
                        colour = highColour,
                    )
                    Text(text = " ")
                    OverlayText(
                        text = day.tempLow,
                        sizeSp = sizeSp * TEMP_SIZE_RATIO,
                        weight = weight,
                        colour = lowColour,
                    )
                }
            }

            if (index < days.size - 1) {
                Spacer(Modifier.width(cellSpacing))
            }
        }
    }
}
