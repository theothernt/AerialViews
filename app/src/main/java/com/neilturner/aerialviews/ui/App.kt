package com.neilturner.aerialviews.ui

import android.app.Application
import com.neilturner.aerialviews.models.VideoQuality
import com.neilturner.aerialviews.models.prefs.AppleVideoPrefs
import com.neilturner.aerialviews.models.prefs.Comm1VideoPrefs
import com.neilturner.aerialviews.models.prefs.Comm2VideoPrefs
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.utils.DeviceHelper

class App : Application() {

    override fun onCreate() {
        super.onCreate()

        if (!DeviceHelper.hasHevcSupport() && GeneralPrefs.firstRun) {
            changeVideoQuality()
            GeneralPrefs.firstRun = false
        }
    }

    private fun changeVideoQuality() {
        // FireTV Gen 1 and emulator can't play HEVC/H.265
        // Set video quality to H.264
        AppleVideoPrefs.quality = VideoQuality.VIDEO_1080_H264
        Comm1VideoPrefs.quality = VideoQuality.VIDEO_1080_H264
        Comm2VideoPrefs.quality = VideoQuality.VIDEO_1080_H264
    }
}