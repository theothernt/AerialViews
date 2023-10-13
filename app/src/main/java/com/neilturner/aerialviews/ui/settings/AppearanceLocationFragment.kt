package com.neilturner.aerialviews.ui.settings

import android.os.Bundle
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.neilturner.aerialviews.R

class AppearanceLocationFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings_appearance_location, rootKey)
        updateSummary()
    }

    private fun updateSummary() {
        val control = findPreference<ListPreference>("filename_as_location")
        control?.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
            control?.findIndexOfValue(newValue as String)?.let { updateDataUsageSummary(it) }
            true
        }
        control?.findIndexOfValue(control.value)?.let { updateDataUsageSummary(it) }
    }

    private fun updateDataUsageSummary(index: Int) {
        val res = context?.resources!!
        val pref = findPreference<Preference>("filename_as_location")
        val summaryList = res.getStringArray(R.array.filename_as_location_summary_entries)
        val summary = summaryList[index]
        pref?.summary = summary
    }
}
