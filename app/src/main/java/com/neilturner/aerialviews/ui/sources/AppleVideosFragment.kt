@file:Suppress("UNCHECKED_CAST")

package com.neilturner.aerialviews.ui.sources

import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import androidx.preference.MultiSelectListPreference
import androidx.preference.Preference
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.models.prefs.AppleVideoPrefs
import com.neilturner.aerialviews.providers.AppleMediaProvider
import com.neilturner.aerialviews.utils.MediaPreferenceHelper
import com.neilturner.aerialviews.utils.MenuStateFragment
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AppleVideosFragment : MenuStateFragment() {
    override fun onCreatePreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        setPreferencesFromResource(R.xml.sources_apple_videos, rootKey)
        MediaPreferenceHelper.setupQualityWithDataUsage(
            fragment = this,
            qualityPrefKey = "apple_videos_quality",
            qualityEntriesArrayId = R.array.apple_videos_quality_entries,
            dataUsageValuesArrayId = R.array.apple_videos_data_usage_values,
            scope = lifecycleScope,
            onChangeCallback = { updateVideoCount(forceRecalculate = true) },
        )
        updateSummary()
        updateVideoCount()
    }

    private fun updateSummary() {
        val sceneType = findPreference<MultiSelectListPreference>("apple_videos_scene_type")
        sceneType?.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { preference, newValue ->
                updateMultiSelectSummary(preference as MultiSelectListPreference, newValue as Set<String>)
                lifecycleScope.launch {
                    delay(100)
                    updateVideoCount(forceRecalculate = true)
                }
                true
            }
        sceneType?.let { updateMultiSelectSummary(it, it.values) }

        val timeOfDay = findPreference<MultiSelectListPreference>("apple_videos_time_of_day")
        timeOfDay?.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { preference, newValue ->
                updateMultiSelectSummary(preference as MultiSelectListPreference, newValue as Set<String>)
                lifecycleScope.launch {
                    delay(100)
                    updateVideoCount(forceRecalculate = true)
                }
                true
            }
        timeOfDay?.let { updateMultiSelectSummary(it, it.values) }
    }

    private fun updateVideoCount(forceRecalculate: Boolean = false) {
        MediaPreferenceHelper.updateMediaCount(
            fragment = this,
            targetPrefKey = "apple_videos_enabled",
            countStringId = R.string.videos_count,
            scope = lifecycleScope,
            getCachedCount = { AppleVideoPrefs.count },
            setCachedCount = { AppleVideoPrefs.count = it },
            fetchMediaCount = { ctx ->
                val provider = AppleMediaProvider(ctx, AppleVideoPrefs)
                provider.fetchMedia().size
            },
            forceRecalculate = forceRecalculate,
        )
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
