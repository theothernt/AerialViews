package com.neilturner.aerialviews.screensaver.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import timber.log.Timber

/**
 * Phase 7 placeholder. It deliberately shows the shared controller's phase rather than any
 * playback UI; Phase 8 replaces this composable with the real screen.
 */
@Suppress("ktlint:standard:function-naming")
@Composable
internal fun ComposeScreensaverScreen(
    hostStarted: Boolean,
    onFirstItemDisplayed: () -> Unit,
) {
    val viewModel: ScreensaverViewModel = viewModel()
    val state by viewModel.state.collectAsState()

    // The placeholder is the only content in Phase 7; its first frame is the successful display
    // that lets the Phase 6 crash-loop guard reset after a Compose session starts.
    LaunchedEffect(Unit) {
        withFrameNanos { }
        Timber.i("Compose screensaver placeholder displayed")
        onFirstItemDisplayed()
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BasicText(
                text = "Compose screensaver placeholder",
                style = TextStyle(color = Color.White, fontSize = 32.sp),
            )
            BasicText(
                text = "Controller phase: ${state.phase.name}",
                style = TextStyle(color = Color.LightGray, fontSize = 18.sp),
            )
            BasicText(
                text = if (hostStarted) "Host started" else "Host waiting",
                style = TextStyle(color = Color.Gray, fontSize = 16.sp),
            )
        }
    }
}
