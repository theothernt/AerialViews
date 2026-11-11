package com.neilturner.aerialviews.screensaver.compose

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import timber.log.Timber

/**
 * Phase 8 screen. It hosts the existing players, drives the controller transitions, renders the
 * shared overlay state and reports the first item that actually becomes visible; input is added in
 * a later batch.
 */
@Suppress("ktlint:standard:function-naming")
@Composable
internal fun ComposeScreensaverScreen(
    hostStarted: Boolean,
    onFirstItemDisplayed: () -> Unit,
) {
    val context = LocalContext.current
    val application =
        context.applicationContext as? Application
            ?: error("Compose screensaver requires an Application context")
    val factory = remember(application) { screensaverViewModelFactory(application) }
    val viewModel: ScreensaverViewModel = viewModel(factory = factory)
    val state by viewModel.state.collectAsState()
    val overlayState by viewModel.overlayState.collectAsState()
    val reportFirstItem by rememberUpdatedState(onFirstItemDisplayed)

    LaunchedEffect(hostStarted) {
        if (hostStarted) {
            viewModel.start()
        }
    }

    var firstItemReported by remember { mutableStateOf(false) }
    LaunchedEffect(state.canSkip, state.media) {
        if (!firstItemReported && state.canSkip && state.media != null) {
            // Reset the Phase 6 guard only after the controller has accepted a prepared item.
            withFrameNanos { }
            firstItemReported = true
            Timber.i("Compose screensaver first item displayed")
            reportFirstItem()
        }
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.Black),
    ) {
        ComposePlaybackLayer(
            controller = viewModel.controller,
            state = state,
            hostStarted = hostStarted,
            modifier = Modifier.fillMaxSize(),
        ) {
            ComposeOverlayLayer(
                controller = viewModel.controller,
                state = state,
                overlayState = overlayState,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

private fun screensaverViewModelFactory(application: Application): ViewModelProvider.Factory =
    object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(ScreensaverViewModel::class.java)) {
                "Unsupported ViewModel: ${modelClass.name}"
            }
            return ScreensaverViewModel(application) as T
        }
    }
