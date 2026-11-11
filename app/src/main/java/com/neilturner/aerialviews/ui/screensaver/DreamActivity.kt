package com.neilturner.aerialviews.ui.screensaver

import android.annotation.SuppressLint
import android.content.Intent
import android.view.KeyEvent
import android.view.MotionEvent
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.ui.core.ExitReason
import com.neilturner.aerialviews.ui.core.ScreensaverUi
import com.neilturner.aerialviews.ui.helpers.WindowHelper.hideSystemUI
import com.neilturner.aerialviews.utils.DreamTiming
import com.neilturner.aerialviews.utils.FirebaseHelper
import timber.log.Timber

class DreamActivity : DreamServiceCompat() {
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

    private var screensaverUi: ScreensaverUi? = null

    @SuppressLint("AppBundleLocaleChanges")
    override fun onAttachedToWindow() {
        DreamTiming.mark("dream.attach")
        super.onAttachedToWindow()
        // Setup
        isFullscreen = true
        isInteractive = true

        // Drop a previous session before a possible reattach on the same service instance.
        releaseScreensaverUi()

        // Some TVs (eg. Philips OLEDs) classify any 3rd-party dream as an on-screen display and
        // drop their own burn-in screensaver over it after a few minutes, whatever the dream shows.
        // Handing off to a normal activity makes them treat it as video playback instead.
        if (GeneralPrefs.runAsActivity) {
            Timber.i("Handing off to an activity instead of playing in the dream")
            return
        }

        // Hide system UI on phones
        hideSystemUI(window)

        val host =
            DreamScreensaverHost(
                context = screensaverLocaleContext(this),
                window = window,
                lifecycleOwner = this,
                savedStateRegistryOwner = this,
                viewModelStoreOwner = this,
                onExit = ::handleExit,
            )
        screensaverUi = ScreensaverUiFactory.create(host)
        DreamTiming.mark("dream.contentView")
    }

    override fun onWakeUp() {
        try {
            super.onWakeUp()
        } catch (e: Exception) {
            // Doesn't matter
        }
    }

    override fun onDreamingStarted() {
        // DreamServiceCompat marks the owner STARTED here first.
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
        screensaverUi?.onStart()
    }

    private fun handleExit(reason: ExitReason) {
        altWakeUp(reason == ExitReason.EXIT)
    }

    private fun altWakeUp(exitApp: Boolean) {
        if (exitApp) wakeUp()
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (screensaverUi?.onTouchEvent(event) == true) {
            return true
        }

        return try {
            super.dispatchTouchEvent(event)
        } catch (e: SecurityException) {
            // Android bug: DreamService internally reads a restricted settings key
            // on Android 12+. Safe to swallow — touch handling may be degraded
            // but the dream will continue running.
            true
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (screensaverUi?.onKeyEvent(event) == true) {
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
        if (screensaverUi?.onGenericMotionEvent(event) == true) {
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
        DreamTiming.mark("dream.stop")
        Timber.d("onDreamingStopped")
        if (activeHandoffDream === this) {
            activeHandoffDream = null
        }
        // Stop playback, animations, etc
        screensaverUi?.onStop()
        super.onDreamingStopped()
    }

    override fun onDetachedFromWindow() {
        Timber.d("onDetachedFromWindow")
        releaseScreensaverUi()
        super.onDetachedFromWindow()
    }

    override fun onDestroy() {
        releaseScreensaverUi()
        super.onDestroy()
    }

    private fun releaseScreensaverUi() {
        val ui = screensaverUi ?: return
        screensaverUi = null
        ScreensaverUiFactory.release(ui)
    }
}
