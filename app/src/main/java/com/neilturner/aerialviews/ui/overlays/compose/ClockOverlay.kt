package com.neilturner.aerialviews.ui.overlays.compose

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.ui.overlays.compose.components.OverlayText
import com.neilturner.aerialviews.ui.overlays.utils.ClockFormatter
import kotlinx.coroutines.delay
import java.time.LocalDateTime

/**
 * The clock overlay. The ticker wakes on the minute boundary rather than every second, which is
 * all the display ever needs.
 */
@Composable
fun ClockOverlay(
    sizeSp: Float,
    weight: String,
) {
    val context = LocalContext.current
    val pattern =
        remember(context) {
            ClockFormatter.pattern(GeneralPrefs.clockFormat, DateFormat.is24HourFormat(context))
        }

    var time by remember { mutableStateOf(LocalDateTime.now()) }

    LaunchedEffect(pattern) {
        while (true) {
            time = LocalDateTime.now()
            delay(ClockFormatter.millisToNextMinute(time))
        }
    }

    OverlayText(
        text = ClockFormatter.format(time, pattern),
        sizeSp = sizeSp,
        weight = weight,
    )
}
