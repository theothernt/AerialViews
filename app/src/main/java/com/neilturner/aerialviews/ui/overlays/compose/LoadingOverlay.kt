package com.neilturner.aerialviews.ui.overlays.compose

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.ui.overlays.compose.OverlayTextStyle

@Composable
fun LoadingOverlay(
    visible: Boolean,
    text: String,
    showSpinner: Boolean,
    modifier: Modifier = Modifier,
) {
    val showText = GeneralPrefs.showLoadingText
    val fadeInDuration = GeneralPrefs.mediaFadeInDuration.toLongOrNull() ?: 600L
    val fadeOutDuration = GeneralPrefs.mediaFadeOutDuration.toLongOrNull() ?: 800L

    var targetAlpha by remember { mutableFloatStateOf(1f) }

    LaunchedEffect(visible) {
        targetAlpha = if (visible) 1f else 0f
    }

    val alpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = tween(
            durationMillis = if (targetAlpha > 0f) fadeOutDuration.toInt() else fadeInDuration.toInt(),
        ),
        label = "loadingAlpha",
    )

    // Always render, control visibility via alpha
    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer { this.alpha = alpha }
            .background(Color.Black),
    ) {
        if (showText || showSpinner) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 32.dp, bottom = 26.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (showSpinner) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Color.White,
                        strokeWidth = 2.dp,
                    )
                }

                if (showText && text.isNotBlank()) {
                    Text(
                        text = text,
                        style = OverlayTextStyle.copy(
                            fontSize = GeneralPrefs.loadingTextSize.toFloat().sp,
                        ),
                    )
                }
            }
        }
    }
}
