package com.neilturner.aerialviews.ui.overlays.compose

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.neilturner.aerialviews.models.enums.ProgressBarLocation
import com.neilturner.aerialviews.ui.overlays.state.ProgressOverlayState

/** Height of the bar, matching aerial_activity.xml's 1dp ProgressBar. */
private val barHeight = 1.dp

/**
 * Playback progress along the top or bottom edge: the Compose counterpart of the XML ProgressBar
 * View, which grows a 1dp View's width with a ValueAnimator.
 *
 * [location] and [opacity] come from `progressBarLocation` and `progressBarOpacity`; the caller
 * positions the bar through [modifier]. The bar deliberately ignores the overlay fade — it tracks
 * playback and keeps the user's opacity while overlays hide and reveal.
 */
@Composable
fun ProgressBarOverlay(
    state: ProgressOverlayState,
    location: ProgressBarLocation?,
    opacity: Float,
    modifier: Modifier = Modifier,
) {
    if (location == null || location == ProgressBarLocation.DISABLED) return

    val fraction = remember { Animatable(0f) }

    // The last START's duration: resuming after a pause runs for the time that is left.
    var durationMillis by remember { mutableLongStateOf(0L) }

    val motion =
        remember(state.state, state.position, state.duration) {
            progressBarMotion(state.state, state.position, state.duration)
        }

    LaunchedEffect(motion) {
        when (motion) {
            is ProgressBarMotion.Animate -> {
                durationMillis = motion.durationMillis
                fraction.snapTo(motion.from)
                fraction.animateTo(
                    targetValue = motion.to,
                    animationSpec = tween(durationMillis = motion.durationMillis.animationMillis(), easing = LinearEasing),
                )
            }

            // The new effect cancels the running animation and the Animatable keeps the value it
            // had reached, which is what pausing the View's ValueAnimator leaves behind.
            ProgressBarMotion.Pause -> {
                // Nothing to do: cancelling the effect is the pause.
            }

            ProgressBarMotion.Resume -> {
                val remaining = ((1f - fraction.value) * durationMillis).toLong()
                fraction.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = remaining.animationMillis(), easing = LinearEasing),
                )
            }

            ProgressBarMotion.Reset -> {
                durationMillis = 0L
                fraction.snapTo(0f)
            }
        }
    }

    Box(modifier = modifier.height(barHeight).alpha(opacity)) {
        Box(
            modifier =
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction.value)
                    .background(Color.White),
        )
    }
}

/** Durations only ever reach `tween` as an Int, so keep an oversized item from wrapping. */
private fun Long.animationMillis(): Int = coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
