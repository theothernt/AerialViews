package com.neilturner.aerialviews.utils

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.google.firebase.encoders.json.BuildConfig

class MigrationHelper(val context: Context) {

    private val prefsPackageName = "${context.packageName}_preferences"

    @RequiresApi(Build.VERSION_CODES.P)
    fun upgradeSettings() {

        // If first install, exit early
        if (PackageHelper.isFirstInstall(context)) {
            Log.i(TAG, "Fresh install, no migration needed")
            return
        }

        // If package not updated, exit early
        if (!PackageHelper.isPackageUpdate(context)) {
            Log.i(TAG, "Package not updated, no migration needed")
            return
        } else {
            Log.i(TAG, "Package updated, checking if migration is needed")
        }

        val latestVersion = BuildConfig.VERSION_CODE
        val lastKnownVersion = lastKnownVersion()

        if (lastKnownVersion == latestVersion) {
            Log.i(TAG, "Package updated but already migrated")
            return
        }

        if (lastKnownVersion <= 10) release10()
        if (lastKnownVersion <= 11) release11()

        // After all migrations, set version to latest
        updateKnownVersion(latestVersion)
    }

    private fun release10() {
        // If less than v10, if apple disabled, disable comm1 and commm2
        // If less than v11, migrate other/new setting
        Log.i(TAG, "Migrating settings for release 10")
    }

    private fun release11() {
        Log.i(TAG, "Migrating settings for release 11")
    }

    // Get saved revision code or return 0
    private fun lastKnownVersion(): Int {
        val prefs = context.getSharedPreferences(prefsPackageName, Context.MODE_PRIVATE)
        return prefs.getInt("last_known_version", 0)
   }

    // Update saved revision code or return 0
    private fun updateKnownVersion(versionCode: Int) {
        val prefs = context.getSharedPreferences(prefsPackageName, Context.MODE_PRIVATE)
        prefs.edit().putInt("last_known_version", versionCode).apply()
    }

    companion object {
        private const val TAG = "MigrationHelper"
    }
}
