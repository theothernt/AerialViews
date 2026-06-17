package com.neilturner.aerialviews.ui.overlays.compose

import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import com.neilturner.aerialviews.ui.core.ImagePlayerView
import com.neilturner.aerialviews.ui.core.VideoPlayerView
import com.neilturner.aerialviews.ui.overlays.state.OverlayStateStore

@Composable
fun ScreensaverScreen(
    overlayStateStore: OverlayStateStore,
    videoPlayer: VideoPlayerView,
    imagePlayer: ImagePlayerView,
    loadingView: View,
    brightnessView: View,
    modifier: Modifier = Modifier,
) {
    val state by overlayStateStore.uiState.collectAsState()

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

        // Brightness overlay
        AndroidView(
            factory = { brightnessView },
            modifier = Modifier.fillMaxSize(),
        )

        // Loading view
        AndroidView(
            factory = { loadingView },
            modifier = Modifier.fillMaxSize(),
        )

        // Overlay layer
        OverlayLayout(
            state = state,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
