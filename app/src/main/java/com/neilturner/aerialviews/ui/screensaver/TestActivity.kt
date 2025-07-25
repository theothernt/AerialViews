package com.neilturner.aerialviews.ui.screensaver

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.ui.core.ScreenController
import com.neilturner.aerialviews.utils.DeviceHelper
import com.neilturner.aerialviews.utils.FirebaseHelper
import com.neilturner.aerialviews.utils.InputHelper
import com.neilturner.aerialviews.utils.LocaleHelper
import com.neilturner.aerialviews.utils.PreferenceHelper
import com.neilturner.aerialviews.utils.WindowHelper.hideSystemUI
import timber.log.Timber

class TestActivity : AppCompatActivity() {
    private lateinit var screenController: ScreenController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Setup
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setTitle(R.string.app_name)
        supportActionBar?.hide()
    }

    override fun onResume() {
        super.onResume()
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        FirebaseHelper.logScreenView("Test Screensaver", this)
    }

    override fun onPause() {
        super.onPause()
        window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Using OSD TV menus calls suspend but the screensaver is still running
        // So only stop if on phone or tablet
        if (!DeviceHelper.isTV(this) && this::screenController.isInitialized) {
            screenController.stop()
            finishWithResult()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

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
        // Stop playback, animations, etc
        // Stop here in TV, already stopped in onPause if phone
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
