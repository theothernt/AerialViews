package com.neilturner.aerialviews.ui.overlays.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.ui.overlays.compose.components.OverlayText
import com.neilturner.aerialviews.ui.overlays.state.MessageOverlayState
import kotlinx.coroutines.delay

internal const val MESSAGE_FADE_MILLIS = 300
private const val DEFAULT_MESSAGE_SIZE = 18

/**
 * Message API overlay. The auto-clear timer belongs to the message itself, so a new message from
 * the API restarts it instead of inheriting the previous one's remaining time.
 *
 * `message_animate_changes` selects the same two modes the View has: off shows and hides the line
 * outright, on fades it out, swaps the message while invisible and fades it back in.
 */
@Composable
fun MessageOverlay(
    state: MessageOverlayState,
    fade: OverlayFade = remember { OverlayFade(MESSAGE_FADE_MILLIS) },
) {
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

    if (!GeneralPrefs.messageAnimateChanges) {
        if (text.isNotBlank()) {
            OverlayText(
                text = text,
                sizeSp = sizeSp,
                weight = weight,
            )
        }
        return
    }

    // What is on screen, which trails [text] by the length of a fade.
    var displayed by remember { mutableStateOf("") }

    LaunchedEffect(text) {
        if (displayed == text) return@LaunchedEffect
        // Nothing is showing yet, so the View snaps to alpha 0 and only fades in; there is no line
        // to fade out first.
        if (displayed.isNotBlank()) fade.fadeOut() else fade.snapHidden()
        displayed = text
        if (text.isNotBlank()) fade.fadeIn()
    }

    if (displayed.isNotBlank()) {
        Box(modifier = Modifier.alpha(fade.alpha.value)) {
            OverlayText(
                text = displayed,
                sizeSp = sizeSp,
                weight = weight,
            )
        }
    }
}
