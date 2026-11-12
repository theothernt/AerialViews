package com.neilturner.aerialviews.ui.overlays.compose

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.neilturner.aerialviews.services.weather.ForecastType
import com.neilturner.aerialviews.services.weather.WeatherEvent
import com.neilturner.aerialviews.ui.overlays.compose.components.OverlayIcon
import com.neilturner.aerialviews.ui.overlays.compose.components.OverlayText
import com.neilturner.aerialviews.ui.overlays.compose.components.overlayColour
import com.neilturner.aerialviews.ui.overlays.compose.components.overlayTextStyle
import com.neilturner.aerialviews.ui.overlays.state.WeatherOverlayState
import timber.log.Timber

private val itemGap = 16.dp
private const val ICON_SCALE = 1.3f

/**
 * Current weather line: the parts and order come from the weatherLine1Layout preference, exactly
 * as the View version reads it.
 */
@Composable
fun WeatherNowOverlay(
    state: WeatherOverlayState,
    layout: String,
    sizeSp: Float,
    weight: String,
) {
    val weather = state.event
    if (layout.isEmpty() || weather.temperature.isEmpty()) return

    val items = remember(layout, weather) { weatherItems(layout, weather) }
    val colour = overlayColour()
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val iconSize =
        with(density) {
            measurer
                .measure(AnnotatedString("Ag"), overlayTextStyle(sizeSp, weight))
                .size.height
                .toFloat()
                .toDp() *
                ICON_SCALE
        }

    Row(verticalAlignment = Alignment.CenterVertically) {
        items.forEachIndexed { index, item ->
            // No gap after an icon: the icon's own ink provides the visual spacing
            val previousIsIcon = index > 0 && items[index - 1] is WeatherItem.Icon

            when (item) {
                is WeatherItem.Text -> {
                    OverlayText(
                        text = item.text,
                        sizeSp = sizeSp,
                        weight = weight,
                        modifier = if (index > 0 && !previousIsIcon) Modifier.padding(start = itemGap) else Modifier,
                    )
                }

                is WeatherItem.Icon -> {
                    OverlayIcon(
                        resId = item.resId,
                        size = iconSize,
                        modifier = Modifier.align(Alignment.Bottom),
                        colour = colour,
                    )
                }
            }
        }
    }
}

private sealed interface WeatherItem {
    data class Text(
        val text: String,
    ) : WeatherItem

    data class Icon(
        @DrawableRes val resId: Int,
    ) : WeatherItem
}

private fun weatherItems(
    layout: String,
    weather: WeatherEvent,
): List<WeatherItem> =
    layout.split(",").mapNotNull { item ->
        try {
            when (ForecastType.valueOf(item.trim())) {
                ForecastType.CITY -> WeatherItem.Text(weather.city)
                ForecastType.TEMPERATURE -> WeatherItem.Text(weather.temperature)
                ForecastType.ICON -> WeatherItem.Icon(weather.icon)
                ForecastType.SUMMARY -> WeatherItem.Text(weather.summary)
                ForecastType.EMPTY -> null
            }
        } catch (e: IllegalArgumentException) {
            Timber.e("Invalid weather info item: ${item.trim()}")
            null
        }
    }
