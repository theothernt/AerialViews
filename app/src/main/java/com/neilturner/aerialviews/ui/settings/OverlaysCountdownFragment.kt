package com.neilturner.aerialviews.ui.settings

import android.content.SharedPreferences
import android.os.Bundle
import androidx.preference.EditTextPreference
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.utils.FirebaseHelper
import com.neilturner.aerialviews.utils.MenuStateFragment
import com.neilturner.aerialviews.utils.toStringOrEmpty

class OverlaysCountdownFragment :
    MenuStateFragment(),
    SharedPreferences.OnSharedPreferenceChangeListener {
    override fun onCreatePreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        setPreferencesFromResource(R.xml.settings_overlays_countdown, rootKey)
    }

    override fun onResume() {
        super.onResume()
        FirebaseHelper.analyticsScreenView("Countdown", this)
        preferenceManager.sharedPreferences?.registerOnSharedPreferenceChangeListener(this)
        limitTextInput()
        updateSummary()
    }

    override fun onDestroy() {
        preferenceManager.sharedPreferences?.unregisterOnSharedPreferenceChangeListener(this)
        super.onDestroy()
    }

    override fun onSharedPreferenceChanged(
        sharedPreferences: SharedPreferences,
        key: String?,
    ) {
        updateSummary()
    }

    private fun updateSummary() {
        // Target Time
        val targetTime = findPreference<EditTextPreference>("countdown_target_time")
        if (targetTime?.text.toStringOrEmpty().isNotEmpty()) {
            targetTime?.summary = targetTime.text
        } else {
            targetTime?.summary = getString(R.string.appearance_countdown_target_time_summary)
        }

        // Target Message
        val targetMessage = findPreference<EditTextPreference>("countdown_target_message")
        if (targetMessage?.text.toStringOrEmpty().isNotEmpty()) {
            targetMessage?.summary = targetMessage.text
        } else {
            targetMessage?.summary = getString(R.string.appearance_countdown_target_message_summary)
        }
    }

    private fun limitTextInput() {
        preferenceScreen.findPreference<EditTextPreference>("countdown_target_time")?.setOnBindEditTextListener { it.setSingleLine() }
        preferenceScreen.findPreference<EditTextPreference>("countdown_target_message")?.setOnBindEditTextListener { it.setSingleLine() }
    }
}
