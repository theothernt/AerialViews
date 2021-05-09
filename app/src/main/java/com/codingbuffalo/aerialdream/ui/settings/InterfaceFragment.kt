package com.codingbuffalo.aerialdream.ui.settings

import android.os.Bundle
import com.codingbuffalo.aerialdream.R
import androidx.preference.PreferenceFragmentCompat

class InterfaceFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings_interface, rootKey)
    }
}