package com.neilturner.aerialviews.ui.screensaver

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.service.dreams.DreamService
import android.view.KeyEvent
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.ui.core.ScreenController
import com.neilturner.aerialviews.utils.FirebaseHelper
import com.neilturner.aerialviews.utils.InputHelper
import com.neilturner.aerialviews.utils.LocaleHelper
import com.neilturner.aerialviews.utils.QuietHoursHelper
import com.neilturner.aerialviews.utils.WindowHelper.hideSystemUI
import timber.log.Timber

class DreamActivity : DreamService() {
    private lateinit var screenController: ScreenController
    
    private val quietHoursWakeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "QUIET_HOURS_WAKE_UP") {
                Timber.d("DreamActivity received quiet hours wake-up broadcast")
                wakeUp()
            }
        }
    }

    @SuppressLint("AppBundleLocaleChanges")
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        
        // Register broadcast receiver for quiet hours wake-up
        val filter = IntentFilter("QUIET_HOURS_WAKE_UP")
        registerReceiver(quietHoursWakeReceiver, filter)
        
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
        
        // Test quiet hours settings for debugging
        QuietHoursHelper.testQuietHoursSettings(this)
        
        // Check if we're in quiet hours
        if (QuietHoursHelper.isInQuietHours()) {
            FirebaseHelper.analyticsScreenView("Screensaver - Quiet Hours", this)
            Timber.d("In quiet hours - scheduling wake-up alarm and exiting")
            // Cancel any existing quiet hours start alarm
            QuietHoursHelper.cancelQuietHoursStartAlarm(this)
            // Schedule alarm to wake up when quiet hours end
            QuietHoursHelper.scheduleWakeUpAlarm(this)
            // Put the screensaver to sleep during quiet hours
            wakeUp()
            return
        }
        
        Timber.d("Not in quiet hours - cancelling any existing alarms and starting normally")
        // Cancel any existing wake-up alarm since we're not in quiet hours
        QuietHoursHelper.cancelWakeUpAlarm(this)
        // Cancel any existing quiet hours start alarm since we're not in quiet hours
        QuietHoursHelper.cancelQuietHoursStartAlarm(this)
        
        // Schedule alarm for when quiet hours start (to turn off screensaver)
        QuietHoursHelper.scheduleQuietHoursStartAlarm(this)
        
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

    override fun onDreamingStopped() {
        super.onDreamingStopped()
        // Stop playback, animations, etc
        if (this::screenController.isInitialized) {
            screenController.stop()
        }
        
        // Unregister broadcast receiver
        try {
            unregisterReceiver(quietHoursWakeReceiver)
        } catch (e: Exception) {
            // Receiver might not be registered
        }
    }
    
}
