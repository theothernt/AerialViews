package com.neilturner.aerialviews.ui.settings

import android.os.Bundle
import androidx.preference.ListPreference
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
        updateAllSummaries()
    }

    override fun onResume() {
        super.onResume()
        FirebaseHelper.logScreenView("Animations", this)
    }

    private fun updateAllSummaries() {
        val editPref = findPreference<ListPreference>("overlay_auto_hide")
        editPref?.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { _, newValue ->
                toggleRevealTimeout(newValue as String)
                true
            }
        toggleRevealTimeout(editPref?.value as String)
    }

    private fun toggleRevealTimeout(value: String) {
        val revealTimeoutPref = findPreference<ListPreference>("overlay_reveal_timeout")
        revealTimeoutPref?.isEnabled = value != "-1"
    }
}
