@file:Suppress("unused")

package com.neilturner.aerialviews.ui.settings.sources

import android.content.SharedPreferences
import android.os.Bundle
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.neilturner.aerialviews.R

class AerialShotsVideosFragment : PreferenceFragmentCompat(),
    SharedPreferences.OnSharedPreferenceChangeListener {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings_aerial_shots_videos, rootKey)
        preferenceManager.sharedPreferences?.registerOnSharedPreferenceChangeListener(this)
        updateSummaries()
    }

    override fun onDestroy() {
        super.onDestroy()
        preferenceManager.sharedPreferences?.unregisterOnSharedPreferenceChangeListener(this)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String) {
        updateSummaries()
    }

    private fun updateSummaries() {
        val res = context?.resources!!
        val quality = findPreference<ListPreference>("aerial_shots_videos_quality")
        val qualityTitle = res.getString(R.string.apple_videos_quality_title)
        quality?.title = "$qualityTitle - ${quality?.entry}"
    }
}
