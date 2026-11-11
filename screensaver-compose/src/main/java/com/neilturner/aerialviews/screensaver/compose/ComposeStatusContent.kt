@file:Suppress("ktlint:standard:function-naming")

package com.neilturner.aerialviews.screensaver.compose

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neilturner.aerialviews.models.LoadingStatus
import com.neilturner.aerialviews.ui.core.ScreensaverError
import com.neilturner.aerialviews.ui.core.ScreensaverPhase
import com.neilturner.aerialviews.ui.core.ScreensaverState

/**
 * The legacy loading view's status content. It sits inside the black layer that fades with the
 * media, so an empty playlist or a provider failure remains readable instead of leaving a blank
 * screen.
 */
@Composable
internal fun ComposeStatusContent(
    state: ScreensaverState,
    loadingStatus: LoadingStatus,
) {
    if (state.blackout.active) return

    val context = LocalContext.current
    val error = state.error
    val text =
        when {
            error == ScreensaverError.EMPTY_PLAYLIST ||
                error == ScreensaverError.ITEM_UNAVAILABLE -> {
                context.getString(R.string.loading_error)
            }

            error == ScreensaverError.PLAYBACK_ERROR -> {
                context.getString(R.string.playback_error)
            }

            state.phase == ScreensaverPhase.IDLE || state.phase == ScreensaverPhase.LOADING -> {
                when (loadingStatus) {
                    LoadingStatus.RESUMING -> context.getString(R.string.loading_resuming)
                    LoadingStatus.BUILDING -> context.getString(R.string.loading_building)
                    LoadingStatus.LOADING -> context.getString(R.string.loading_title)
                }
            }

            else -> {
                return
            }
        }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (error == null) {
            LoadingSpinner()
        }
        BasicText(
            text = text,
            style =
                TextStyle(
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Light,
                ),
        )
    }
}

@Composable
private fun LoadingSpinner() {
    val transition = rememberInfiniteTransition()
    val angle by
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(durationMillis = 900, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart,
                ),
        )
    Canvas(modifier = Modifier.size(28.dp)) {
        drawArc(
            color = Color.White.copy(alpha = 0.8f),
            startAngle = angle,
            sweepAngle = 280f,
            useCenter = false,
            topLeft = androidx.compose.ui.geometry.Offset.Zero,
            size = Size(size.width, size.height),
            style = Stroke(width = 3.dp.toPx()),
        )
    }
}
