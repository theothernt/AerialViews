package com.neilturner.aerialviews.ui.sources

import android.content.SharedPreferences
import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.models.prefs.UnsplashMediaPrefs
import com.neilturner.aerialviews.providers.unsplash.UnsplashMediaProvider
import com.neilturner.aerialviews.utils.DialogHelper
import com.neilturner.aerialviews.utils.MenuStateFragment
import com.neilturner.aerialviews.utils.toStringOrEmpty
import kotlinx.coroutines.launch

class UnsplashVideosFragment :
    MenuStateFragment(),
    SharedPreferences.OnSharedPreferenceChangeListener {

    override fun onCreatePreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        setPreferencesFromResource(R.xml.sources_unsplash_videos, rootKey)
        preferenceManager.sharedPreferences?.registerOnSharedPreferenceChangeListener(this)

        limitTextInput()
        updateSummary()

        findPreference<Preference>("unsplash_media_test_connection")?.setOnPreferenceClickListener {
            lifecycleScope.launch { testUnsplashConnection() }
            true
        }
    }

    override fun onDestroy() {
        preferenceManager.sharedPreferences?.unregisterOnSharedPreferenceChangeListener(this)
        super.onDestroy()
    }

    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        if (preference.key.isNullOrEmpty()) {
            return super.onPreferenceTreeClick(preference)
        }

        if (preference.key.contains("unsplash_media_test_connection")) {
            lifecycleScope.launch { testUnsplashConnection() }
            return true
        }

        return super.onPreferenceTreeClick(preference)
    }

    override fun onSharedPreferenceChanged(
        sharedPreferences: SharedPreferences,
        key: String?,
    ) {
        updateSummary()
    }

    private fun updateSummary() {
        // Access Key
        val accessKey = findPreference<EditTextPreference>("unsplash_media_access_key")
        if (accessKey?.text.toStringOrEmpty().isNotEmpty()) {
            accessKey?.summary = "*".repeat(minOf(accessKey.text!!.length, 20))
        } else {
            accessKey?.summary = getString(R.string.unsplash_media_access_key_summary)
        }

        // Search Query
        val searchQuery = findPreference<EditTextPreference>("unsplash_media_search_query")
        if (searchQuery?.text.toStringOrEmpty().isNotEmpty()) {
            searchQuery?.summary = searchQuery.text
        } else {
            searchQuery?.summary = getString(R.string.unsplash_media_search_query_summary)
        }

        // Photos per page
        val photosPerPage = findPreference<ListPreference>("unsplash_media_photos_per_page")
        photosPerPage?.summary = "${photosPerPage?.value ?: "30"} photos"

        // Orientation
        val orientation = findPreference<ListPreference>("unsplash_media_orientation")
        val orientationEntries = resources.getStringArray(R.array.unsplash_orientation_entries)
        val orientationValues = resources.getStringArray(R.array.unsplash_orientation_values)
        val orientationIndex = orientationValues.indexOf(orientation?.value ?: "landscape")
        orientation?.summary = if (orientationIndex >= 0) orientationEntries[orientationIndex] else "Landscape"

        // Order by
        val orderBy = findPreference<ListPreference>("unsplash_media_order_by")
        val orderByEntries = resources.getStringArray(R.array.unsplash_order_by_entries)
        val orderByValues = resources.getStringArray(R.array.unsplash_order_by_values)
        val orderByIndex = orderByValues.indexOf(orderBy?.value ?: "relevant")
        orderBy?.summary = if (orderByIndex >= 0) orderByEntries[orderByIndex] else "Relevant"
    }

    private fun limitTextInput() {
        preferenceScreen.findPreference<EditTextPreference>("unsplash_media_access_key")?.setOnBindEditTextListener { it.setSingleLine() }
        preferenceScreen.findPreference<EditTextPreference>("unsplash_media_search_query")?.setOnBindEditTextListener { it.setSingleLine() }
    }

    private suspend fun testUnsplashConnection() {
        val loadingMessage = getString(R.string.message_media_searching)
        val progressDialog =
            DialogHelper.progressDialog(
                requireContext(),
                loadingMessage,
            )
        progressDialog.show()

        val provider = UnsplashMediaProvider(requireContext(), UnsplashMediaPrefs)
        val result = provider.fetchTest()

        progressDialog.dismiss()
        DialogHelper.showOnMain(
            requireContext(),
            getString(R.string.unsplash_media_test_results),
            result,
        )
    }
}
