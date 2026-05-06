package com.neilturner.aerialviews.ui.sources

import android.content.SharedPreferences
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.preference.EditTextPreference
import androidx.preference.MultiSelectListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.data.network.SambaHelper
import com.neilturner.aerialviews.models.prefs.WebDavMediaPrefs
import com.neilturner.aerialviews.providers.ProviderFetchResult
import com.neilturner.aerialviews.providers.webdav.WebDavHostParser
import com.neilturner.aerialviews.providers.webdav.WebDavMediaProvider
import com.neilturner.aerialviews.ui.controls.MenuStateFragment
import com.neilturner.aerialviews.ui.helpers.DialogHelper
import com.neilturner.aerialviews.utils.setSummaryFromValues
import com.neilturner.aerialviews.utils.toStringOrEmpty
import kotlinx.coroutines.launch

class WebDavVideosFragment :
    MenuStateFragment(),
    SharedPreferences.OnSharedPreferenceChangeListener,
    PreferenceManager.OnPreferenceTreeClickListener {
    override fun onCreatePreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        setPreferencesFromResource(R.xml.sources_webdav_videos, rootKey)
        preferenceManager.sharedPreferences?.registerOnSharedPreferenceChangeListener(this)

        updateSummary()
        setupValidation()
    }

    override fun onDestroy() {
        preferenceManager.sharedPreferences?.unregisterOnSharedPreferenceChangeListener(this)
        super.onDestroy()
    }

    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        if (preference.key.isNullOrEmpty()) {
            return super.onPreferenceTreeClick(preference)
        }

        if (preference.key.contains("webdav_media_test_connection")) {
            lifecycleScope.launch { testWebDavConnection() }
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
        val mediaSelection = findPreference<MultiSelectListPreference>("webdav_media_selection")
        mediaSelection?.setSummaryFromValues(WebDavMediaPrefs.mediaSelection)

        // Host name
        val hostname = findPreference<EditTextPreference>("webdav_media_hostname")
        if (hostname?.text.toStringOrEmpty().isNotEmpty()) {
            hostname?.summary = hostname.text
        } else {
            hostname?.summary = getString(R.string.webdav_media_hostname_summary)
        }

        // Path name
        val pathname = findPreference<EditTextPreference>("webdav_media_pathname")
        if (pathname?.text.toStringOrEmpty().isNotEmpty()) {
            val fixedShareName = SambaHelper.fixShareName(WebDavMediaPrefs.pathName)
            WebDavMediaPrefs.pathName = fixedShareName
            pathname?.summary = fixedShareName
            pathname?.text = fixedShareName
        } else {
            pathname?.summary = getString(R.string.webdav_media_pathname_summary)
        }

        // Username
        val username = findPreference<EditTextPreference>("webdav_media_username")
        if (username?.text.toStringOrEmpty().isNotEmpty()) {
            username?.summary = username.text
        } else {
            username?.summary = getString(R.string.webdav_media_username_summary)
        }

        // Password
        val password = findPreference<EditTextPreference>("webdav_media_password")
        if (password?.text.toStringOrEmpty().isNotEmpty()) {
            password?.summary = "*".repeat(WebDavMediaPrefs.password.length)
        } else {
            password?.summary = getString(R.string.webdav_media_password_summary)
        }
    }

    private fun setupValidation() {
        findPreference<EditTextPreference>("webdav_media_hostname")?.setOnPreferenceChangeListener { _, newValue ->
            try {
                val value = newValue.toString().trim()
                if (value.isNotEmpty()) {
                    WebDavHostParser.parse(value)
                }
                true
            } catch (_: IllegalArgumentException) {
                AlertDialog
                    .Builder(requireContext())
                    .setMessage(getString(R.string.webdav_media_hostname_invalid))
                    .setPositiveButton(R.string.button_ok, null)
                    .show()
                false
            }
        }
    }

    private suspend fun testWebDavConnection() {
        val loadingMessage = getString(R.string.message_media_searching)
        val progressDialog =
            DialogHelper.progressDialog(
                requireContext(),
                loadingMessage,
            )
        progressDialog.show()

        val provider = WebDavMediaProvider(requireContext(), WebDavMediaPrefs)
	    val message =
            when (val result = provider.fetch()) {
                is ProviderFetchResult.Success -> result.summary
                is ProviderFetchResult.Error -> result.message
            }

        progressDialog.dismiss()
        DialogHelper.showOnMain(requireContext(), resources.getString(R.string.webdav_media_test_results), message)
    }
}
