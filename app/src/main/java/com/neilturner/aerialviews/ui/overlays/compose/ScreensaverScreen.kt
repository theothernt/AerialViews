package com.neilturner.aerialviews.ui.overlays.compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import com.neilturner.aerialviews.models.enums.ProgressBarLocation
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.ui.core.ImagePlayerView
import com.neilturner.aerialviews.ui.core.VideoPlayerView
import com.neilturner.aerialviews.ui.overlays.state.OverlayStateStore
import kotlin.math.pow

@Composable
fun ScreensaverScreen(
    overlayStateStore: OverlayStateStore,
    videoPlayer: VideoPlayerView,
    imagePlayer: ImagePlayerView,
    loadingVisible: Boolean = true,
    loadingText: String = "",
    loadingSpinnerVisible: Boolean = false,
    onSkipNext: () -> Unit = {},
    onSkipPrevious: () -> Unit = {},
    onPause: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val state by overlayStateStore.uiState.collectAsState()
    val progressBarLocation = GeneralPrefs.progressBarLocation

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        // Video player layer
        AndroidView(
            factory = { videoPlayer },
            modifier = Modifier.fillMaxSize(),
        )

        // Image player layer
        AndroidView(
            factory = { imagePlayer },
            modifier = Modifier.fillMaxSize(),
        )

        // Overlay layer
        OverlayLayout(
            state = state,
            modifier = Modifier.fillMaxSize(),
        )

        // Loading overlay - on top of everything
        LoadingOverlay(
            visible = loadingVisible,
            text = loadingText,
            showSpinner = loadingSpinnerVisible,
        )

        // Progress bar
        if (progressBarLocation != ProgressBarLocation.DISABLED) {
            val alignment = if (progressBarLocation == ProgressBarLocation.TOP) {
                Alignment.TopCenter
            } else {
                Alignment.BottomCenter
            }

            ProgressBarComposable(
                state = state.progress,
                modifier = Modifier.align(alignment),
            )
        }
    }
}

@Composable
fun GradientTop(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = easeInGradientColors(),
                ),
            ),
    )
}

@Composable
fun GradientBottom(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = easeInGradientColors().reversed(),
                ),
            ),
    )
}

private fun easeInGradientColors(): List<Color> {
    // Cubic ease-in equation matching GradientHelper.smoothBackgroundAlt
    val equation: (Double) -> Double = { x ->
        if (x < 0.5) 4 * x * x * x else 1 - (-2 * x + 2).pow(3.0) / 2
    }

    val colors = mutableListOf<Color>()
    val min = 0.3
    val max = 1.0
    val steps = 20.0

    var i = min
    while (i <= max) {
        val alpha = (1 - equation(i)).toFloat()
        colors.add(Color(0f, 0f, 0f, alpha))
        i += max / steps
    }

    return colors
}
