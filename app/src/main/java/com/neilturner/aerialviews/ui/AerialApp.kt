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
import timber.log.Timber

class AerialApp : Application() {
    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.DEBUG || BuildConfig.FLAVOR.contains("beta", false)) {
            Timber.plant(Timber.DebugTree())
        }

        if (BuildConfig.DEBUG) {
            setupStrictMode()
        }

        if (!GeneralPrefs.checkForHevcSupport) {
            // FireTV Gen 1 and emulator can't play HEVC/H.265
            // Set video quality to H.264
            if (!DeviceHelper.hasHevcSupport()) changeVideoQuality()

            // Turn off location overlay as layout is broken on the phone
            if (!DeviceHelper.isTV(applicationContext)) changeOverlayOption()

            GeneralPrefs.checkForHevcSupport = true
        }

        migrateImmichFavoritesPreference()
    }

    // Temp fix for betas, should be removed in future releases
    private fun migrateImmichFavoritesPreference() {
        val sharedPrefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(this)

        try {
            // Check if the preference exists
            if (sharedPrefs.contains("immich_media_include_favorites")) {
                // Check if it's a boolean value
                val allPrefs = sharedPrefs.all
                if (allPrefs["immich_media_include_favorites"] is Boolean) {
                    // Get editor and perform migration
                    sharedPrefs.edit().apply {
                        // Remove boolean preference
                        remove("immich_media_include_favorites")
                        // Create string preference with same name
                        putString("immich_media_include_favorites", "100")
                        apply()
                    }

                    Timber.d("Migrated immich_media_include_favorites from boolean to string")
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error migrating immich_media_include_favorites preference")
        }
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
                .penaltyFlashScreen()
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
