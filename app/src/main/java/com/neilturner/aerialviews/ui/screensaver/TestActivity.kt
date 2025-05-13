package com.neilturner.aerialviews.ui.screensaver

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.ui.core.ScreenController
import com.neilturner.aerialviews.utils.FirebaseHelper
import com.neilturner.aerialviews.utils.InputHelper
import com.neilturner.aerialviews.utils.LocaleHelper
import com.neilturner.aerialviews.utils.PreferenceHelper
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            @Suppress("DEPRECATION")
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let {
                it.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                            View.SYSTEM_UI_FLAG_FULLSCREEN
                    )
        }

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
