package com.neilturner.aerialviews.ui.overlays.compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.neilturner.aerialviews.models.enums.ProgressBarLocation
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.ui.controls.ProgressState
import com.neilturner.aerialviews.ui.overlays.state.ProgressOverlayState
import kotlinx.coroutines.delay

@Composable
fun ProgressBarComposable(
    state: ProgressOverlayState,
    modifier: Modifier = Modifier,
) {
    val location = GeneralPrefs.progressBarLocation
    val opacity = GeneralPrefs.progressBarOpacity.toFloat() / 100f

    if (location == ProgressBarLocation.DISABLED) return

    var progress by remember { mutableFloatStateOf(0f) }

    // Animate progress based on state
    LaunchedEffect(state) {
        when (state.state) {
            ProgressState.START -> {
                if (state.duration > 0) {
                    val startFraction = state.position.toFloat() / state.duration.toFloat()
                    progress = startFraction.coerceIn(0f, 1f)

                    // Animate from current position to end
                    val remainingMs = state.duration - state.position
                    val steps = 50
                    val stepDelay = remainingMs / steps
                    val stepIncrement = (1f - startFraction) / steps

                    repeat(steps) {
                        delay(stepDelay)
                        progress = (progress + stepIncrement).coerceIn(0f, 1f)
                    }
                    progress = 1f
                }
            }
            ProgressState.PAUSE -> {
                // Stay at current position
            }
            ProgressState.RESUME -> {
                // Continue from current position (handled by next START)
            }
            ProgressState.RESET -> {
                progress = 0f
            }
        }
    }

    val isVisible = location != ProgressBarLocation.DISABLED

    AnimatedVisibility(
        visible = isVisible,
        modifier = modifier,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Color.White.copy(alpha = opacity)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .height(1.dp)
                    .background(Color.White.copy(alpha = opacity)),
            )
        }
    }
}
