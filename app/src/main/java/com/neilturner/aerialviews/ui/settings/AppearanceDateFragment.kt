package com.neilturner.aerialviews.ui.settings

import android.os.Bundle
import android.util.Log
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.neilturner.aerialviews.R
import java.lang.Exception
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class AppearanceDateFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings_appearance_date, rootKey)
        updateSummary()
    }

    private fun updateSummary() {
        val control = findPreference<EditTextPreference>("date_custom")
        control?.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
            control?.summary = dateFormatting(newValue as String)
            true
        }
        control?.summary = dateFormatting(control?.text)
    }

    private fun dateFormatting(format: String?): String {
        val result = try {
            val today = Calendar.getInstance().time
            val formatter = SimpleDateFormat(format, Locale.getDefault())
            formatter.format(today)
        } catch (ex: Exception) {
            Log.i(TAG, "Exception while trying custom date formatting")
            "Invalid custom date format!"
        }
        return "$format - ($result)"
    }

    companion object {
        private const val TAG = "AppearanceLocationFrag"
    }
}
