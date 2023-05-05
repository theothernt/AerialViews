@file:Suppress("unused")

package com.neilturner.aerialviews.ui.settings

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.preference.ListPreference
import androidx.preference.MultiSelectListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.utils.setSummaryFromValues

class AdvancedFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings_advanced, rootKey)
        updateSummary()
        restartOnLanguageChange()
    }

    @Suppress("UNCHECKED_CAST")
    private fun updateSummary() {
        val dialects = findPreference<MultiSelectListPreference>("samba_videos_smb_dialects")
        dialects?.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
            dialects?.setSummaryFromValues(newValue as Set<String>)
            true
        }
        dialects?.setSummaryFromValues(dialects.values)
    }

    private fun restartOnLanguageChange() {
        val dialects = findPreference<ListPreference>("locale_menu")
        dialects?.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, _ ->
            askForRestart()
            true
        }
    }

    private fun askForRestart() {
        AlertDialog.Builder(requireContext()).apply {
            setTitle(R.string.locale_restart_app_title)
            setMessage(R.string.locale_restart_app_summary)
            setNegativeButton(R.string.button_cancel) { _, _ ->
            }
            setPositiveButton(R.string.button_ok) { _, _ ->
                startActivity(Intent.makeRestartActivityTask(activity?.intent?.component))
            }
            create().show()
        }
    }
}
