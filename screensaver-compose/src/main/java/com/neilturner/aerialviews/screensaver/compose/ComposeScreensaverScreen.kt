package com.neilturner.aerialviews.screensaver.compose

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.neilturner.aerialviews.ui.core.ExitReason
import timber.log.Timber

/**
 * Phase 8 screen. It hosts the existing players, drives the controller transitions, renders the
 * shared overlay state, installs the input handler and reports the first item that becomes visible.
 */
@Suppress("ktlint:standard:function-naming")
@Composable
internal fun ComposeScreensaverScreen(
    hostStarted: Boolean,
    onFirstItemDisplayed: () -> Unit,
    onExit: (ExitReason) -> Unit = {},
    onInputReady: (ComposeScreensaverInputHandler?) -> Unit = {},
) {
    val context = LocalContext.current
    val application =
        context.applicationContext as? Application
            ?: error("Compose screensaver requires an Application context")
    val factory = remember(application) { screensaverViewModelFactory(application) }
    val viewModel: ScreensaverViewModel = viewModel(factory = factory)
    val state by viewModel.state.collectAsState()
    val overlayState by viewModel.overlayState.collectAsState()
    val loadingStatus by viewModel.loadingStatus.collectAsState()
    val reportFirstItem by rememberUpdatedState(onFirstItemDisplayed)
    val exitState = rememberUpdatedState(onExit)
    val inputReadyState = rememberUpdatedState(onInputReady)

    var playbackControls by remember { mutableStateOf<ComposePlaybackControls?>(null) }
    var overlayRevealKey by remember { mutableIntStateOf(0) }

    val inputHandler =
        remember(viewModel.controller) {
            ComposeScreensaverInput(
                controller = viewModel.controller,
                playbackControls = { playbackControls },
                onExit = { exitState.value(it) },
                onRevealOverlays = { overlayRevealKey++ },
            )
        }

    DisposableEffect(inputHandler) {
        inputReadyState.value(inputHandler)
        onDispose {
            inputReadyState.value(null)
            inputHandler.reset()
        }
    }

    LaunchedEffect(hostStarted) {
        if (hostStarted) {
            viewModel.start()
        }
    }

    // A reveal applies only to the current item; the next item starts with its configured mode.
    LaunchedEffect(state.itemGeneration) {
        overlayRevealKey = 0
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
            onActionsReady = { playbackControls = it },
            loadingContent = {
                ComposeStatusContent(
                    state = state,
                    loadingStatus = loadingStatus,
                )
            },
            overlayContent = {
                ComposeOverlayLayer(
                    controller = viewModel.controller,
                    state = state,
                    overlayState = overlayState,
                    revealKey = overlayRevealKey,
                    modifier = Modifier.fillMaxSize(),
                )
            },
        )

        if (state.blackout.active) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black))
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
