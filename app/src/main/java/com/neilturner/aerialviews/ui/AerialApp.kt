package com.neilturner.aerialviews.ui

import android.app.Application
import android.os.StrictMode
import android.os.StrictMode.VmPolicy
import com.neilturner.aerialviews.BuildConfig
import com.neilturner.aerialviews.models.enums.OverlayType
import com.neilturner.aerialviews.models.enums.VideoQuality
import com.neilturner.aerialviews.models.prefs.AppleVideoPrefs
import com.neilturner.aerialviews.models.prefs.Comm1VideoPrefs
import com.neilturner.aerialviews.models.prefs.Comm2VideoPrefs
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.utils.DeviceHelper
import com.neilturner.aerialviews.utils.LogcatCapture
import timber.log.Timber

class AerialApp : Application() {
    override fun onCreate() {
        super.onCreate()
        configureLogging()

        if (GeneralPrefs.enableLogCapture) {
            LogcatCapture.start(applicationContext)
        }

        if (BuildConfig.DEBUG || BuildConfig.FLAVOR.contains("beta", false)) {
            Timber.plant(Timber.DebugTree())
        }

        @Suppress("ControlFlowWithEmptyBody")
        if (BuildConfig.DEBUG) {
            // setupStrictMode()
        }

        if (!GeneralPrefs.checkForHevcSupport) {
            // FireTV Gen 1 and emulator can't play HEVC/H.265
            // Set video quality to H.264
            if (!DeviceHelper.hasHevcSupport()) changeVideoQuality()

            // Turn off location overlay as layout is broken on the phone
            if (!DeviceHelper.isTV(applicationContext)) changeOverlayOption()

            GeneralPrefs.checkForHevcSupport = true
        }
    }

    private fun configureLogging() {
        val debugLogging = BuildConfig.DEBUG

        // Current backend for SMBJ logs is slf4j-simple.
        System.setProperty("org.slf4j.simpleLogger.showDateTime", "false")
        System.setProperty("org.slf4j.simpleLogger.showThreadName", "false")
        System.setProperty("org.slf4j.simpleLogger.showLogName", "false")

        // Change to debug when needed, very chatty!
        System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", if (debugLogging) "info" else "warn")
        System.setProperty("org.slf4j.simpleLogger.log.com.hierynomus", if (debugLogging) "info" else "warn")
        System.setProperty("org.slf4j.simpleLogger.log.org.apache.sshd", if (debugLogging) "info" else "warn")

        // If Log4j is reintroduced as backend, keep status logging debug-only.
        System.setProperty("log4j2.debug", debugLogging.toString())
    }

    private fun changeVideoQuality() {
        AppleVideoPrefs.quality = VideoQuality.VIDEO_1080_H264
        Comm1VideoPrefs.quality = VideoQuality.VIDEO_1080_H264
        Comm2VideoPrefs.quality = VideoQuality.VIDEO_1080_H264
    }

    private fun changeOverlayOption() {
        GeneralPrefs.slotBottomRight1 = OverlayType.EMPTY
    }

    private fun setupStrictMode() {
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy
                .Builder()
                .detectCustomSlowCalls()
                // .penaltyFlashScreen()
                .penaltyLog()
                .build(),
        )
        StrictMode.setVmPolicy(
            VmPolicy
                .Builder()
                .detectLeakedClosableObjects()
                .detectLeakedSqlLiteObjects()
                .detectLeakedRegistrationObjects()
                .detectActivityLeaks()
                .penaltyLog()
                // .penaltyDeath()
                .build(),
        )
    }
}
