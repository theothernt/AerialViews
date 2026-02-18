package com.neilturner.aerialviews.ui.settings

import android.content.SharedPreferences
import android.os.Bundle
import androidx.preference.ListPreference
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.utils.FirebaseHelper
import com.neilturner.aerialviews.utils.MenuStateFragment

class OverlaysMetadataSlotFragment : MenuStateFragment() {
    private val folderFieldValues = setOf("FOLDER_FILENAME", "FOLDER_ONLY")

    private val prefChangeListener =
        SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
            if (key == "overlay_metadata1_videos" || key == "overlay_metadata1_photos") {
                updateFolderLevelVisibility(sharedPreferences)
            }
        }

    override fun onCreatePreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        setPreferencesFromResource(R.xml.settings_overlays_metadata_slot, rootKey)
    }

    override fun onResume() {
        super.onResume()
        FirebaseHelper.analyticsScreenView("Metadata Slot", this)
        preferenceManager.sharedPreferences?.let {
            updateFolderLevelVisibility(it)
            it.registerOnSharedPreferenceChangeListener(prefChangeListener)
        }
    }

    override fun onPause() {
        preferenceManager.sharedPreferences?.unregisterOnSharedPreferenceChangeListener(prefChangeListener)
        super.onPause()
    }

    private fun updateFolderLevelVisibility(sharedPreferences: SharedPreferences) {
        val videoSelection = sharedPreferences.getString("overlay_metadata1_videos", "").orEmpty()
        val photoSelection = sharedPreferences.getString("overlay_metadata1_photos", "").orEmpty()

        findPreference<ListPreference>("description_video_folder_levels")
            ?.isVisible = containsFolderLevelValue(videoSelection)
        findPreference<ListPreference>("description_photo_folder_levels")
            ?.isVisible = containsFolderLevelValue(photoSelection)
    }

    private fun containsFolderLevelValue(selection: String): Boolean =
        selection
            .split(',')
            .map { it.trim() }
            .any { folderFieldValues.contains(it) }
}
