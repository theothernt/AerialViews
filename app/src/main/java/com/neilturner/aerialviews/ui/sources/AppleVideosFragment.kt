@file:Suppress("unused")

package com.neilturner.aerialviews.ui.sources

import android.os.Bundle
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.neilturner.aerialviews.R

class AppleVideosFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.sources_apple_videos, rootKey)
        updateSummaries()
    }

    private fun updateSummaries() {
        val res = context?.resources!!
        val quality = findPreference<ListPreference>("apple_videos_quality")
        val dataUsage = findPreference<Preference>("apple_videos_data_usage")
        val index = quality?.findIndexOfValue(quality.value)
        val bitrates = res.getStringArray(R.array.apple_videos_data_usage_values)
        val bitrate = index?.let { bitrates[it] }

        dataUsage?.summary = String.format(res.getString(R.string.apple_videos_data_estimate_summary), bitrate)
    }
}
