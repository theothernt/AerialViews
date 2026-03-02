package com.neilturner.aerialviews.ui.settings

import android.content.SharedPreferences
import android.os.Bundle
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.models.enums.DateType
import com.neilturner.aerialviews.utils.DateHelper
import com.neilturner.aerialviews.utils.FirebaseHelper
import com.neilturner.aerialviews.utils.MenuStateFragment

abstract class BaseOverlaysMetadataSlotFragment : MenuStateFragment() {
    private val folderFieldValues = setOf("FOLDER_FILENAME", "FOLDER_ONLY")
    private val locationFieldValue = "LOCATION"
    private val dateTakenFieldValue = "DATE_TAKEN"
    private lateinit var dateTypeEntries: Map<String, String>

    protected abstract val analyticsScreenName: String
    protected abstract val prefKeyVideoSelection: String
    protected abstract val prefKeyVideoFolderLevel: String
    protected abstract val prefKeyVideoLocationType: String
    protected abstract val prefKeyPhotoSelection: String
    protected abstract val prefKeyPhotoFolderLevel: String
    protected abstract val prefKeyPhotoLocationType: String
    protected abstract val prefKeyPhotoDateType: String
    protected abstract val prefKeyPhotoDateCustom: String

    protected abstract fun getDateType(): DateType

    private val prefChangeListener =
        SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, _ ->
            updateConditionalPreferenceVisibility(sharedPreferences)
            updatePhotoDateFormatSummary(sharedPreferences)
        }

    override fun onCreatePreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        setPreferencesFromResource(getPreferenceResource(), rootKey)
        dateTypeEntries = findEntriesAndValues(R.array.date_format_values, R.array.date_format_entries)
        findPreference<EditTextPreference>(prefKeyPhotoDateCustom)
            ?.setOnBindEditTextListener { it.setSingleLine() }
    }

    protected abstract fun getPreferenceResource(): Int

    override fun onResume() {
        super.onResume()
        FirebaseHelper.analyticsScreenView(analyticsScreenName, this)
        preferenceManager.sharedPreferences?.let {
            updateConditionalPreferenceVisibility(it)
            updatePhotoDateFormatSummary(it)
            it.registerOnSharedPreferenceChangeListener(prefChangeListener)
        }
    }

    override fun onPause() {
        preferenceManager.sharedPreferences?.unregisterOnSharedPreferenceChangeListener(prefChangeListener)
        super.onPause()
    }

    private fun updateConditionalPreferenceVisibility(sharedPreferences: SharedPreferences) {
        val videoSelection = sharedPreferences.getString(prefKeyVideoSelection, "").orEmpty()
        val photoSelection = sharedPreferences.getString(prefKeyPhotoSelection, "").orEmpty()
        val showDatePrefs = containsFieldValue(photoSelection, dateTakenFieldValue)
        val dateType = getDateType()

        findPreference<ListPreference>(prefKeyVideoFolderLevel)
            ?.isVisible = containsFolderLevelValue(videoSelection)
        findPreference<ListPreference>(prefKeyVideoLocationType)
            ?.isVisible = containsFieldValue(videoSelection, locationFieldValue)
        findPreference<ListPreference>(prefKeyPhotoFolderLevel)
            ?.isVisible = containsFolderLevelValue(photoSelection)
        findPreference<ListPreference>(prefKeyPhotoLocationType)
            ?.isVisible = containsFieldValue(photoSelection, locationFieldValue)
        findPreference<ListPreference>(prefKeyPhotoDateType)
            ?.isVisible = showDatePrefs
        findPreference<EditTextPreference>(prefKeyPhotoDateCustom)
            ?.isVisible = showDatePrefs && dateType == DateType.CUSTOM
    }

    private fun updatePhotoDateFormatSummary(sharedPreferences: SharedPreferences) {
        val customValue = sharedPreferences.getString(prefKeyPhotoDateCustom, "yyyy-MM-dd").orEmpty()
        val dateType = getDateType()

        findPreference<ListPreference>(prefKeyPhotoDateType)
            ?.summary = dateTypeSummary(dateType, customValue)

        findPreference<EditTextPreference>(prefKeyPhotoDateCustom)
            ?.summary = customDateSummary(customValue)
    }

    private fun dateTypeSummary(
        dateType: DateType,
        customFormat: String,
    ): String {
        val forExample = getString(R.string.appearance_date_custom_example)
        return when (dateType) {
            DateType.CUSTOM -> {
                val format = customFormat.ifBlank { "yyyy-MM-dd" }
                val example = DateHelper.formatDate(requireContext(), DateType.CUSTOM, format)
                "${dateTypeEntries[DateType.CUSTOM.toString()]}: $forExample $example ($format)"
            }

            else -> {
                val example = DateHelper.formatDate(requireContext(), dateType, null)
                "${dateTypeEntries[dateType.toString()]} ($forExample $example)"
            }
        }
    }

    private fun customDateSummary(formatInput: String): String {
        val format = formatInput.ifBlank { "yyyy-MM-dd" }
        val example = DateHelper.formatDate(requireContext(), DateType.CUSTOM, format)
        val prefix = getString(R.string.appearance_date_custom_example)
        return "$prefix $example ($format)"
    }

    private fun findEntriesAndValues(
        valuesId: Int,
        entriesId: Int,
    ): Map<String, String> {
        val res = requireContext().resources
        val values = res.getStringArray(valuesId)
        val entries = res.getStringArray(entriesId)
        return values.zip(entries).toMap()
    }

    private fun containsFolderLevelValue(selection: String): Boolean =
        selection
            .split(',')
            .map { it.trim() }
            .any { folderFieldValues.contains(it) }

    private fun containsFieldValue(
        selection: String,
        value: String,
    ): Boolean =
        selection
            .split(',')
            .map { it.trim() }
            .any { it == value }
}
