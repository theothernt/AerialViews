package com.neilturner.aerialviews.ui.overlays.compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.services.weather.ForecastType
import com.neilturner.aerialviews.services.weather.WeatherEvent
import com.neilturner.aerialviews.ui.helpers.FontHelper
import com.neilturner.aerialviews.ui.overlays.state.WeatherOverlayState

@Composable
fun WeatherNowOverlayComposable(
    state: WeatherOverlayState,
    layout: String,
    modifier: Modifier = Modifier,
) {
    val weather = state.event
    val isVisible = weather.temperature.isNotBlank() && layout.isNotBlank()

    AnimatedVisibility(
        visible = isVisible,
        modifier = modifier,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        WeatherNowContent(weather = weather, layout = layout)
    }
}

@Composable
private fun WeatherNowContent(
    weather: WeatherEvent,
    layout: String,
) {
    val items = remember(layout, weather) {
        layout.split(",").mapNotNull { item ->
            val trimmedItem = item.trim()
            try {
                val forecastType = ForecastType.valueOf(trimmedItem)
                when (forecastType) {
                    ForecastType.CITY -> WeatherItem.Text(weather.city)
                    ForecastType.TEMPERATURE -> WeatherItem.Text(weather.temperature)
                    ForecastType.ICON -> WeatherItem.Icon(weather.icon)
                    ForecastType.SUMMARY -> WeatherItem.Text(weather.summary)
                    ForecastType.EMPTY -> null
                }
            } catch (e: IllegalArgumentException) {
                null
            }
        }
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEachIndexed { index, item ->
            when (item) {
                is WeatherItem.Text -> {
                    Text(
                        text = item.text,
                        style = OverlayTextStyle,
                    )
                }
                is WeatherItem.Icon -> {
                    if (item.resourceId > 0) {
                        WeatherIconComposable(
                            resourceId = item.resourceId,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
            }
        }
    }
}

private sealed class WeatherItem {
    data class Text(val text: String) : WeatherItem()
    data class Icon(val resourceId: Int) : WeatherItem()
}
