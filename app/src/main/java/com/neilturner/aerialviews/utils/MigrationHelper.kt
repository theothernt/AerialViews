package com.neilturner.aerialviews.utils

import android.content.Context
import android.util.Log
import com.neilturner.aerialviews.BuildConfig

@Suppress("SameParameterValue")
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
        // if (lastKnownVersion < 12) release12()
        if (lastKnownVersion < 13) release13()
        if (lastKnownVersion < 14) release14()
        if (lastKnownVersion < 15) release15()
        // if (lastKnownVersion < 16) release16()
        if (lastKnownVersion < 17) release17()

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

    private fun release13() {
        Log.i(TAG, "Migrating settings for release 13")

        val sambaUsed = prefs.contains("network_videos_enabled")
        if (sambaUsed) {
            Log.i(TAG, "Migrating samba settings/keys")
            prefs.edit().putBoolean("samba_videos_enabled", prefs.getBoolean("network_videos_enabled", false)).apply()
            prefs.edit().putBoolean("samba_videos_enable_encryption", prefs.getBoolean("network_videos_enable_encryption", false)).apply()
            prefs.edit().putString("samba_videos_username", prefs.getString("network_videos_username", "")).apply()
            prefs.edit().putString("samba_videos_password", prefs.getString("network_videos_password", "")).apply()
            prefs.edit().putString("samba_videos_hostname", prefs.getString("network_videos_hostname", "")).apply()
            prefs.edit().putString("samba_videos_sharename", prefs.getString("network_videos_sharename", "")).apply()
            prefs.edit().putString("samba_videos_domainname", prefs.getString("network_videos_domainname", "WORKGROUP")).apply()
            prefs.edit().putStringSet("samba_videos_smb_dialects", prefs.getStringSet("network_videos_smb_dialects", emptySet())).apply()

            Log.i(TAG, "Deleting old network settings/keys")
            prefs.edit().remove("network_videos_enable_encryption").apply()
            prefs.edit().remove("network_videos_username").apply()
            prefs.edit().remove("network_videos_password").apply()
            prefs.edit().remove("network_videos_hostname").apply()
            prefs.edit().remove("network_videos_sharename").apply()
            prefs.edit().remove("network_videos_domainname").apply()
            prefs.edit().remove("network_videos_smb_dialects").apply()
            prefs.edit().remove("network_videos_enabled").apply()
        }
    }

    private fun release14() {
        Log.i(TAG, "Migrating settings for release 14")

        val filterFolderUsed = prefs.contains("local_videos_filter_folder_name")
        if (filterFolderUsed) {
            Log.i(TAG, "Migrating samba settings/keys")
            prefs.edit().putString("local_videos_media_store_filter_folder", prefs.getString("local_videos_filter_folder_name", "")).apply()
            prefs.edit().remove("local_videos_filter_folder_name").apply()
        }
    }

    private fun release15() {
        Log.i(TAG, "Migrating settings for release 15")

        val filenameAsLocationUsed = prefs.contains("any_videos_filename_location")
        if (filenameAsLocationUsed) {
            val filenameAsLocationEnabled = prefs.getBoolean("any_videos_filename_location", false)
            if (filenameAsLocationEnabled) {
                Log.i(TAG, "Migrating filename as location setting/key")
                prefs.edit().putString("filename_as_location", "FORMATTED").apply()
            }
            prefs.edit().remove("any_videos_filename_location").apply()
        }
    }

    private fun release17() {
        Log.i(TAG, "Migrating settings for release 17")

        // Location
        val locationUsed = prefs.contains("location_style")
        if (locationUsed) {
            val locationStyle = prefs.getString("location_style", "POI").toStringOrEmpty()
            if (locationStyle.contains("DISABLED")) {
                Log.i(TAG, "Location disabled so removing overlay from default slot")
                prefs.edit().putString("location_style", "POI").apply()
                prefs.edit().putString("slot_bottom_right1", "EMPTY").apply()
            } else {
                Log.i(TAG, "No change to location as default is used")
            }
        }

        // Clock
        val clockUsed = prefs.contains("show_clock")
        if (clockUsed) {
            val clockEnabled = prefs.getBoolean("show_clock", false)
            if (!clockEnabled) {
                Log.i(TAG, "Clock disabled so removing overlay from default slot")
                prefs.edit().putString("slot_bottom_left1", "EMPTY").apply()
            } else {
                Log.i(TAG, "Set new clock prefs")
                val textSize = prefs.getString("clock_size", "18")
                if (textSize == "36") {
                    Log.i(TAG, "Clock text size at old default, updating to new size")
                    prefs.edit().putString("clock_size", "18").apply()
                } else {
                    Log.i(TAG, "Clock text size is custom, leaving alone")
                }
            }
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
