@file:Suppress("UNCHECKED_CAST")

package com.neilturner.aerialviews.ui.sources

import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import androidx.preference.ListPreference
import androidx.preference.MultiSelectListPreference
import androidx.preference.Preference
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.models.prefs.AppleVideoPrefs
import com.neilturner.aerialviews.providers.AppleMediaProvider
import com.neilturner.aerialviews.utils.MenuStateFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppleVideosFragment : MenuStateFragment() {
    override fun onCreatePreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        setPreferencesFromResource(R.xml.sources_apple_videos, rootKey)
        updateQualityEntriesWithDataUsage()
        updateSummary()
        updateVideoCount()
    }

    private fun updateQualityEntriesWithDataUsage() {
        val quality = findPreference<ListPreference>("apple_videos_quality") ?: return
        val res = context?.resources ?: return
        val qualityEntries = res.getStringArray(R.array.apple_videos_quality_entries)
        val dataUsageValues = res.getStringArray(R.array.apple_videos_data_usage_values)

        val combinedEntries = qualityEntries.mapIndexed { index, qualityEntry ->
            if (index < dataUsageValues.size) {
                "$qualityEntry (${dataUsageValues[index]} per hour)"
            } else {
                qualityEntry
            }
        }.toTypedArray()

        quality.entries = combinedEntries

        // Update video count when quality changes
        quality.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { _, _ ->
                lifecycleScope.launch {
                    delay(100)
                    updateVideoCount(forceRecalculate = true)
                }
                true
            }
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
        val enabledSwitch = findPreference<Preference>("apple_videos_enabled") ?: return
        val ctx = context ?: return

        lifecycleScope.launch {
            // Check if we have a valid cached count
            val cachedCount = AppleVideoPrefs.count.toIntOrNull()
            val count = if (!forceRecalculate && cachedCount != null && cachedCount != -1) {
                // Use cached value
                cachedCount
            } else {
                // Recalculate and cache
                withContext(Dispatchers.IO) {
                    try {
                        val provider = AppleMediaProvider(ctx, AppleVideoPrefs)
                        val videoCount = provider.fetchMedia().size
                        AppleVideoPrefs.count = videoCount.toString()
                        videoCount
                    } catch (e: Exception) {
                        0
                    }
                }
            }

            enabledSwitch.summary = ctx.getString(R.string.apple_videos_count, count)
        }
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
