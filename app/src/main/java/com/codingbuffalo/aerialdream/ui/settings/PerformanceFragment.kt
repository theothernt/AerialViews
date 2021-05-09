package com.codingbuffalo.aerialdream.ui.settings

import android.os.Bundle
import com.codingbuffalo.aerialdream.R
import androidx.preference.PreferenceFragmentCompat

class PerformanceFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings_performance, rootKey)
    }
}