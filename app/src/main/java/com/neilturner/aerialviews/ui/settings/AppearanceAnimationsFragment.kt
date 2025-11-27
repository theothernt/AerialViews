package com.neilturner.aerialviews.ui.settings

import android.os.Bundle
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.SwitchPreference
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
        val editPref = findPreference<ListPreference>("overlay_auto_hide")
        editPref?.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { _, newValue ->
                toggleOverlaySettings(newValue as String)
                true
            }
        toggleOverlaySettings(editPref?.value as String)
    }

    private fun toggleOverlaySettings(value: String) {
        val isAutoHideEnabled = value != "-1"

        // Enable/disable reveal timeout
        val revealTimeoutPref = findPreference<ListPreference>("overlay_reveal_timeout")
        revealTimeoutPref?.isEnabled = isAutoHideEnabled

        // Enable/disable per-corner fade settings
        val fadeTopLeft = findPreference<SwitchPreference>("fade_top_left_corner")
        val fadeTopRight = findPreference<SwitchPreference>("fade_top_right_corner")
        val fadeBottomLeft = findPreference<SwitchPreference>("fade_bottom_left_corner")
        val fadeBottomRight = findPreference<SwitchPreference>("fade_bottom_right_corner")

        fadeTopLeft?.isEnabled = isAutoHideEnabled
        fadeTopRight?.isEnabled = isAutoHideEnabled
        fadeBottomLeft?.isEnabled = isAutoHideEnabled
        fadeBottomRight?.isEnabled = isAutoHideEnabled
    }
}
