package com.neilturner.aerialviews.ui.settings

import android.os.Bundle
import androidx.preference.ListPreference
import androidx.preference.Preference
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.utils.FirebaseHelper
import com.neilturner.aerialviews.utils.MenuStateFragment
import timber.log.Timber

class OverlaysLocationFragment : MenuStateFragment() {
    override fun onCreatePreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        setPreferencesFromResource(R.xml.settings_overlays_location, rootKey)
    }

    override fun onResume() {
        super.onResume()
        FirebaseHelper.logScreenView("Location", this)
        updateAllSummaries()
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
                updateSummary(pref, entries, pref.findIndexOfValue(newValue as String))
                showPathOptions(pref, newValue)
                true
            }

        pref?.let {
            updateSummary(pref, entries, it.findIndexOfValue(pref.value))
            showPathOptions(it, it.value)
        }
    }

    private fun updateSummary(
        pref: ListPreference,
        entries: Int,
        index: Int,
    ) {
        val res = requireContext().resources
        var summary = pref.entries?.elementAtOrNull(index) ?: ""
        summary = if (summary == "Disabled") "" else "$summary: "

        val entries = res?.getStringArray(entries)
        val description = entries?.elementAtOrNull(index) ?: ""

        pref.summary = summary + description
    }

    private fun showPathOptions(
        pref: ListPreference,
        value: String,
    ) {
        if (pref.key == "description_video_filename_style") {
            findPreference<ListPreference>("description_video_folder_levels")
                ?.isVisible = (value.contains("LAST_FOLDER"))
        }

        if (pref.key == "description_photo_filename_style") {
            findPreference<ListPreference>("description_photo_folder_levels")
                ?.isVisible = (value.contains("LAST_FOLDER"))
        }
        Timber.i("control: ${pref.key}, value: $value")
    }
}
