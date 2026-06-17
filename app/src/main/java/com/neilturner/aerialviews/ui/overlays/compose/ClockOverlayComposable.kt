package com.neilturner.aerialviews.ui.overlays.compose

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.neilturner.aerialviews.models.enums.ClockType
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ClockOverlayComposable(
    format: ClockType?,
    modifier: Modifier = Modifier,
) {
    var currentTime by remember { mutableStateOf("") }

    val pattern = when (format) {
        ClockType.HOUR_24 -> "HH:mm"
        ClockType.HOUR_12 -> "h:mm a"
        else -> null
    }

    LaunchedEffect(pattern) {
        while (true) {
            currentTime = if (pattern != null) {
                SimpleDateFormat(pattern, Locale.getDefault()).format(Date())
            } else {
                SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            }
            delay(1000)
        }
    }

    Text(
        text = currentTime,
        modifier = modifier,
        style = OverlayTextStyle,
    )
}

val OverlayTextStyle = TextStyle(
    color = Color.White,
    fontSize = 18.sp,
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.Light,
    shadow = Shadow(
        color = Color.Black,
        offset = Offset(1f, 1f),
        blurRadius = 1f,
    ),
)
