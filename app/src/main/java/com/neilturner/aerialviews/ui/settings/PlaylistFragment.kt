package com.neilturner.aerialviews.ui.settings

import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import androidx.preference.CheckBoxPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.utils.FirebaseHelper
import com.neilturner.aerialviews.utils.MenuStateFragment
import kotlinx.coroutines.launch

class PlaylistFragment : MenuStateFragment() {
    override fun onCreatePreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        setPreferencesFromResource(R.xml.settings_playlist, rootKey)
    }

    override fun onResume() {
        super.onResume()
        FirebaseHelper.analyticsScreenView("Playlist", this)
        updateAllSummaries()
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

        val autoTimeOfDayPref = findPreference<CheckBoxPreference>("playlist_auto_time_of_day")
        autoTimeOfDayPref?.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { _, newValue ->
                updateLocationEnabledState(newValue as Boolean)
                true
            }

        updateLocationEnabledState(autoTimeOfDayPref?.isChecked ?: false)

        val randomStartPref = findPreference<ListPreference>("random_start_position_range")
        randomStartPref?.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { _, newValue ->
                randomStartPref.summary = "0%% - $newValue%%"
                true
            }
        randomStartPref?.summary = "0%% - ${randomStartPref.value}%%"

        setupLocationPreference()
    }

    private fun updateLocationEnabledState(enabled: Boolean) {
        val locationPreference = findPreference<Preference>("weather_location_name")
        locationPreference?.isEnabled = enabled
    }

    private fun setupLocationPreference() {
        updateLocationSummary()
    }

    private fun updateLocationSummary() {
        val locationPreference = findPreference<Preference>("weather_location_name")
        locationPreference?.summary =
            getString(
                R.string.playlist_set_location_summary,
                GeneralPrefs.weatherLocationName.ifEmpty { getString(R.string.location_not_set) },
            )
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
