package com.neilturner.aerialviews.ui.overlays.compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.ui.overlays.compose.components.OverlayText
import com.neilturner.aerialviews.ui.overlays.state.MessageOverlayState
import kotlinx.coroutines.delay

private const val MESSAGE_FADE_MILLIS = 300
private const val DEFAULT_MESSAGE_SIZE = 18

/**
 * Message API overlay. The auto-clear timer belongs to the message itself, so a new message from
 * the API restarts it instead of inheriting the previous one's remaining time.
 */
@Composable
fun MessageOverlay(state: MessageOverlayState) {
    val sizeSp = (state.textSize ?: GeneralPrefs.messageSize.toIntOrNull() ?: DEFAULT_MESSAGE_SIZE).toFloat()
    val weight = state.textWeight?.toString() ?: GeneralPrefs.messageWeight

    var text by remember(state) { mutableStateOf(state.text) }

    LaunchedEffect(state) {
        val duration = state.duration ?: 0
        if (state.text.isNotBlank() && duration > 0) {
            delay(duration * 1_000L)
            text = ""
        }
    }

    AnimatedVisibility(
        visible = text.isNotBlank(),
        enter = fadeIn(tween(durationMillis = MESSAGE_FADE_MILLIS)),
        exit = fadeOut(tween(durationMillis = MESSAGE_FADE_MILLIS)),
    ) {
        OverlayText(
            text = text,
            sizeSp = sizeSp,
            weight = weight,
        )
    }
}
