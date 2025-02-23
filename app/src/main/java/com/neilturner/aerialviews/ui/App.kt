package com.neilturner.aerialviews.ui

import android.app.Application
import com.neilturner.aerialviews.BuildConfig
import com.neilturner.aerialviews.models.enums.VideoQuality
import com.neilturner.aerialviews.models.prefs.AppleVideoPrefs
import com.neilturner.aerialviews.models.prefs.Comm1VideoPrefs
import com.neilturner.aerialviews.models.prefs.Comm2VideoPrefs
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.utils.DeviceHelper
import timber.log.Timber

class App : Application() {
    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.DEBUG || BuildConfig.FLAVOR.contains("beta", false)) {
            Timber.plant(Timber.DebugTree())
        }

        // FireTV Gen 1 and emulator can't play HEVC/H.265
        // Set video quality to H.264
        if (!DeviceHelper.hasHevcSupport() &&
            !GeneralPrefs.checkForHevcSupport
        ) {
            changeVideoQuality()
            GeneralPrefs.checkForHevcSupport = true
        }
    }

    private fun changeVideoQuality() {
        AppleVideoPrefs.quality = VideoQuality.VIDEO_1080_H264
        Comm1VideoPrefs.quality = VideoQuality.VIDEO_1080_H264
        Comm2VideoPrefs.quality = VideoQuality.VIDEO_1080_H264
    }
}
