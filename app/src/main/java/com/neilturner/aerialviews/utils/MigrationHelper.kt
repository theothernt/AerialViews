package com.neilturner.aerialviews.utils

import android.content.Context
import com.neilturner.aerialviews.BuildConfig
import timber.log.Timber

@Suppress("SameParameterValue")
class MigrationHelper(
    val context: Context,
) {
    private val prefsPackageName = "${context.packageName}_preferences"
    private val prefs = context.getSharedPreferences(prefsPackageName, Context.MODE_PRIVATE)

    fun upgradeSettings() {
        val latestVersion = BuildConfig.VERSION_CODE
        val lastKnownVersion = getLastKnownVersion()

        Timber.i("Build code $latestVersion, Last known version $lastKnownVersion")

        // If package not updated, exit early
        if (!PackageHelper.isPackageUpdate(context)) {
            Timber.i("Package not updated, no migration needed")
            return
        } else {
            Timber.i("Package updated, checking if migration is needed")
        }

        if (lastKnownVersion == latestVersion) {
            Timber.i("Package updated but already migrated")
            return
        }

        if (lastKnownVersion < 10) release10()
        if (lastKnownVersion < 11) release11()
        if (lastKnownVersion < 13) release13()
        if (lastKnownVersion < 14) release14()
        if (lastKnownVersion < 15) release15()
        if (lastKnownVersion < 17) release17()
        if (lastKnownVersion < 19) release19()
        if (lastKnownVersion < 20) release20()
        if (lastKnownVersion < 22) release22()
        if (lastKnownVersion < 23) release23()
        if (lastKnownVersion < 24) release24()

        // After all migrations, set version to latest
        updateKnownVersion(latestVersion)
    }

    private fun release10() {
        Timber.i("Migrating settings for release 10")

        // Covers case when Apple videos are disabled but Community videos are not
        val communityVideosEnabled =
            prefs.contains("comm1_videos_enabled") ||
                prefs.contains("comm2_videos_enabled")
        val appleVideosEnabled = prefs.getBoolean("apple_videos_enabled", false)

        if (!appleVideosEnabled && !communityVideosEnabled) {
            Timber.i("Disabling new community videos")
            prefs.edit().putBoolean("comm1_videos_enabled", false).apply()
            prefs.edit().putBoolean("comm2_videos_enabled", false).apply()
            return
        } else {
            Timber.i("Leaving community videos enabled")
        }

        val videoQuality = prefs.getString("apple_videos_quality", "").toStringOrEmpty()
        if (videoQuality.contains("4K", true) &&
            appleVideosEnabled
        ) {
            Timber.i("Setting community videos to 4K")
            prefs.edit().putString("comm1_videos_quality", "VIDEO_4K_SDR").apply()
            prefs.edit().putString("comm2_videos_quality", "VIDEO_4K_SDR").apply()
        } else {
            Timber.i("Not setting community videos to 4K")
        }
    }

    private fun release11() {
        Timber.i("Migrating settings for release 11")

        // Setting key will exist if changed or if user has visited that settings fragment/ui
        val oldLocationSetting = prefs.contains("show_location")
        if (!oldLocationSetting) {
            Timber.i("Old location setting does not exist, no need to migrate")
            return
        }

        val locationEnabled = prefs.getBoolean("show_location", true)
        val locationType = prefs.getString("show_location_style", "VERBOSE").toStringOrEmpty()

        Timber.i("Remove old video location pref and set new POI default")
        prefs.edit().remove("show_location").apply()
        prefs.edit().putString("location_style", "POI").apply()

        if (locationEnabled) {
            if (locationType.contains("SHORT")) {
                Timber.i("Set video location to title")
                prefs.edit().putString("location_style", "TITLE").apply()
            }
        } else {
            Timber.i("Set video location to off")
            prefs.edit().putString("location_style", "OFF").apply()
        }
    }

    private fun release13() {
        Timber.i("Migrating settings for release 13")

        val sambaUsed = prefs.contains("network_videos_enabled")
        if (sambaUsed) {
            Timber.i("Migrating samba settings/keys")
            prefs.edit().putBoolean("samba_videos_enabled", prefs.getBoolean("network_videos_enabled", false)).apply()
            prefs.edit().putBoolean("samba_videos_enable_encryption", prefs.getBoolean("network_videos_enable_encryption", false)).apply()
            prefs.edit().putString("samba_videos_username", prefs.getString("network_videos_username", "")).apply()
            prefs.edit().putString("samba_videos_password", prefs.getString("network_videos_password", "")).apply()
            prefs.edit().putString("samba_videos_hostname", prefs.getString("network_videos_hostname", "")).apply()
            prefs.edit().putString("samba_videos_sharename", prefs.getString("network_videos_sharename", "")).apply()
            prefs.edit().putString("samba_videos_domainname", prefs.getString("network_videos_domainname", "WORKGROUP")).apply()
            prefs.edit().putStringSet("samba_videos_smb_dialects", prefs.getStringSet("network_videos_smb_dialects", emptySet())).apply()

            Timber.i("Deleting old network settings/keys")
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
        Timber.i("Migrating settings for release 14")

        val filterFolderUsed = prefs.contains("local_videos_filter_folder_name")
        if (filterFolderUsed) {
            Timber.i("Migrating samba settings/keys")
            prefs.edit().putString("local_videos_media_store_filter_folder", prefs.getString("local_videos_filter_folder_name", "")).apply()
            prefs.edit().remove("local_videos_filter_folder_name").apply()
        }
    }

    private fun release15() {
        Timber.i("Migrating settings for release 15")

        val filenameAsLocationUsed = prefs.contains("any_videos_filename_location")
        if (filenameAsLocationUsed) {
            val filenameAsLocationEnabled = prefs.getBoolean("any_videos_filename_location", false)
            if (filenameAsLocationEnabled) {
                Timber.i("Migrating filename as location setting/key")
                prefs.edit().putString("filename_as_location", "FORMATTED").apply()
            }
            prefs.edit().remove("any_videos_filename_location").apply()
        }
    }

    private fun release17() {
        Timber.i("Migrating settings for release 17")

        // Location
        val locationUsed = prefs.contains("location_style")
        if (locationUsed) {
            val locationStyle = prefs.getString("location_style", "POI").toStringOrEmpty()
            if (locationStyle.contains("OFF")) {
                Timber.i("Location disabled so removing overlay from default slot")
                prefs.edit().putString("location_style", "POI").apply()
                prefs.edit().putString("slot_bottom_right1", "EMPTY").apply()
            } else {
                Timber.i("No change to location as default is used")
                prefs.edit().putString("slot_bottom_right1", "LOCATION").apply()
            }
        }

        // Clock
        val clockUsed = prefs.contains("show_clock")
        if (clockUsed) {
            val clockEnabled = prefs.getBoolean("show_clock", false)
            if (!clockEnabled) {
                Timber.i("Clock disabled so removing overlay from default slot")
                prefs.edit().putString("slot_bottom_left1", "EMPTY").apply()
            } else {
                Timber.i("Set new clock prefs")
                prefs.edit().putString("slot_bottom_left1", "CLOCK").apply()
                val textSize = prefs.getString("clock_size", "18")
                if (textSize == "36") {
                    Timber.i("Clock text size at old default, updating to new size")
                    prefs.edit().putString("clock_size", "18").apply()
                } else {
                    Timber.i("Clock text size is custom, leaving alone")
                }
            }
        }
    }

    private fun release19() {
        Timber.i("Migrating settings for release 19")

        // Remove bad key if found
        val locationUsed = prefs.contains("location_style")
        if (locationUsed) {
            val locationStyle = prefs.getString("location_style", "POI").toStringOrEmpty()
            if (!locationStyle.contains("POI") &&
                !locationStyle.contains("TITLE")
            ) {
                Timber.i("Setting location style to default/POI and setting slot to empty")
                prefs.edit().putString("location_style", "POI").apply()
                prefs.edit().putString("slot_bottom_right1", "EMPTY").apply()
            }
        }
    }

    private fun release20() {
        Timber.i("Migrating settings for release 20")

        val fontWeightUsed = prefs.contains("font_weight")
        if (fontWeightUsed) {
            val fontWeight = prefs.getString("font_weight", "300").toStringOrEmpty()
            if (fontWeight != "300" && fontWeight.toDoubleOrNull() != null) {
                Timber.i("Setting new font weight to all overlays")
                prefs.edit().putString("clock_weight", fontWeight).apply()
                prefs.edit().putString("date_weight", fontWeight).apply()
                prefs.edit().putString("location_weight", fontWeight).apply()
                prefs.edit().putString("message_weight", fontWeight).apply()
            }
        }
    }

    private fun release22() {
        Timber.i("Migrating settings for release 22")

        val locationStyleUsed = prefs.contains("location_style")
        if (locationStyleUsed) {
            val locationStyle = prefs.getString("location_style", "POI")
            if (locationStyle == "TITLE") {
                Timber.i("Updating description manifest style to TITLE")
                prefs.edit().putString("description_video_manifest_style", "TITLE").apply()
            }
            prefs.edit().remove("location_style").apply()
        }

        val filenameAsLocationUsed = prefs.contains("filename_as_location")
        if (filenameAsLocationUsed) {
            val filenameAsLocation = prefs.getString("filename_as_location", "DISABLED")
            if (filenameAsLocation != "DISABLED") {
                Timber.i("Updating description video/photo style to FILENAME")
                prefs.edit().putString("description_video_filename_style", "FILENAME").apply()
                prefs.edit().putString("description_photo_filename_style", "FILENAME").apply()
            }
            prefs.edit().remove("filename_as_location").apply()
        }

        val locationSizeUsed = prefs.contains("location_size")
        if (locationSizeUsed) {
            Timber.i("Updating description size")
            val locationSize = prefs.getString("location_size", "18")
            prefs.edit().putString("description_size", locationSize).apply()
            prefs.edit().remove("location_size").apply()
        }

        val locationWeightUsed = prefs.contains("location_weight")
        if (locationWeightUsed) {
            Timber.i("Updating description weight")
            val locationWeight = prefs.getString("location_weight", "18")
            prefs.edit().putString("description_weight", locationWeight).apply()
            prefs.edit().remove("location_weight").apply()
        }
    }

    private fun release23() {
        Timber.i("Migrating settings for release 23")

        val skipVideosUsed = prefs.contains("enable_skip_videos")
        if (skipVideosUsed) {
            Timber.i("Updating dpad skip videos")
            prefs.edit().putString("button_left_press", "SKIP_PREVIOUS").apply()
            prefs.edit().putString("button_right_press", "SKIP_NEXT").apply()
            prefs.edit().remove("enable_skip_videos").apply()
        }

        val playbackSpeedChangeUsed = prefs.contains("enable_playback_speed_change")
        if (playbackSpeedChangeUsed) {
            Timber.i("Updating dpad speed change")
            prefs.edit().putString("button_up_press", "SPEED_INCREASE").apply()
            prefs.edit().putString("button_down_press", "SPEED_DECREASE").apply()
            prefs.edit().remove("enable_playback_speed_change").apply()
        }
    }

    private fun release24() {
        Timber.i("Migrating settings for release 24")

        val playbackSpeedUsed = prefs.contains("playback_speed")
        if (playbackSpeedUsed) {
            val speed = prefs.getString("playback_speed", "1")
            val speedMapping = mapOf(
                "0.25" to "0.2",
                "0.50" to "0.4",
                "0.75" to "0.8",
                "1.25" to "1.2",
                "1.50" to "1.4",
                "1.75" to "1.8",
            )

            // Ignore default
            if (speed?.contains("1", true) == true) {
                return
            }

            var found = false
            speedMapping.forEach { (oldSpeed, newSpeed) ->
                if (speed == oldSpeed) {
                    prefs.edit().putString("playback_speed", newSpeed).apply()
                    found = true
                }
            }

            if (!found) {
                // If other value, reset the pref to default
                prefs.edit().putString("playback_speed", "1").apply()
            }
        }
    }

    // Get saved revision code or return 0
    private fun getLastKnownVersion(): Int = prefs.getInt("last_known_version", 0)

    // Update saved revision code or return 0
    private fun updateKnownVersion(versionCode: Int) {
        Timber.i("Updating last known version to $versionCode")
        prefs.edit().putInt("last_known_version", versionCode).apply()
    }
}
