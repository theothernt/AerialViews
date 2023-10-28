@file:Suppress("unused")

package com.neilturner.aerialviews.ui.settings

import android.os.Bundle
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.models.enums.DateType
import com.neilturner.aerialviews.utils.DateHelper

class AppearanceDateFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings_appearance_date, rootKey)

        limitTextInput()
        updateSummary()
    }

    private fun updateSummary() {
        val editPref = findPreference<EditTextPreference>("date_custom")
        editPref?.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
            editPref?.summary = dateFormatting(DateType.CUSTOM, newValue as String)
            true
        }
        editPref?.summary = dateFormatting(DateType.CUSTOM, editPref?.text)

        val textPref = findPreference<ListPreference>("date_format")
        textPref?.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
            val dateType = DateType.valueOf(newValue as String)
            textPref?.summary = dateFormatting(dateType, null)
            editPref?.isEnabled = dateType == DateType.CUSTOM
            true
        }
        val dateType = DateType.valueOf(textPref?.value!!)
        textPref.summary = dateFormatting(dateType, null)
        editPref?.isEnabled = dateType == DateType.CUSTOM
    }

    private fun limitTextInput() {
        preferenceScreen.findPreference<EditTextPreference>("date_custom")?.setOnBindEditTextListener { it.setSingleLine() }
    }

    private fun dateFormatting(type: DateType, format: String?): String {
        val forExample = requireContext().resources.getString(R.string.appearance_date_custom_example)
        return if (type == DateType.CUSTOM && format == null) {
            "CUSTOM"
        } else if (type == DateType.CUSTOM) {
            "$format ($forExample ${DateHelper.formatDate(requireContext(), type, format)})"
        } else {
            "$type ($forExample ${DateHelper.formatDate(requireContext(), type, format)})"
        }
    }

    companion object {
        private const val TAG = "AppearanceDateFragment"
    }
}
