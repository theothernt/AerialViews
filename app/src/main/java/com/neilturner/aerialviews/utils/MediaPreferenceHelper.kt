package com.neilturner.aerialviews.utils

import android.content.Context
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Helper class for common media preference operations
 */
object MediaPreferenceHelper {
    /**
     * Updates a quality preference by combining quality entries with data usage values
     * @param fragment The preference fragment
     * @param qualityPrefKey The preference key for the quality setting
     * @param qualityEntriesArrayId Resource ID for quality entries
     * @param dataUsageValuesArrayId Resource ID for data usage values
     * @param scope The lifecycle coroutine scope
     * @param onChangeCallback Optional callback when quality changes
     */
    fun setupQualityWithDataUsage(
        fragment: PreferenceFragmentCompat,
        qualityPrefKey: String,
        qualityEntriesArrayId: Int,
        dataUsageValuesArrayId: Int,
        scope: LifecycleCoroutineScope,
        onChangeCallback: (() -> Unit)? = null,
    ) {
        val quality = fragment.findPreference<ListPreference>(qualityPrefKey) ?: return
        val res = fragment.context?.resources ?: return
        val qualityEntries = res.getStringArray(qualityEntriesArrayId)
        val dataUsageValues = res.getStringArray(dataUsageValuesArrayId)

        val combinedEntries =
            qualityEntries
                .mapIndexed { index, qualityEntry ->
                    if (index < dataUsageValues.size) {
                        "$qualityEntry (${dataUsageValues[index]} per hour)"
                    } else {
                        qualityEntry
                    }
                }.toTypedArray()

        quality.entries = combinedEntries

        if (onChangeCallback != null) {
            quality.onPreferenceChangeListener =
                Preference.OnPreferenceChangeListener { _, _ ->
                    scope.launch {
                        delay(100)
                        onChangeCallback()
                    }
                    true
                }
        }
    }

    /**
     * Updates a media count on a preference summary with caching support
     * @param fragment The preference fragment
     * @param targetPrefKey The preference key to update with the count
     * @param countStringId Resource ID for the count string format
     * @param scope The lifecycle coroutine scope
     * @param getCachedCount Lambda to get the cached count value
     * @param setCachedCount Lambda to set the cached count value
     * @param fetchMediaCount Lambda to fetch the actual media count
     * @param forceRecalculate Whether to force recalculation ignoring cache
     */
    fun updateMediaCount(
        fragment: PreferenceFragmentCompat,
        targetPrefKey: String,
        countStringId: Int,
        scope: LifecycleCoroutineScope,
        getCachedCount: () -> String,
        setCachedCount: (String) -> Unit,
        fetchMediaCount: suspend (Context) -> Int,
        forceRecalculate: Boolean = false,
    ) {
        val targetPref = fragment.findPreference<Preference>(targetPrefKey) ?: return
        val ctx = fragment.context ?: return

        scope.launch {
            // Check if we have a valid cached count
            val cachedCount = getCachedCount().toIntOrNull()
            val count =
                if (!forceRecalculate && cachedCount != null && cachedCount != -1) {
                    // Use cached value
                    cachedCount
                } else {
                    // Recalculate and cache
                    withContext(Dispatchers.IO) {
                        try {
                            val mediaCount = fetchMediaCount(ctx)
                            setCachedCount(mediaCount.toString())
                            mediaCount
                        } catch (e: Exception) {
                            Timber.e(e, "Error fetching media count")
                            0
                        }
                    }
                }

            targetPref.summary = ctx.getString(countStringId, count)
        }
    }
}
