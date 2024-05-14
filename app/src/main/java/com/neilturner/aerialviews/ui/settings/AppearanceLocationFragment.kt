package com.neilturner.aerialviews.ui.settings

import android.os.Bundle
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.utils.LoggingHelper

class AppearanceLocationFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings_appearance_location, rootKey)
        updateAllSummaries()
    }

    private fun updateAllSummaries() {
        val manifestPref = findPreference<ListPreference>(VIDEO_MANIFEST_STYLE)
        manifestPref?.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
            manifestPref?.findIndexOfValue(newValue as String)?.let { updateSummary(VIDEO_MANIFEST_STYLE, R.array.description_video_manifest_entries, it) }
            true
        }
        manifestPref?.findIndexOfValue(manifestPref.value)?.let { updateSummary(VIDEO_MANIFEST_STYLE, R.array.description_video_manifest_entries, it) }

        val videoPref = findPreference<ListPreference>(VIDEO_FILENAME_STYLE)
        videoPref?.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
            videoPref?.findIndexOfValue(newValue as String)?.let { updateSummary(VIDEO_FILENAME_STYLE, R.array.description_video_filename_entries, it) }
            true
        }
        videoPref?.findIndexOfValue(videoPref.value)?.let { updateSummary(VIDEO_FILENAME_STYLE, R.array.description_video_filename_entries, it) }

        val photoPref = findPreference<ListPreference>(PHOTO_FILENAME_STYLE)
        photoPref?.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
            photoPref?.findIndexOfValue(newValue as String)?.let { updateSummary(PHOTO_FILENAME_STYLE, R.array.description_photo_filename_entries, it) }
            true
        }
        photoPref?.findIndexOfValue(photoPref.value)?.let { updateSummary(PHOTO_FILENAME_STYLE, R.array.description_photo_filename_entries, it) }
    }

    override fun onResume() {
        super.onResume()
        LoggingHelper.logScreenView("Location", TAG)
    }

    private fun updateSummary(control: String, entries: Int, index: Int) {
        val res = context?.resources!!
        val pref = findPreference<Preference>(control)
        val summaryList = res.getStringArray(entries)
        val newIndex = if (index < 0 || index >= summaryList.size) 0 else index
        val summary = summaryList[newIndex]
        pref?.summary = summary
    }

    companion object {
        private const val VIDEO_MANIFEST_STYLE = "description_video_manifest_style"
        private const val VIDEO_FILENAME_STYLE = "description_video_filename_style"
        private const val PHOTO_FILENAME_STYLE = "description_photo_filename_style"
        private const val TAG = "LocationFragment"
    }
}
