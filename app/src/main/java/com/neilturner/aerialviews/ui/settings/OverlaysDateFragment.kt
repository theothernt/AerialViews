package com.neilturner.aerialviews.ui.settings

import android.content.Context
import android.os.Bundle
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.models.enums.DateType
import com.neilturner.aerialviews.utils.DateHelper
import com.neilturner.aerialviews.utils.FirebaseHelper

class OverlaysDateFragment : PreferenceFragmentCompat() {
    private lateinit var entriesAndValues: Map<String, String>

    override fun onCreatePreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        setPreferencesFromResource(R.xml.settings_overlays_date, rootKey)

        entriesAndValues = findEntriesAndValues(requireContext(), R.array.date_format_values, R.array.date_format_entries)

        limitTextInput()
        updateSummary()
    }

    override fun onResume() {
        super.onResume()
        FirebaseHelper.logScreenView("Date", this)
    }

    private fun updateSummary() {
        val editPref = findPreference<EditTextPreference>("date_custom")
        editPref?.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { _, newValue ->
                editPref?.summary = dateFormatting(DateType.CUSTOM, newValue as String)
                true
            }
        editPref?.summary = dateFormatting(DateType.CUSTOM, editPref?.text)

        val textPref = findPreference<ListPreference>("date_format")
        textPref?.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { _, newValue ->
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

    private fun dateFormatting(
        type: DateType,
        format: String?,
    ): String {
        val forExample = requireContext().resources.getString(R.string.appearance_date_custom_example)
        val typeEntry = entriesAndValues[type.toString()]
        return if (type == DateType.CUSTOM && format == null) {
            "$typeEntry"
        } else if (type == DateType.CUSTOM) {
            "$format ($forExample ${DateHelper.formatDate(requireContext(), type, format)})"
        } else {
            "$typeEntry ($forExample ${DateHelper.formatDate(requireContext(), type, format)})"
        }
    }

    private fun findEntriesAndValues(
        context: Context,
        valuesId: Int,
        entriesId: Int,
    ): Map<String, String> {
        // values -> entries
        // key -> value
        // MESSAGE1 -> Message Line 1
        val res = context.resources
        val values = res.getStringArray(valuesId) // EMPTY, CLOCK, etc
        val entries = res.getStringArray(entriesId) // Empty, Clock, etc
        return values.zip(entries).toMap()
    }
}
