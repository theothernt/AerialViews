@file:Suppress("UNCHECKED_CAST")

package com.neilturner.aerialviews.ui.sources

import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import androidx.preference.EditTextPreference
import androidx.preference.MultiSelectListPreference
import androidx.preference.Preference
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.utils.DialogHelper
import com.neilturner.aerialviews.utils.MenuStateFragment
import com.neilturner.aerialviews.utils.UrlValidator
import kotlinx.coroutines.launch
import timber.log.Timber

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

        if (preference.key.contains("custom_media_test_connection")) {
            testUrlConnections()
            return true
        }

        return super.onPreferenceTreeClick(preference)
    }

    private fun testUrlConnections() {
        val urlsPreference = findPreference<EditTextPreference>("custom_media_urls")
        val urlsString = urlsPreference?.text
        val context = context ?: return

        if (urlsString.isNullOrBlank()) {
            DialogHelper.show(
                context,
                "Test Connection",
                "Please configure URLs before testing connections."
            )
            return
        }

        // Show loading dialog
        DialogHelper.show(
            context,
            "Testing Connections",
            "Testing URLs accessibility and JSON content..."
        )

        lifecycleScope.launch {
            try {
                val results = UrlValidator.validateUrlsWithNetworkTest(urlsString)

                val resultMessages = mutableListOf<String>()
                var allValid = true
                var hasAccessibleUrls = false
                var hasJsonUrls = false

                for ((url, result) in results) {
                    val shortUrl = if (url.length > 50) "${url.take(47)}..." else url

                    when {
                        !result.isValid -> {
                            resultMessages.add("❌ $shortUrl: Invalid URL format")
                            allValid = false
                        }
                        !result.isAccessible -> {
                            resultMessages.add("❌ $shortUrl: ${result.error ?: "Not accessible"}")
                            allValid = false
                        }
                        !result.containsJson -> {
                            resultMessages.add("⚠️ $shortUrl: Accessible but no JSON content")
                            hasAccessibleUrls = true
                        }
                        else -> {
                            resultMessages.add("✅ $shortUrl: Valid JSON endpoint")
                            hasAccessibleUrls = true
                            hasJsonUrls = true
                        }
                    }
                }

                val title = when {
                    allValid && hasJsonUrls -> "✅ Connection Test Successful"
                    hasAccessibleUrls -> "⚠️ Partial Success"
                    else -> "❌ Connection Test Failed"
                }

                val summary = when {
                    allValid && hasJsonUrls -> "All URLs are accessible and return valid JSON content."
                    hasAccessibleUrls && !hasJsonUrls -> "URLs are accessible but may not return JSON content. This might still work depending on your use case."
                    hasAccessibleUrls -> "Some URLs are accessible. Check individual results below."
                    else -> "No URLs are accessible. Please check your URLs and network connection."
                }

                val message = "$summary\n\nDetailed Results:\n${resultMessages.joinToString("\n")}"

                DialogHelper.show(context, title, message)

            } catch (e: Exception) {
                Timber.e(e, "Error testing URL connections")
                DialogHelper.show(
                    context,
                    "❌ Connection Test Error",
                    "Failed to test connections: ${e.message}"
                )
            }
        }
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
