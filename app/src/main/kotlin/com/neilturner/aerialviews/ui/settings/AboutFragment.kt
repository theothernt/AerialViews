@file:Suppress("unused")

package com.neilturner.aerialviews.ui.settings

import android.os.Bundle
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.neilturner.aerialviews.BuildConfig
import com.neilturner.aerialviews.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AboutFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings_about, rootKey)
        updateSummary()
    }

    private fun updateSummary() {
        val version = findPreference<Preference>("about_version")
        val date = findPreference<Preference>("about_date")

        version?.summary = buildVersionSummary()
        date?.summary = buildDateSummary()
    }

    private fun buildVersionSummary(): String {
        return "Aerial Views ${BuildConfig.VERSION_NAME} (${BuildConfig.FLAVOR}.${BuildConfig.BUILD_TYPE})"
    }

    private fun buildDateSummary(): String {
        val dateFormat = SimpleDateFormat("dd/MM/yyyy 'at' HH:mm", Locale.getDefault())
        val date = Date(BuildConfig.BUILD_TIME.toLong())
        return dateFormat.format(date)
    }
}
