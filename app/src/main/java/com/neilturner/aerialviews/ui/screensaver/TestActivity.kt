package com.neilturner.aerialviews.ui.screensaver

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.ui.core.ScreenController
import com.neilturner.aerialviews.utils.DeviceHelper
import com.neilturner.aerialviews.utils.FirebaseHelper
import com.neilturner.aerialviews.utils.InputHelper
import com.neilturner.aerialviews.utils.LocaleHelper
import com.neilturner.aerialviews.utils.PreferenceHelper
import com.neilturner.aerialviews.utils.SwipeGestureListener
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

        if (this::screenController.isInitialized) {
            screenController.stop()
        }

        // Don't use finishWithResult as it's not suitable at the moment
        finishAndRemoveTask()
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

        screenController
            .view
            .setOnTouchListener(
            SwipeGestureListener(
                context = this,
                onSwipeUp = { Toast.makeText(this, "Swiped Up", Toast.LENGTH_SHORT).show() },
                onSwipeDown = { Toast.makeText(this, "Swiped Down", Toast.LENGTH_SHORT).show() },
                onSwipeLeft = { Toast.makeText(this, "Swiped Left", Toast.LENGTH_SHORT).show() },
                onSwipeRight = { Toast.makeText(this, "Swiped Right", Toast.LENGTH_SHORT).show() },
            )
        )
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (GeneralPrefs.closeOnScreenTap && !DeviceHelper.isTV(this)) {
            finishWithResult()
            return true
        }
        return super.onTouchEvent(event)
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
        window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (this::screenController.isInitialized) {
            screenController.stop()
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
