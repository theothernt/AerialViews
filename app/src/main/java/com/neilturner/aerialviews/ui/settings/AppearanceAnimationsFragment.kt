package com.neilturner.aerialviews.ui.settings

import android.os.Bundle
import androidx.preference.ListPreference
import androidx.preference.MultiSelectListPreference
import androidx.preference.Preference
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.utils.FirebaseHelper
import com.neilturner.aerialviews.utils.MenuStateFragment

class AppearanceAnimationsFragment : MenuStateFragment() {
    override fun onCreatePreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        setPreferencesFromResource(R.xml.settings_appearance_animations, rootKey)
    }

    override fun onResume() {
        super.onResume()
        FirebaseHelper.analyticsScreenView("Animations", this)
        updateAllSummaries()
    }

    private fun updateAllSummaries() {
        val autoHidePref = findPreference<ListPreference>("overlay_auto_hide")
        autoHidePref?.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { _, newValue ->
                toggleOverlaySettings(newValue as String)
                true
            }
        toggleOverlaySettings(autoHidePref?.value ?: "-1")

        // Setup corner fade preference summary
        val fadeCornersPref = findPreference<MultiSelectListPreference>("fade_corners_selection")
        fadeCornersPref?.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { preference, newValue ->
                @Suppress("UNCHECKED_CAST")
                updateMultiSelectSummary(preference as MultiSelectListPreference, newValue as Set<String>)
                true
            }
        fadeCornersPref?.let { updateMultiSelectSummary(it, it.values) }
    }

    private fun toggleOverlaySettings(value: String) {
        val isAutoHideEnabled = value != "-1"

        // Enable/disable reveal timeout
        val revealTimeoutPref = findPreference<ListPreference>("overlay_reveal_timeout")
        revealTimeoutPref?.isEnabled = isAutoHideEnabled

        // Enable/disable corner fade selection
        val fadeCornersPref = findPreference<MultiSelectListPreference>("fade_corners_selection")
        fadeCornersPref?.isEnabled = isAutoHideEnabled
    }

    private fun updateMultiSelectSummary(
        preference: MultiSelectListPreference,
        selectedValues: Set<String>,
    ) {
        val res = context?.resources ?: return
        val entries = preference.entries
        val entryValues = preference.entryValues

        if (selectedValues.isEmpty()) {
            preference.summary = res.getString(R.string.none_selected)
            return
        }

        val selectedEntries = mutableListOf<String>()
        for (value in selectedValues) {
            val index = entryValues.indexOf(value)
            if (index >= 0 && index < entries.size) {
                selectedEntries.add(entries[index].toString())
            }
        }

        preference.summary =
            if (selectedEntries.size == entries.size) {
                res.getString(R.string.all_selected)
            } else {
                selectedEntries.joinToString(", ")
            }
    }
}
