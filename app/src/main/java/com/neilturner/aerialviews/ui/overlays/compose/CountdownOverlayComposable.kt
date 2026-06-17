package com.neilturner.aerialviews.ui.overlays.compose

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.ui.overlays.utils.CountdownTimeParser
import kotlinx.coroutines.delay
import timber.log.Timber
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

@Composable
fun CountdownOverlayComposable(
    modifier: Modifier = Modifier,
) {
    val targetTimeStr = GeneralPrefs.countdownTargetTime
    val targetMessage = GeneralPrefs.countdownTargetMessage.ifEmpty { "Time's up!" }

    var displayText by remember { mutableStateOf("") }
    var isCompleted by remember { mutableStateOf(false) }

    LaunchedEffect(targetTimeStr) {
        if (targetTimeStr.isEmpty()) {
            displayText = ""
            return@LaunchedEffect
        }

        val targetDateTime = CountdownTimeParser.parseTargetTime(targetTimeStr, LocalDateTime.now())
        if (targetDateTime == null) {
            displayText = "Invalid time format"
            return@LaunchedEffect
        }

        while (true) {
            val now = LocalDateTime.now()
            val totalSeconds = ChronoUnit.SECONDS.between(now, targetDateTime)

            if (totalSeconds <= 0) {
                displayText = targetMessage
                isCompleted = true
                break
            }

            displayText = formatCountdown(totalSeconds)
            delay(1000)
        }
    }

    if (displayText.isNotBlank()) {
        Text(
            text = displayText,
            modifier = modifier,
            style = OverlayTextStyle,
        )
    }
}

private fun formatCountdown(totalSeconds: Long): String {
    val days = totalSeconds / 86400
    val hours = (totalSeconds % 86400) / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60

    return when {
        days > 0 -> "${days}d ${hours}h ${minutes}m"
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m ${seconds}s"
        else -> "${seconds}s"
    }
}
