package com.neilturner.aerialviews.ui.screensaver

import android.annotation.SuppressLint
import android.service.dreams.DreamService
import android.view.KeyEvent
import android.view.MotionEvent
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.ui.core.ScreenController
import com.neilturner.aerialviews.utils.FirebaseHelper
import com.neilturner.aerialviews.utils.InputHelper
import com.neilturner.aerialviews.utils.LocaleHelper
import com.neilturner.aerialviews.utils.WindowHelper.hideSystemUI

class DreamActivity : DreamService() {
    private lateinit var screenController: ScreenController

    @SuppressLint("AppBundleLocaleChanges")
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // Setup
        isFullscreen = true
        isInteractive = true

        // Hide system UI on phones
        hideSystemUI(window)

        // Start playback, etc
        screenController =
            if (GeneralPrefs.localeScreensaver.startsWith("default")) {
                ScreenController(this)
            } else {
                val altContext = LocaleHelper.alternateLocale(this, GeneralPrefs.localeScreensaver)
                ScreenController(altContext)
            }
        setContentView(screenController.view)

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
            // Doesn't matter
        }
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        FirebaseHelper.analyticsScreenView("Screensaver", this)
        // Start playback, etc
    }

    private fun altWakeUp(exitApp: Boolean) {
        if (exitApp) wakeUp()
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
            // Ignore the restricted setting access error
            false
        }

    override fun onDreamingStopped() {
        super.onDreamingStopped()
        // Stop playback, animations, etc
        if (this::screenController.isInitialized) {
            screenController.stop()
        }
    }
}
