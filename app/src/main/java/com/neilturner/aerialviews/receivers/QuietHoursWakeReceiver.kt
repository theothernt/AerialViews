package com.neilturner.aerialviews.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import com.neilturner.aerialviews.ui.screensaver.TestActivity
import com.neilturner.aerialviews.utils.QuietHoursHelper
import timber.log.Timber

class QuietHoursWakeReceiver : BroadcastReceiver() {
    
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            "WAKE_UP_FROM_QUIET_HOURS" -> {
                Timber.d("Received wake-up broadcast from quiet hours")
                
                try {
                    // Wake up the screen first
                    val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                    val wakeLock = powerManager.newWakeLock(
                        PowerManager.FULL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                        "AerialViews:QuietHoursWakeUp"
                    )
                    wakeLock.acquire(10000) // Hold for 10 seconds
                    Timber.d("Acquired wake lock to turn on screen")
                    
                    // Start the screensaver using the same mechanism as the main app
                    val screensaverIntent = Intent(context, TestActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                    context.startActivity(screensaverIntent)
                    Timber.d("Started screensaver after quiet hours")
                    
                    // Release the wake lock after a short delay to allow the screensaver to start
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        try {
                            if (wakeLock.isHeld) {
                                wakeLock.release()
                                Timber.d("Released wake lock")
                            }
                        } catch (e: Exception) {
                            Timber.e(e, "Error releasing wake lock")
                        }
                    }, 5000) // Release after 5 seconds
                    
                } catch (e: Exception) {
                    Timber.e(e, "Failed to start screensaver after quiet hours")
                }
            }
            
            "QUIET_HOURS_START" -> {
                Timber.d("Received quiet hours start broadcast")
                
                try {
                    // Schedule the wake-up alarm for when quiet hours end
                    QuietHoursHelper.scheduleWakeUpAlarm(context)
                    Timber.d("Scheduled wake-up alarm for end of quiet hours")
                    
                    // Send a broadcast to the DreamActivity to wake up
                    val wakeUpIntent = Intent("QUIET_HOURS_WAKE_UP").apply {
                        setPackage(context.packageName)
                    }
                    context.sendBroadcast(wakeUpIntent)
                    Timber.d("Sent wake-up broadcast to DreamActivity")
                    
                } catch (e: Exception) {
                    Timber.e(e, "Failed to handle quiet hours start")
                }
            }
        }
    }
}
