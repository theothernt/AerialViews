@file:Suppress("unused")

package com.neilturner.aerialviews.ui.settings

import android.content.SharedPreferences
import android.os.Bundle
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.neilturner.aerialviews.R

class AppleVideosFragment :
    PreferenceFragmentCompat(),
    SharedPreferences.OnSharedPreferenceChangeListener {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings_apple_videos, rootKey)
        preferenceManager.sharedPreferences?.registerOnSharedPreferenceChangeListener(this)
        updateSummaries()
    }

    override fun onDestroy() {
        preferenceManager.sharedPreferences?.unregisterOnSharedPreferenceChangeListener(this)
        super.onDestroy()
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String) {
        updateSummaries()
    }

    private fun updateSummaries() {
        val quality = findPreference<ListPreference>("apple_videos_quality") as ListPreference
        val qualityTitle = context?.getString(R.string.apple_videos_quality_title)
        quality.title = "$qualityTitle - ${quality.entry}"

        val dataUsage = findPreference<Preference>("apple_videos_data_usage") as Preference
        val index = quality.findIndexOfValue(quality.value)
        val bitrates = context?.resources?.getStringArray(R.array.apple_videos_data_usage_values)
        val bitrate = bitrates?.get(index)

        dataUsage.summary = "Approx. $bitrate per hour"
    }
}
