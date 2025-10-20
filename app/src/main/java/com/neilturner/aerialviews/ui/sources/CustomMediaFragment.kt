@file:Suppress("UNCHECKED_CAST")

package com.neilturner.aerialviews.ui.sources

import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import androidx.preference.EditTextPreference
import androidx.preference.MultiSelectListPreference
import androidx.preference.Preference
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.models.prefs.CustomMediaPrefs
import com.neilturner.aerialviews.providers.custom.CustomFeedProvider
import com.neilturner.aerialviews.utils.DialogHelper
import com.neilturner.aerialviews.utils.MenuStateFragment
import com.neilturner.aerialviews.utils.UrlValidator
import kotlinx.coroutines.launch

class CustomMediaFragment : MenuStateFragment() {
    override fun onCreatePreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        setPreferencesFromResource(R.xml.sources_custom_media, rootKey)
        updateSummary()
    }

    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        if (preference.key.isNullOrEmpty()) {
            return super.onPreferenceTreeClick(preference)
        }

        if (preference.key.contains("validate_feeds")) {
            lifecycleScope.launch { testUrlConnections() }
            return true
        }

        return super.onPreferenceTreeClick(preference)
    }

    private suspend fun testUrlConnections() {
        val loadingMessage = getString(R.string.message_media_searching)
        val progressDialog =
            DialogHelper.progressDialog(
                requireContext(),
                loadingMessage,
            )
        progressDialog.show()

        val provider = CustomFeedProvider(requireContext(), CustomMediaPrefs)
        val result = provider.fetchTest()

        progressDialog.dismiss()
        DialogHelper.showOnMain(
            requireContext(),
            resources.getString(R.string.samba_videos_test_results),
            result,
        )
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
                        invalidUrlsText,
                    )
                    return@OnPreferenceChangeListener false
                }

                updateUrlsSummary(urlsString)
                true
            }
        urls?.let { updateUrlsSummary(it.text) }

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
        urls.summary =
            when {
                validUrls.isEmpty() -> context.getString(R.string.custom_media_urls_invalid)
                validUrls.size == 1 -> "1 URL configured"
                else -> "${validUrls.size} URLs configured"
            }
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
