@file:Suppress("UNCHECKED_CAST")

package com.neilturner.aerialviews.ui.sources

import android.os.Bundle
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.MultiSelectListPreference
import androidx.preference.Preference
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.utils.DialogHelper
import com.neilturner.aerialviews.utils.MenuStateFragment
import com.neilturner.aerialviews.utils.UrlValidator

class CustomMediaFragment : MenuStateFragment() {
    override fun onCreatePreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        setPreferencesFromResource(R.xml.sources_custom_media, rootKey)
        updateSummary()
    }

    private fun updateSummary() {
        val urls = findPreference<EditTextPreference>("custom_media_urls")
        urls?.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { _, newValue ->
                val urlsString = newValue as String
                val (isValid, invalidUrls) = UrlValidator.validateUrls(urlsString)

                if (!isValid) {
                    val context = context ?: return@OnPreferenceChangeListener false
                    val invalidUrlsText = invalidUrls.joinToString(", ")
                    DialogHelper.show(
                        context,
                        context.getString(R.string.custom_media_urls_invalid),
                        invalidUrlsText
                    )
                    return@OnPreferenceChangeListener false
                }

                updateUrlsSummary(urlsString)
                true
            }
        urls?.let { updateUrlsSummary(it.text) }

        val quality = findPreference<ListPreference>("custom_media_quality")
        quality?.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { _, newValue ->
                updateDataUsageSummary(quality.findIndexOfValue(newValue as String))
                true
            }
        quality?.findIndexOfValue(quality.value)?.let { updateDataUsageSummary(it) }

        val sceneType = findPreference<MultiSelectListPreference>("custom_media_scene_type")
        sceneType?.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { preference, newValue ->
                updateMultiSelectSummary(preference as MultiSelectListPreference, newValue as Set<String>)
                true
            }
        sceneType?.let { updateMultiSelectSummary(it, it.values) }

        val timeOfDay = findPreference<MultiSelectListPreference>("custom_media_time_of_day")
        timeOfDay?.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { preference, newValue ->
                updateMultiSelectSummary(preference as MultiSelectListPreference, newValue as Set<String>)
                true
            }
        timeOfDay?.let { updateMultiSelectSummary(it, it.values) }
    }

    private fun updateUrlsSummary(urlsString: String?) {
        val urls = findPreference<EditTextPreference>("custom_media_urls") ?: return
        val context = context ?: return

        if (urlsString?.isBlank() == true) {
            urls.summary = context.getString(R.string.custom_media_urls_summary)
            return
        }

        val validUrls = UrlValidator.parseUrls(urlsString)
        urls.summary = when {
            validUrls.isEmpty() -> context.getString(R.string.custom_media_urls_invalid)
            validUrls.size == 1 -> "1 URL configured"
            else -> "${validUrls.size} URLs configured"
        }
    }

    private fun updateDataUsageSummary(index: Int) {
//        val res = context?.resources ?: return
//        val dataUsage = findPreference<Preference>("custom_media_data_usage") ?: return
//        val bitrateList = res.getStringArray(R.array.apple_videos_data_usage_values)
//        val bitrate = bitrateList[index]
//        dataUsage.summary = String.format(res.getString(R.string.apple_videos_data_estimate_summary), bitrate)
    }

    private fun updateMultiSelectSummary(
        preference: MultiSelectListPreference,
        selectedValues: Set<String>,
    ) {
        val res = context?.resources ?: return
        val entries = preference.entries
        val entryValues = preference.entryValues

        if (selectedValues.isEmpty()) {
            preference.summary = res.getString(R.string.none_selected)
            return
        }

        val selectedEntries = mutableListOf<String>()
        for (value in selectedValues) {
            val index = entryValues.indexOf(value)
            if (index >= 0 && index < entries.size) {
                selectedEntries.add(entries[index].toString())
            }
        }

        preference.summary =
            if (selectedEntries.size == entries.size) {
                res.getString(R.string.all_selected)
            } else {
                selectedEntries.joinToString(", ")
            }
    }
}
