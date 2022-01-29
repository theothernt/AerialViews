@file:Suppress("unused")

package com.neilturner.aerialviews.ui.settings

import android.content.SharedPreferences
import android.os.Bundle
import androidx.preference.ListPreference
import androidx.preference.PreferenceFragmentCompat
import com.neilturner.aerialviews.R
import java.util.Locale

class InterfaceFragment :
    PreferenceFragmentCompat(),
    SharedPreferences.OnSharedPreferenceChangeListener {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings_interface, rootKey)
        preferenceManager.sharedPreferences.registerOnSharedPreferenceChangeListener(this)
        updateSummaries()
    }

    override fun onDestroy() {
        preferenceManager.sharedPreferences.unregisterOnSharedPreferenceChangeListener(this)
        super.onDestroy()
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String) {
        updateSummaries()
    }

    private fun updateSummaries() {
        val locationStyle = findPreference<ListPreference>("show_location_style") as ListPreference
        val locationStyleTitle = context?.getString(R.string.interface_show_location_style_title)

        val formattedLocationStyle = locationStyle.entry.toString()
            .replaceFirstChar { it.lowercase(Locale.getDefault()) }

        locationStyle.title = "$locationStyleTitle - $formattedLocationStyle"
    }
}
