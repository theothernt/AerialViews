package com.neilturner.aerialviews.ui.overlays.compose

import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.ui.overlays.compose.components.OverlayText
import com.neilturner.aerialviews.ui.overlays.utils.CountdownFormatter
import com.neilturner.aerialviews.ui.overlays.utils.CountdownTimeParser
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/**
 * Countdown to a time from the preferences. Ticking stops at zero, leaving the "time is up"
 * message on screen for the rest of the session, as the View version does.
 */
@Composable
fun CountdownOverlay(
    sizeSp: Float,
    weight: String,
) {
    val resources = LocalContext.current.resources
    val targetTime = GeneralPrefs.countdownTargetTime
    val label = GeneralPrefs.countdownLabel
    val targetMessage =
        GeneralPrefs.countdownTargetMessage.ifEmpty {
            resources.getString(R.string.countdown_time_is_up)
        }

    val text by
        produceState(
            initialValue = "",
            targetTime,
            label,
            targetMessage,
        ) {
            if (targetTime.isEmpty()) return@produceState

            val target = CountdownTimeParser.parseTargetTime(targetTime, LocalDateTime.now())
            if (target == null) {
                value = resources.getString(R.string.countdown_invalid_time_format)
                return@produceState
            }

            while (true) {
                val remaining =
                    try {
                        ChronoUnit.SECONDS.between(LocalDateTime.now(), target)
                    } catch (e: Exception) {
                        value = resources.getString(R.string.countdown_error)
                        return@produceState
                    }

                if (remaining <= 0) {
                    value = targetMessage
                    return@produceState
                }

                value = formatCountdown(resources, remaining, label)
                delay(COUNTDOWN_TICK_MILLIS)
            }
        }

    if (text.isBlank()) return

    OverlayText(
        text = text,
        sizeSp = sizeSp,
        weight = weight,
    )
}

private const val COUNTDOWN_TICK_MILLIS = 1000L

private fun formatCountdown(
    resources: Resources,
    totalSeconds: Long,
    label: String,
): String {
    val separator = resources.getString(R.string.countdown_unit_separator)
    val countdown =
        CountdownFormatter.parts(totalSeconds).joinToString(separator) { part ->
            resources.getQuantityString(part.unit.pluralsRes, part.value.toInt(), part.value)
        }

    return listOfNotNull(
        label.takeIf { it.isNotBlank() },
        countdown.takeIf { it.isNotBlank() },
    ).joinToString(" ")
}
