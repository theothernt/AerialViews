@file:Suppress(
    "unused",
    "RedundantOverride",
    "RedundantOverride",
    "EmptyMethod",
    "RedundantSuppression",
    "RedundantSuppression",
)

package com.neilturner.aerialviews.ui.screensaver

import android.annotation.SuppressLint
import android.service.dreams.DreamService
import android.view.KeyEvent
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.ui.core.ScreenController
import com.neilturner.aerialviews.utils.FirebaseHelper
import com.neilturner.aerialviews.utils.InputHelper
import com.neilturner.aerialviews.utils.LocaleHelper
import com.neilturner.aerialviews.utils.WindowHelper
import timber.log.Timber

class DreamActivity : DreamService() {
    private lateinit var screenController: ScreenController

    @SuppressLint("AppBundleLocaleChanges")
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // Setup
        isFullscreen = true
        isInteractive = true

        // Start playback, etc
        screenController =
            if (GeneralPrefs.localeScreensaver.startsWith("default")) {
                ScreenController(this)
            } else {
                val altContext = LocaleHelper.alternateLocale(this, GeneralPrefs.localeScreensaver)
                ScreenController(altContext)
            }
        setContentView(screenController.view)
    }

    override fun onWakeUp() {
        try {
            super.onWakeUp()
        } catch (e: Exception) {
            Timber.e(e, "onWakeUp() super class exception")
        }
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        FirebaseHelper.logScreenView("Screensaver", this)
        // Start playback, etc
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (this::screenController.isInitialized &&
            InputHelper.handleKeyEvent(event, screenController, ::wakeUp)
        ) {
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && this::screenController.isInitialized) {
            WindowHelper.hideSystemUI(window, screenController.view)
        }
    }

    override fun onDreamingStopped() {
        super.onDreamingStopped()
        // Stop playback, animations, etc
        if (this::screenController.isInitialized) {
            screenController.stop()
        }
    }
}
