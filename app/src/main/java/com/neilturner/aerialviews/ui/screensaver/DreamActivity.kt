package com.neilturner.aerialviews.ui.screensaver

import android.annotation.SuppressLint
import android.service.dreams.DreamService
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowManager
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.services.MusicEvent
import com.neilturner.aerialviews.ui.core.ScreenController
import com.neilturner.aerialviews.ui.helpers.InputHelper
import com.neilturner.aerialviews.ui.helpers.LocaleHelper
import com.neilturner.aerialviews.ui.helpers.WindowHelper.hideSystemUI
import com.neilturner.aerialviews.utils.FirebaseHelper
import me.kosert.flowbus.EventsReceiver
import me.kosert.flowbus.subscribe
import timber.log.Timber

class DreamActivity : DreamService() {
    private lateinit var screenController: ScreenController
    private val eventsReceiver = EventsReceiver()

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
        eventsReceiver.subscribe<MusicEvent> { event ->
            updateKeepScreenOn(event.isPlaying)
        }
        // Start playback, etc
    }

    private fun altWakeUp(exitApp: Boolean) {
        if (exitApp) wakeUp()
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean =
        try {
            super.dispatchTouchEvent(event)
        } catch (e: SecurityException) {
            // Android bug: DreamService internally reads a restricted settings key
            // on Android 12+. Safe to swallow — touch handling may be degraded
            // but the dream will continue running.
            true
        }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (this::screenController.isInitialized &&
            InputHelper.handleKeyEvent(event, screenController, ::altWakeUp)
        ) {
            return true
        }

        return try {
            super.dispatchKeyEvent(event)
        } catch (e: SecurityException) {
            // Android bug: some OEM builds require BROADCAST_CLOSE_SYSTEM_DIALOGS
            // for the fallback event handler's sendCloseSystemWindows() call.
            // Safe to swallow — this only fires for keys we don't already handle.
            true
        }
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
        eventsReceiver.unsubscribe()
        window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // Stop playback, animations, etc
        if (this::screenController.isInitialized) {
            screenController.stop()
        }
    }

    private fun updateKeepScreenOn(isMusicPlaying: Boolean) {
        if (GeneralPrefs.keepScreenOnWhileMusicPlaying && isMusicPlaying) {
            Timber.i("Keep screen on")
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            Timber.i("DON'T Keep screen on")
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}
