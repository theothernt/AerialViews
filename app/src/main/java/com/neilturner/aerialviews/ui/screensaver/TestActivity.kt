package com.neilturner.aerialviews.ui.screensaver

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.ui.core.ScreenController
import com.neilturner.aerialviews.ui.core.ScreensaverViewModel
import com.neilturner.aerialviews.ui.helpers.DeviceHelper
import com.neilturner.aerialviews.ui.helpers.InputHelper
import com.neilturner.aerialviews.ui.helpers.LocaleHelper
import com.neilturner.aerialviews.ui.helpers.PreferenceHelper
import com.neilturner.aerialviews.ui.helpers.WindowHelper.hideSystemUI
import com.neilturner.aerialviews.ui.overlays.compose.ScreensaverScreen
import com.neilturner.aerialviews.utils.FirebaseHelper
import org.koin.android.ext.android.getKoin
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf
import timber.log.Timber

class TestActivity : AppCompatActivity() {
    private lateinit var screenController: ScreenController
    private val viewModel: ScreensaverViewModel by viewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setTitle(R.string.app_name)
        supportActionBar?.hide()
    }

    override fun onResume() {
        super.onResume()
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        FirebaseHelper.analyticsScreenView("Test Screensaver", this)
    }

    override fun onPause() {
        super.onPause()
        window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        if (!DeviceHelper.isTV(this) && this::screenController.isInitialized) {
            screenController.stop()
            finishWithResult()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        viewModel.stopOverlayEventBridge()
        viewModel.stopServices()
        if (this::screenController.isInitialized) {
            screenController.stop()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        hideSystemUI(window)

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
            exit = ::finishWithResult,
        )
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (this::screenController.isInitialized &&
            InputHelper.handleKeyEvent(event, screenController, ::finishWithResult)
        ) {
            return true
        }

        return super.dispatchKeyEvent(event)
    }

    override fun onStop() {
        super.onStop()
        window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (this::screenController.isInitialized && DeviceHelper.isTV(this)) {
            screenController.stop()
            finishAndRemoveTask()
        }
    }

    private fun finishWithResult(exitApp: Boolean = false) {
        Timber.i(
            "isExitToSettingSet: ${PreferenceHelper.isExitToSettingSet()}, exitApp: $exitApp, startScreensaverOnLaunch: ${GeneralPrefs.startScreensaverOnLaunch}",
        )

        val shouldExitApp = (
            GeneralPrefs.startScreensaverOnLaunch &&
                exitApp &&
                PreferenceHelper.isExitToSettingSet()
        )
        val resultIntent =
            Intent().apply {
                putExtra("exit_app", shouldExitApp)
            }
        setResult(RESULT_OK, resultIntent)
        finish()
    }
}
