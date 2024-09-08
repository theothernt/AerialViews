package com.neilturner.aerialviews.ui.settings

import android.os.Bundle
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.utils.FirebaseHelper

class SettingsFragment :
    PreferenceFragmentCompat(),
    PreferenceManager.OnPreferenceTreeClickListener {
    override fun onCreatePreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        setPreferencesFromResource(R.xml.settings, rootKey)
    }

    override fun onResume() {
        super.onResume()
        FirebaseHelper.logScreenView("Settings", this)
    }

    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        if (preference.key.isNullOrEmpty()) {
            return super.onPreferenceTreeClick(preference)
        }

        return super.onPreferenceTreeClick(preference)
    }
}
