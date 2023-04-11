package com.neilturner.aerialviews.utils

import android.content.Context
import android.util.Log
import com.neilturner.aerialviews.BuildConfig

class MigrationHelper(val context: Context) {

    private val prefsPackageName = "${context.packageName}_preferences"
    private val prefs = context.getSharedPreferences(prefsPackageName, Context.MODE_PRIVATE)

    fun upgradeSettings() {
        val latestVersion = BuildConfig.VERSION_CODE
        val lastKnownVersion = getLastKnownVersion()

        Log.i(TAG, "Build code $latestVersion, Last known version $lastKnownVersion")

        // If package not updated, exit early
        if (!PackageHelper.isPackageUpdate(context)) {
            Log.i(TAG, "Package not updated, no migration needed")
            return
        } else {
            Log.i(TAG, "Package updated, checking if migration is needed")
        }

        if (lastKnownVersion == latestVersion) {
            Log.i(TAG, "Package updated but already migrated")
            return
        }

        if (lastKnownVersion < 10) release10()
        if (lastKnownVersion < 11) release11()

        // After all migrations, set version to latest
        updateKnownVersion(latestVersion)
    }

    private fun release10() {
        Log.i(TAG, "Migrating settings for release 10")

        // Covers case when Apple videos are disabled but Community videos are not
        val communityVideosEnabled = prefs.contains("comm1_videos_enabled") ||
            prefs.contains("comm2_videos_enabled")
        val appleVideosEnabled = prefs.getBoolean("apple_videos_enabled", false)

        if (!appleVideosEnabled && !communityVideosEnabled) {
            Log.i(TAG, "Disabling new community videos")
            prefs.edit().putBoolean("comm1_videos_enabled", false).apply()
            prefs.edit().putBoolean("comm2_videos_enabled", false).apply()
            return
        } else {
            Log.i(TAG, "Leaving community videos enabled")
        }

        val videoQuality = prefs.getString("apple_videos_quality", "").toStringOrEmpty()
        if (videoQuality.contains("4K", true) &&
            appleVideosEnabled
        ) {
            Log.i(TAG, "Setting community videos to 4K")
            prefs.edit().putString("comm1_videos_quality", "VIDEO_4K_SDR").apply()
            prefs.edit().putString("comm2_videos_quality", "VIDEO_4K_SDR").apply()
        } else {
            Log.i(TAG, "Not setting community videos to 4K")
        }
    }

    private fun release11() {
        Log.i(TAG, "Migrating settings for release 11")

        // Setting key will exist if changed or if user has visited that settings fragment/ui
        val oldLocationSetting = prefs.contains("show_location")
        if (!oldLocationSetting) {
            Log.i(TAG, "Old location setting does not exist, no need to migrate")
            return
        }

        val locationEnabled = prefs.getBoolean("show_location", true)
        val locationType = prefs.getString("show_location_style", "VERBOSE").toStringOrEmpty()

        Log.i(TAG, "Remove old video location pref and set new POI default")
        prefs.edit().remove("show_location").apply()
        prefs.edit().putString("location_style", "POI").apply()

        if (locationEnabled) {
            if (locationType.contains("SHORT")) {
                Log.i(TAG, "Set video location to title")
                prefs.edit().putString("location_style", "TITLE").apply()
            }
        } else {
            Log.i(TAG, "Set video location to off")
            prefs.edit().putString("location_style", "OFF").apply()
        }
    }

    // Get saved revision code or return 0
    private fun getLastKnownVersion(): Int {
        return prefs.getInt("last_known_version", 0)
    }

    // Update saved revision code or return 0
    private fun updateKnownVersion(versionCode: Int) {
        Log.i(TAG, "Updating last known version to $versionCode")
        prefs.edit().putInt("last_known_version", versionCode).apply()
    }

    companion object {
        private const val TAG = "MigrationHelper"
    }
}
