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

class AppearanceLocationFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings_appearance_location, rootKey)
        updateSummary()
    }

    private fun updateSummary() {
        val summary = findPreference<EditTextPreference>("date_custom")
        summary?.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
            summary?.text = dateFormatting(newValue as String)
            true
        }
        summary?.text = dateFormatting(summary?.text)
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
