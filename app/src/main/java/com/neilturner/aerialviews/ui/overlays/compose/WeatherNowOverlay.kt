package com.neilturner.aerialviews.ui.overlays.compose

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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

private val itemMarginPx = 16f
private const val ICON_SCALE = 1.3f

/** The View version pads the text 2dp at the top (and -2dp at the bottom) to give the degree
 * symbol headroom when font padding is off. Compose rejects negative padding, so the extra shift
 * is an offset, the same as the forecast overlay. */
private val superscriptExtra = 2.dp

/** The View version fades the line out, swaps the weather and fades it back in over 300ms. */
internal const val WEATHER_FADE_MILLIS = 300

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
    fade: OverlayFade = remember { OverlayFade(WEATHER_FADE_MILLIS) },
) {
    val weather = state.event
    var displayed by remember { mutableStateOf(weather) }

    // The View fades out, swaps the content while it is invisible, then fades back in. Nothing on
    // screen snaps straight to the new content instead of fading out an empty line first.
    LaunchedEffect(layout, weather) {
        if (displayed == weather) return@LaunchedEffect
        if (hasWeather(layout, displayed)) fade.fadeOut() else fade.snapHidden()
        displayed = weather
        if (hasWeather(layout, weather)) fade.fadeIn()
    }

    if (!hasWeather(layout, displayed)) return

    val items = remember(layout, displayed) { weatherItems(layout, displayed) }
    val colour = overlayColour()
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val itemGap = with(density) { itemMarginPx.toDp() }
    val iconSize =
        with(density) {
            measurer
                .measure(AnnotatedString("Ag"), overlayTextStyle(sizeSp, weight))
                .size.height
                .toFloat()
                .toDp() *
                ICON_SCALE
        }

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.alpha(fade.alpha.value)) {
        items.forEachIndexed { index, item ->
            // No gap after an icon: the icon's own ink provides the visual spacing
            val previousIsIcon = index > 0 && items[index - 1] is WeatherItem.Icon

            when (item) {
                is WeatherItem.Text -> {
                    OverlayText(
                        text = item.text,
                        sizeSp = sizeSp,
                        weight = weight,
                        modifier =
                            Modifier
                                .then(if (index > 0 && !previousIsIcon) Modifier.padding(start = itemGap) else Modifier)
                                .offset(y = superscriptExtra),
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

private fun hasWeather(
    layout: String,
    weather: WeatherEvent,
): Boolean = layout.isNotEmpty() && weather.temperature.isNotEmpty()

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
