package com.neilturner.aerialviews.ui.settings

import android.content.SharedPreferences
import android.os.Bundle
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.models.enums.DateType
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.utils.DateHelper
import com.neilturner.aerialviews.utils.FirebaseHelper
import com.neilturner.aerialviews.utils.MenuStateFragment

class OverlaysMetadataSlot2Fragment : MenuStateFragment() {
    private val folderFieldValues = setOf("FOLDER_FILENAME", "FOLDER_ONLY")
    private val locationFieldValue = "LOCATION"
    private val dateTakenFieldValue = "DATE_TAKEN"
    private lateinit var dateTypeEntries: Map<String, String>

    private val prefChangeListener =
        SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, _ ->
                updateConditionalPreferenceVisibility(sharedPreferences)
                updatePhotoDateFormatSummary(sharedPreferences)
        }

    override fun onCreatePreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        setPreferencesFromResource(R.xml.settings_overlays_metadata_slot2, rootKey)
        dateTypeEntries = findEntriesAndValues(R.array.date_format_values, R.array.date_format_entries)
        findPreference<EditTextPreference>("overlay_metadata2_photo_date_custom")
            ?.setOnBindEditTextListener { it.setSingleLine() }
    }

    override fun onResume() {
        super.onResume()
        FirebaseHelper.analyticsScreenView("Metadata Slot 2", this)
        preferenceManager.sharedPreferences?.let {
            updateConditionalPreferenceVisibility(it)
            updatePhotoDateFormatSummary(it)
            it.registerOnSharedPreferenceChangeListener(prefChangeListener)
        }
    }

    override fun onPause() {
        preferenceManager.sharedPreferences?.unregisterOnSharedPreferenceChangeListener(prefChangeListener)
        super.onPause()
    }

    private fun updateConditionalPreferenceVisibility(sharedPreferences: SharedPreferences) {
        val videoSelection = sharedPreferences.getString("overlay_metadata2_videos", "").orEmpty()
        val photoSelection = sharedPreferences.getString("overlay_metadata2_photos", "").orEmpty()
        val showDatePrefs = containsFieldValue(photoSelection, dateTakenFieldValue)
        val dateType = GeneralPrefs.overlayMetadata1PhotosDateType ?: DateType.COMPACT

        findPreference<ListPreference>("overlay_metadata2_video_folder_levels")
            ?.isVisible = containsFolderLevelValue(videoSelection)
        findPreference<ListPreference>("overlay_metadata2_photo_folder_levels")
            ?.isVisible = containsFolderLevelValue(photoSelection)
        findPreference<ListPreference>("overlay_metadata2_photo_location_type")
            ?.isVisible = containsFieldValue(photoSelection, locationFieldValue)
        findPreference<ListPreference>("overlay_metadata2_photo_date_type")
            ?.isVisible = showDatePrefs
        findPreference<EditTextPreference>("overlay_metadata2_photo_date_custom")
            ?.isVisible = showDatePrefs && dateType == DateType.CUSTOM
    }

    private fun updatePhotoDateFormatSummary(sharedPreferences: SharedPreferences) {
        val customValue = sharedPreferences.getString("overlay_metadata2_photo_date_custom", "yyyy-MM-dd").orEmpty()
        val dateType = GeneralPrefs.overlayMetadata1PhotosDateType ?: DateType.COMPACT

        findPreference<ListPreference>("overlay_metadata2_photo_date_type")
            ?.summary = dateTypeSummary(dateType, customValue)

        findPreference<EditTextPreference>("overlay_metadata2_photo_date_custom")
            ?.summary = customDateSummary(customValue)
    }

    private fun dateTypeSummary(
        dateType: DateType,
        customFormat: String,
    ): String {
        val forExample = getString(R.string.appearance_date_custom_example)
        return when (dateType) {
            DateType.CUSTOM -> {
                val format = customFormat.ifBlank { "yyyy-MM-dd" }
                val example = DateHelper.formatDate(requireContext(), DateType.CUSTOM, format)
                "${dateTypeEntries[DateType.CUSTOM.toString()]}: $forExample $example ($format)"
            }

            else -> {
                val example = DateHelper.formatDate(requireContext(), dateType, null)
                "${dateTypeEntries[dateType.toString()]} ($forExample $example)"
            }
        }
    }

    private fun customDateSummary(formatInput: String): String {
        val format = formatInput.ifBlank { "yyyy-MM-dd" }
        val example = DateHelper.formatDate(requireContext(), DateType.CUSTOM, format)
        val prefix = getString(R.string.appearance_date_custom_example)
        return "$prefix $example ($format)"
    }

    private fun findEntriesAndValues(
        valuesId: Int,
        entriesId: Int,
    ): Map<String, String> {
        val res = requireContext().resources
        val values = res.getStringArray(valuesId)
        val entries = res.getStringArray(entriesId)
        return values.zip(entries).toMap()
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
}
