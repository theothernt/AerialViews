package com.neilturner.aerialviews.ui.screensaver

import android.app.Activity
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.ui.core.ScreenController
import com.neilturner.aerialviews.utils.FirebaseHelper
import com.neilturner.aerialviews.utils.InputHelper
import com.neilturner.aerialviews.utils.LocaleHelper
import com.neilturner.aerialviews.utils.WindowHelper

class TestActivity : Activity() {
    private lateinit var screenController: ScreenController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Setup
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setTitle(R.string.app_name)
    }

    override fun onResume() {
        super.onResume()
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        FirebaseHelper.logScreenView("Test Screensaver", this)
    }

    override fun onPause() {
        window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        super.onPause()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
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

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (this::screenController.isInitialized &&
            InputHelper.handleKeyEvent(event, screenController, ::finish)
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

    override fun onStop() {
        super.onStop()
        // Stop playback, animations, etc
        window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (this::screenController.isInitialized) {
            screenController.stop()
        }
    }
}
