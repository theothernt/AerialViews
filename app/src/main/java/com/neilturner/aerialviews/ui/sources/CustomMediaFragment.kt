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
import kotlinx.coroutines.launch

class CustomMediaFragment : MenuStateFragment() {
    override fun onCreatePreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        setPreferencesFromResource(R.xml.sources_custom_media, rootKey)
        updateSummary()
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
        val resultMessage = provider.fetchTest()

        progressDialog.dismiss()

        DialogHelper.showOnMain(
            requireContext(),
            resources.getString(R.string.samba_videos_test_results),
            resultMessage,
        )

        updateValidatedUrlsSummary()
    }

    private fun updateSummary() {
        val urls = findPreference<EditTextPreference>("custom_media_urls")
        urls?.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { preference, newValue ->
                val urlsString = newValue as String
                val previousValue = (preference as EditTextPreference).text ?: ""

                val provider = CustomFeedProvider(requireContext(), CustomMediaPrefs)
                val invalidUrlsText = provider.validateUrlFormat(urlsString)

                if (invalidUrlsText != null) {
                    val context = context ?: return@OnPreferenceChangeListener false
                    DialogHelper.show(
                        context,
                        context.getString(R.string.custom_media_urls_invalid),
                        invalidUrlsText,
                    )
                    return@OnPreferenceChangeListener false
                }

                updateUrlsSummary(urlsString)

                // Automatically test the feeds only if URL is not blank and has changed
                if (urlsString.isNotBlank() && urlsString != previousValue) {
                    // Save the new value to preferences immediately before testing
                    CustomMediaPrefs.urls = urlsString
                    lifecycleScope.launch { testUrlConnections() }
                }

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
        } else {
            urls.summary = urlsString
        }
    }

    private fun updateValidatedUrlsSummary() {
        val urls = findPreference<EditTextPreference>("custom_media_urls") ?: return

        val validatedUrls = CustomMediaPrefs.urlsCache
        if (validatedUrls.isBlank()) {
            urls.summary = "No valid URLs found"
            return
        }

        val urlList = validatedUrls.split(",").map { it.trim() }.filter { it.isNotBlank() }
        val rtspCount = urlList.count { it.startsWith("rtsp://", ignoreCase = true) }
        val entriesCount = urlList.size - rtspCount
        val videoCount = urlList.count() - rtspCount

        urls.summary = buildString {
            if (videoCount > 0 && entriesCount > 0) {
                append("$videoCount video")
                if (videoCount != 1) append("s")
                append(" in $entriesCount URL")
                if (entriesCount != 1) append("s")
            } else if (entriesCount > 0) {
                append("$entriesCount entries.json URL")
                if (entriesCount != 1) append("s")
            }

            if (rtspCount > 0) {
                if (entriesCount > 0) append(" and ")
                append("$rtspCount RTSP stream")
                if (rtspCount != 1) append("s")
            }
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
