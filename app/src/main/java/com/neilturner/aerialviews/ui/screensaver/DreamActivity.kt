package com.neilturner.aerialviews.ui.screensaver

import android.annotation.SuppressLint
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.ui.core.ScreensaverViewModel
import com.neilturner.aerialviews.ui.helpers.InputHelper
import com.neilturner.aerialviews.ui.helpers.LocaleHelper
import com.neilturner.aerialviews.ui.helpers.WindowHelper.hideSystemUI
import com.neilturner.aerialviews.ui.overlays.compose.ScreensaverScreen
import com.neilturner.aerialviews.utils.FirebaseHelper
import org.koin.android.ext.android.getKoin
import org.koin.core.parameter.parametersOf

class DreamActivity : DreamServiceCompat() {
    private lateinit var screenController: com.neilturner.aerialviews.ui.core.ScreenController
    private lateinit var viewModel: ScreensaverViewModel

    @SuppressLint("AppBundleLocaleChanges")
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isFullscreen = true
        isInteractive = true

        hideSystemUI(window)

        viewModel = koinViewModel()

        val controllerContext =
            if (GeneralPrefs.localeScreensaver.startsWith("default")) {
                this
            } else {
                LocaleHelper.alternateLocale(this, GeneralPrefs.localeScreensaver)
            }
        screenController = getKoin().get { parametersOf(controllerContext) }

        screenController.onMetadataUpdate = { media -> viewModel.updateMetadataOverlayData(media) }
        screenController.onOverlayReset = { viewModel.onOverlayReset() }
        screenController.onLoadingStateUpdate = { visible, text, spinnerVisible ->
            viewModel.onLoadingStateUpdate(visible, text, spinnerVisible)
        }

        screenController.detachViewsForCompose()
        viewModel.startOverlayEventBridge()
        viewModel.startServices()
        viewModel.scheduleSleepTimer()

        setContent {
            val loadingState by viewModel.loadingState.collectAsState()
            val overlayState by viewModel.overlayStateStore.uiState.collectAsState()
            ScreensaverScreen(
                overlayStateStore = viewModel.overlayStateStore,
                videoPlayer = screenController.videoPlayer,
                imagePlayer = screenController.imagePlayer,
                loadingVisible = loadingState.visible,
                loadingText = loadingState.text,
                loadingSpinnerVisible = loadingState.spinnerVisible,
                onSkipNext = { screenController.skipItem() },
                onSkipPrevious = { screenController.skipItem(previous = true) },
                onPause = { screenController.togglePause() },
            )
        }

        InputHelper.setupGestureListener(
            context = this,
            controller = screenController,
            exit = ::altWakeUp,
        )
    }

    override fun onWakeUp() {
        try {
            super.onWakeUp()
        } catch (e: Exception) {
        }
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        FirebaseHelper.analyticsScreenView("Screensaver", this)
    }

    private fun altWakeUp(exitApp: Boolean) {
        if (exitApp) wakeUp()
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean =
        try {
            super.dispatchTouchEvent(event)
        } catch (e: SecurityException) {
            true
        }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (this::screenController.isInitialized &&
            InputHelper.handleKeyEvent(event, screenController, ::altWakeUp)
        ) {
            return true
        }

        return super.dispatchKeyEvent(event)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean =
        try {
            super.dispatchGenericMotionEvent(event)
        } catch (e: SecurityException) {
            false
        }

    override fun onDreamingStopped() {
        super.onDreamingStopped()
        viewModel.stopOverlayEventBridge()
        viewModel.stopServices()
        if (this::screenController.isInitialized) {
            screenController.stop()
        }
    }
}
