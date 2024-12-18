@file:Suppress("SameParameterValue")

package com.neilturner.aerialviews.ui.settings

import android.os.Bundle
import androidx.preference.CheckBoxPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.utils.FirebaseHelper
import com.neilturner.aerialviews.utils.MenuStateFragment

class PlaylistAdvancedVideoFragment : MenuStateFragment() {
    override fun onCreatePreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        setPreferencesFromResource(R.xml.settings_playlist_advanced_video, rootKey)
        updateAllSummaries()
    }

    override fun onResume() {
        super.onResume()
        FirebaseHelper.logScreenView("Advanced Video", this)
    }

    private fun updateAllSummaries() {
        val maxLengthPref = findPreference<ListPreference>("playback_max_video_length")
        maxLengthPref?.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { _, newValue ->
                toggleControls(newValue as String)
                true
            }
        toggleControls(maxLengthPref?.value as String)
        setupSummaryUpdater("limit_longer_videos", R.array.limit_Longer_videos_summaries)

        val randomStartPref = findPreference<ListPreference>("random_start_position_range")
        randomStartPref?.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { _, newValue ->
                randomStartPref.summary = "0%% - $newValue%%"
                true
            }
        randomStartPref?.summary = "0%% - ${randomStartPref.value}%%"
    }

    private fun toggleControls(value: String) {
        val shortVideosPref = findPreference<CheckBoxPreference>("loop_short_videos")
        val longVideosPref = findPreference<ListPreference>("limit_longer_videos")
        val randomStartPref = findPreference<CheckBoxPreference>("random_start_position")

        if (value == "0") {
            shortVideosPref?.isEnabled = false
            longVideosPref?.isEnabled = false
            randomStartPref?.isEnabled = true
        } else {
            shortVideosPref?.isEnabled = true
            longVideosPref?.isEnabled = true
            randomStartPref?.isEnabled = false
        }
    }

    private fun setupSummaryUpdater(
        control: String,
        entries: Int,
    ) {
        val pref = findPreference<ListPreference>(control)
        pref?.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { _, newValue ->
                updateSummary(control, entries, pref.findIndexOfValue(newValue as String))
                true
            }
        pref?.findIndexOfValue(pref.value)?.let { updateSummary(control, entries, it) }
    }

    private fun updateSummary(
        control: String,
        entries: Int,
        index: Int,
    ) {
        val res = requireContext().resources
        val pref = findPreference<Preference>(control)
        val summaries = res?.getStringArray(entries)
        val summary = summaries?.elementAtOrNull(index) ?: ""
        pref?.summary = summary
    }
}
