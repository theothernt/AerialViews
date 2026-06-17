package com.neilturner.aerialviews.ui.overlays.compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neilturner.aerialviews.services.weather.ForecastDay
import com.neilturner.aerialviews.ui.overlays.state.ForecastOverlayState

@Composable
fun WeatherForecastOverlayComposable(
    state: ForecastOverlayState,
    modifier: Modifier = Modifier,
) {
    val days = state.event.days
    val isVisible = days.isNotEmpty()

    AnimatedVisibility(
        visible = isVisible,
        modifier = modifier,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            days.forEach { day ->
                ForecastDayColumn(day = day)
            }
        }
    }
}

@Composable
private fun ForecastDayColumn(day: ForecastDay) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Day name
        Text(
            text = day.dayName,
            style = OverlayTextStyle.copy(
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.78f),
            ),
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Weather icon
        if (day.icon > 0) {
            WeatherIconComposable(
                resourceId = day.icon,
                modifier = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.height(4.dp))
        }

        // Temperature row
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = day.tempHigh,
                style = OverlayTextStyle.copy(
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.9f),
                ),
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.width(4.dp))

            Text(
                text = day.tempLow,
                style = OverlayTextStyle.copy(
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.55f),
                ),
                textAlign = TextAlign.Center,
            )
        }
    }
}
