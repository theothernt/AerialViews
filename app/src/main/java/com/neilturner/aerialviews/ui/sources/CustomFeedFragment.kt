@file:Suppress("UNCHECKED_CAST")

package com.neilturner.aerialviews.ui.sources

import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.models.prefs.CustomFeedPrefs
import com.neilturner.aerialviews.providers.custom.CustomFeedProvider
import com.neilturner.aerialviews.ui.controls.MenuStateFragment
import com.neilturner.aerialviews.ui.helpers.DialogHelper
import kotlinx.coroutines.launch

class CustomFeedFragment : MenuStateFragment() {
    override fun onCreatePreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        setPreferencesFromResource(R.xml.sources_custom_feed, rootKey)
        updateSummary()
    }

    private fun updateSummary() {
        val urls = findPreference<EditTextPreference>("custom_media_urls")
        urls?.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { preference, newValue ->
                val urlsString = newValue as String
                val previousValue = (preference as EditTextPreference).text ?: ""

                if (urlsString.isNotBlank() && urlsString != previousValue) {
                    CustomFeedPrefs.urls = urlsString
                    lifecycleScope.launch { validateUrls() }
                } else if (urlsString.isBlank()) {
                    CustomFeedPrefs.urlsCache = ""
                    CustomFeedPrefs.urlsSummary = ""
                    updateUrlsSummary()
                }

                true
            }
        urls?.let { updateUrlsSummary(CustomFeedPrefs.urlsSummary) }
    }

    private fun updateUrlsSummary(summary: String = "") {
        val urls = findPreference<EditTextPreference>("custom_media_urls") ?: return
        val context = context ?: return

        urls.summary =
            summary.ifBlank {
                context.getString(R.string.custom_media_urls_summary)
            }
    }

    private suspend fun validateUrls() {
        val loadingMessage = getString(R.string.message_media_searching)
        val progressDialog =
            DialogHelper.progressDialog(
                requireContext(),
                loadingMessage,
            )
        progressDialog.show()

        val provider = CustomFeedProvider(requireContext(), CustomFeedPrefs)
        val resultMessage = provider.fetchTest()
        progressDialog.dismiss()

        DialogHelper.showOnMain(
            requireContext(),
            resources.getString(R.string.samba_videos_test_results),
            resultMessage,
        )

        updateUrlsSummary(CustomFeedPrefs.urlsSummary)
    }
}
