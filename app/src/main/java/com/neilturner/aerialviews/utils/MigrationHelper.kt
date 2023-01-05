package com.neilturner.aerialviews.utils

import android.content.Context
import android.util.Log

class MigrationHelper(val context: Context) {

    private val prefsPackageName = "${context.packageName}_preferences"

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
        }

        //val versionCode = lastKnownVersion()
        val version = PackageHelper.versionCode(context)
        val lastKnownVersion = lastKnownVersion()

        if (lastKnownVersion == version) {
            Log.i(TAG, "Package updated but already migrated")
            return
        }

        if (version <= 10) {
            // If less than v10, if apple disabled, disable comm1 and commm2

            // If less than v11, migrate other/new setting
            Log.i(TAG, "Migration settings for release 10")
        }

        // After all migrations, set version to latest
        //updateKnownVersion(version)
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
