package com.neilturner.aerialviews.ui.screensaver

import android.annotation.SuppressLint
import android.content.Intent
import android.service.dreams.DreamService
import android.view.KeyEvent
import android.view.MotionEvent
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.ui.core.ScreenController
import com.neilturner.aerialviews.ui.helpers.InputHelper
import com.neilturner.aerialviews.ui.helpers.LocaleHelper
import com.neilturner.aerialviews.ui.helpers.WindowHelper.hideSystemUI
import com.neilturner.aerialviews.utils.FirebaseHelper
import timber.log.Timber

class DreamActivity : DreamService() {
    companion object {
        // The dream that handed playback over to an activity and is still holding the screen on,
        // so the activity can dismiss it when the viewer exits
        private var activeHandoffDream: DreamActivity? = null

        fun dismissHandoffDream() {
            val dream = activeHandoffDream ?: return
            activeHandoffDream = null
            try {
                dream.wakeUp()
            } catch (e: Exception) {
                Timber.d(e, "Dream already gone when dismissing after handoff")
            }
        }
    }

    private lateinit var screenController: ScreenController

    @SuppressLint("AppBundleLocaleChanges")
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // Setup
        isFullscreen = true
        isInteractive = true

        // Some TVs (eg. Philips OLEDs) classify any 3rd-party dream as an on-screen display and
        // drop their own burn-in screensaver over it after a few minutes, whatever the dream shows.
        // Handing off to a normal activity makes them treat it as video playback instead.
        if (GeneralPrefs.runAsActivity) {
            Timber.i("Handing off to an activity instead of playing in the dream")
            return
        }

        // Hide system UI on phones
        hideSystemUI(window)

        if (this::screenController.isInitialized) {
            Timber.d("onAttachedToWindow called again with an active screenController — releasing it first")
            screenController.stop()
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

        // Launched from here rather than onAttachedToWindow, so the dream's window is already
        // showing - an app without a visible window is not allowed to start an activity
        if (GeneralPrefs.runAsActivity) {
            startActivity(
                Intent(this, TestActivity::class.java).apply {
                    putExtra(TestActivity.EXTRA_FROM_DREAM, true)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                },
            )
            // The dream deliberately stays alive underneath, holding the screen on. Ending it here
            // would let the system apply the idle timeout that has already expired and sleep the
            // screen, and the activity cannot hold the screen itself until its window is visible,
            // which it is not while the dream is on top. The activity dismisses it on exit.
            activeHandoffDream = this
            return
        }

        FirebaseHelper.analyticsScreenView("Screensaver", this)
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

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (InputHelper.handleGenericMotionEvent(event, ::altWakeUp)) {
            return true
        }

        return try {
            super.dispatchGenericMotionEvent(event)
        } catch (e: SecurityException) {
            // Ignore the restricted setting access error
            false
        }
    }

    override fun onDreamingStopped() {
        Timber.d("onDreamingStopped")
        if (activeHandoffDream === this) {
            activeHandoffDream = null
        }
        // Stop playback, animations, etc
        if (this::screenController.isInitialized) {
            screenController.stop()
        }
        super.onDreamingStopped()
    }

    override fun onDetachedFromWindow() {
        Timber.d("onDetachedFromWindow")
        if (this::screenController.isInitialized) {
            screenController.stop()
        }
        super.onDetachedFromWindow()
    }
}
