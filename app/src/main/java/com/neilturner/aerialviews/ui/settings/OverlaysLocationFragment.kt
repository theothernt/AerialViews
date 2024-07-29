package com.neilturner.aerialviews.ui.settings

import android.os.Bundle
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.utils.LoggingHelper

class OverlaysLocationFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        setPreferencesFromResource(R.xml.settings_overlays_location, rootKey)
        updateAllSummaries()
    }

    override fun onResume() {
        super.onResume()
        LoggingHelper.logScreenView("Location", TAG)
    }

    private fun updateAllSummaries() {
        setupSummaryUpdater("description_video_manifest_style", R.array.description_video_manifest_entries)
        setupSummaryUpdater("description_video_filename_style", R.array.description_video_filename_entries)
        setupSummaryUpdater("description_photo_filename_style", R.array.description_photo_filename_entries)
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

    companion object {
        private const val TAG = "LocationFragment"
    }
}
