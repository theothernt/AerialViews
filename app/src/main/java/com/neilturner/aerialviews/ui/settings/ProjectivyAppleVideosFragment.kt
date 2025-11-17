@file:Suppress("UNCHECKED_CAST")

package com.neilturner.aerialviews.ui.settings

import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import androidx.preference.ListPreference
import androidx.preference.MultiSelectListPreference
import androidx.preference.Preference
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.services.Display
import com.neilturner.aerialviews.services.HDRFormat
import com.neilturner.aerialviews.services.getDisplay
import com.neilturner.aerialviews.utils.MediaPreferenceHelper
import com.neilturner.aerialviews.utils.MenuStateFragment
import kotlinx.coroutines.launch

class ProjectivyAppleVideosFragment : MenuStateFragment() {
    private lateinit var display: Display

    override fun onCreatePreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        setPreferencesFromResource(R.xml.settings_projectivy_apple_videos, rootKey)

        lifecycleScope.launch {
            display = getDisplay(activity)
            setupQualityPreference()
            updateDolbyVisionNoticeVisibility()
        }

        updateSummary()
    }

    private fun setupQualityPreference() {
        MediaPreferenceHelper.setupQualityWithDataUsage(
            fragment = this,
            qualityPrefKey = "projectivy_apple_videos_quality",
            qualityEntriesArrayId = R.array.apple_videos_quality_entries,
            dataUsageValuesArrayId = R.array.apple_videos_data_usage_values,
            scope = lifecycleScope,
            onChangeCallback = {
                updateDolbyVisionNoticeVisibility()
            },
        )
    }

    private fun updateDolbyVisionNoticeVisibility() {
        val qualityPref = findPreference<ListPreference>("projectivy_apple_videos_quality")
        val notice = findPreference<Preference>("projectivy_apple_videos_dolby_vision_notice")

        val selectedQuality = qualityPref?.value ?: ""
        val isDolbyVisionSelected = selectedQuality == "VIDEO_1080_HDR" || selectedQuality == "VIDEO_4K_HDR"
        val deviceSupportsDolbyVision = display.supportsHDR && display.hdrFormats.contains(HDRFormat.DOLBY_VISION)

        notice?.isVisible = isDolbyVisionSelected && !deviceSupportsDolbyVision
    }

    private fun updateSummary() {
        val sceneType = findPreference<MultiSelectListPreference>("projectivy_apple_videos_scene_type")
        sceneType?.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { preference, newValue ->
                updateMultiSelectSummary(preference as MultiSelectListPreference, newValue as Set<String>)
                true
            }
        sceneType?.let { updateMultiSelectSummary(it, it.values) }

        val timeOfDay = findPreference<MultiSelectListPreference>("projectivy_apple_videos_time_of_day")
        timeOfDay?.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { preference, newValue ->
                updateMultiSelectSummary(preference as MultiSelectListPreference, newValue as Set<String>)
                true
            }
        timeOfDay?.let { updateMultiSelectSummary(it, it.values) }
    }

    private fun updateMultiSelectSummary(
        preference: MultiSelectListPreference,
        selectedValues: Set<String>,
    ) {
        val res = context?.resources ?: return
        val entries = preference.entries
        val entryValues = preference.entryValues

        if (selectedValues.isEmpty()) {
            preference.summary = res.getString(R.string.none_selected)
            return
        }

        val selectedEntries = mutableListOf<String>()
        for (value in selectedValues) {
            val index = entryValues.indexOf(value)
            if (index >= 0 && index < entries.size) {
                selectedEntries.add(entries[index].toString())
            }
        }

        preference.summary =
            if (selectedEntries.size == entries.size) {
                res.getString(R.string.all_selected)
            } else {
                selectedEntries.joinToString(", ")
            }
    }
}
