package com.codingbuffalo.aerialdream.ui.settings

import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat
import com.codingbuffalo.aerialdream.R

class CapabilitiesFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings_capabilities, rootKey)
    }
}