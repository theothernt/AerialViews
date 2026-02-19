package com.neilturner.aerialviews.ui.settings

import android.content.SharedPreferences
import android.os.Bundle
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.models.enums.DateType
import com.neilturner.aerialviews.utils.DateHelper
import com.neilturner.aerialviews.utils.FirebaseHelper
import com.neilturner.aerialviews.utils.MenuStateFragment

class OverlaysMetadataSlotFragment : MenuStateFragment() {
    private val folderFieldValues = setOf("FOLDER_FILENAME", "FOLDER_ONLY")
    private val locationFieldValue = "LOCATION"
    private val dateTakenFieldValue = "DATE_TAKEN"

    private val prefChangeListener =
        SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
            if (key == "overlay_metadata1_videos" || key == "overlay_metadata1_photos") {
                updateConditionalPreferenceVisibility(sharedPreferences)
            }
        }

    override fun onCreatePreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        setPreferencesFromResource(R.xml.settings_overlays_metadata_slot, rootKey)
        val dateFormatPref = findPreference<EditTextPreference>("overlay_metadata1_photo_date_format")
        dateFormatPref?.setOnBindEditTextListener { it.setSingleLine() }
        dateFormatPref?.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { _, newValue ->
                updatePhotoDateFormatSummary((newValue as? String).orEmpty())
                true
            }
        updatePhotoDateFormatSummary(dateFormatPref?.text.orEmpty())
    }

    override fun onResume() {
        super.onResume()
        FirebaseHelper.analyticsScreenView("Metadata Slot 1", this)
        preferenceManager.sharedPreferences?.let {
            updateConditionalPreferenceVisibility(it)
            it.registerOnSharedPreferenceChangeListener(prefChangeListener)
        }
    }

    override fun onPause() {
        preferenceManager.sharedPreferences?.unregisterOnSharedPreferenceChangeListener(prefChangeListener)
        super.onPause()
    }

    private fun updateConditionalPreferenceVisibility(sharedPreferences: SharedPreferences) {
        val videoSelection = sharedPreferences.getString("overlay_metadata1_videos", "").orEmpty()
        val photoSelection = sharedPreferences.getString("overlay_metadata1_photos", "").orEmpty()

        findPreference<ListPreference>("overlay_metadata1_video_folder_levels")
            ?.isVisible = containsFolderLevelValue(videoSelection)
        findPreference<ListPreference>("overlay_metadata1_photo_folder_levels")
            ?.isVisible = containsFolderLevelValue(photoSelection)
        findPreference<ListPreference>("overlay_metadata1_photo_location_type")
            ?.isVisible = containsFieldValue(photoSelection, locationFieldValue)
        findPreference<EditTextPreference>("overlay_metadata1_photo_date_format")
            ?.isVisible = containsFieldValue(photoSelection, dateTakenFieldValue)
    }

    private fun containsFolderLevelValue(selection: String): Boolean =
        selection
            .split(',')
            .map { it.trim() }
            .any { folderFieldValues.contains(it) }

    private fun containsFieldValue(
        selection: String,
        value: String,
    ): Boolean =
        selection
            .split(',')
            .map { it.trim() }
            .any { it == value }

    private fun updatePhotoDateFormatSummary(formatInput: String) {
        val format = formatInput.ifBlank { "yyyy-MM-dd" }
        val example = DateHelper.formatDate(requireContext(), DateType.CUSTOM, format)
        val prefix = getString(R.string.appearance_date_custom_example)
        findPreference<EditTextPreference>("overlay_metadata1_photo_date_format")
            ?.summary = "$prefix $example ($format)"
    }
}
