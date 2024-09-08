package com.neilturner.aerialviews.ui.settings

import android.os.Bundle
import androidx.preference.CheckBoxPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.utils.FirebaseHelper

class PlaylistFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        setPreferencesFromResource(R.xml.settings_playlist, rootKey)
        updateAllSummaries()
    }

    override fun onResume() {
        super.onResume()
        FirebaseHelper.logScreenView("Playlist", this)
    }

    private fun updateAllSummaries() {
        val editPref = findPreference<ListPreference>("playback_max_video_length")
        editPref?.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { _, newValue ->
                toggleVideoLimitControls(newValue as String)
                true
            }
        toggleVideoLimitControls(editPref?.value as String)

        setupSummaryUpdater("limit_longer_videos", R.array.limit_Longer_videos_summaries)
    }

    private fun toggleVideoLimitControls(value: String) {
        val shortVideosPref = findPreference<CheckBoxPreference>("loop_short_videos")
        val longVideosPref = findPreference<ListPreference>("limit_longer_videos")

        if (value == "0") {
            shortVideosPref?.isEnabled = false
            longVideosPref?.isEnabled = false
        } else {
            shortVideosPref?.isEnabled = true
            longVideosPref?.isEnabled = true
        }
    }

    private fun setupSummaryUpdater(
        control: String,
        entries: Int,
    ) {
        val pref = findPreference<ListPreference>(control)
        pref?.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { _, newValue ->
                pref?.findIndexOfValue(newValue as String)?.let { updateSummary(control, entries, it) }
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
