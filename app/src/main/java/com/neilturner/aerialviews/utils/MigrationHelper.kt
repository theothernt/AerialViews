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

        Log.i(TAG, "Build code $lastKnownVersion, Last known version $lastKnownVersion")

        // If first install, exit early
        if (PackageHelper.isFirstInstall(context)) {
            Log.i(TAG, "Fresh install, no migration needed")
            updateKnownVersion(latestVersion)
            return
        }

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

        if (lastKnownVersion <= 10) release10()
        // if (lastKnownVersion <= 11) release11()

        // After all migrations, set version to latest
        updateKnownVersion(latestVersion)
    }

    private fun release10() {
        Log.i(TAG, "Migrating settings for release 10")

        val appleVideosEnabled = prefs.getBoolean("apple_videos_enabled", false)
        if (!appleVideosEnabled) {
            Log.i(TAG, "Disabling new community videos")
            prefs.edit().putBoolean("comm1_videos_enabled", false).apply()
            prefs.edit().putBoolean("comm2_videos_enabled", false).apply()
            return
        }

        val videoQuality = prefs.getString("apple_videos_quality", "").toStringOrEmpty()
        if (videoQuality.contains("4K", true)) {
            Log.i(TAG, "Setting community videos to 4K")
            prefs.edit().putString("comm1_videos_quality", "VIDEO_4K_SDR").apply()
            prefs.edit().putString("comm2_videos_quality", "VIDEO_4K_SDR").apply()
        }
    }

    private fun release11() {
        Log.i(TAG, "Migrating settings for release 11")
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
