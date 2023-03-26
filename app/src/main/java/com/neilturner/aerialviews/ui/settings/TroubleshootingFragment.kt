@file:Suppress("unused")

package com.neilturner.aerialviews.ui.settings

import android.os.Bundle
import androidx.preference.MultiSelectListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.utils.setSummaryFromValues

class TroubleshootingFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings_troubleshooting, rootKey)
        updateSummary()
    }

    @Suppress("UNCHECKED_CAST")
    private fun updateSummary() {
        val dialects = findPreference<MultiSelectListPreference>("network_videos_smb_dialects")
        dialects?.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
            dialects?.setSummaryFromValues(newValue as Set<String>)
            true
        }
        dialects?.setSummaryFromValues(dialects.values)
    }
}
