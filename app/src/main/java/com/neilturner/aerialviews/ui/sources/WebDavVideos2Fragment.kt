package com.neilturner.aerialviews.ui.sources

import android.content.SharedPreferences
import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.models.prefs.WebDavMediaPrefs2
import com.neilturner.aerialviews.providers.webdav.WebDavMediaProvider
import com.neilturner.aerialviews.utils.DialogHelper
import com.neilturner.aerialviews.utils.MenuStateFragment
import com.neilturner.aerialviews.utils.SambaHelper
import com.neilturner.aerialviews.utils.toStringOrEmpty
import kotlinx.coroutines.launch

class WebDavVideos2Fragment :
    MenuStateFragment(),
    SharedPreferences.OnSharedPreferenceChangeListener,
    PreferenceManager.OnPreferenceTreeClickListener {
    override fun onCreatePreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        setPreferencesFromResource(R.xml.sources_webdav_videos2, rootKey)
        preferenceManager.sharedPreferences?.registerOnSharedPreferenceChangeListener(this)

        limitTextInput()
        updateSummary()
    }

    override fun onDestroy() {
        preferenceManager.sharedPreferences?.unregisterOnSharedPreferenceChangeListener(this)
        super.onDestroy()
    }

    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        if (preference.key.isNullOrEmpty()) {
            return super.onPreferenceTreeClick(preference)
        }

        if (preference.key.contains("webdav_media2_test_connection")) {
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
        val hostname = findPreference<EditTextPreference>("webdav_media2_hostname")
        if (WebDavMediaPrefs2.hostName.isNotEmpty()) {
            hostname?.summary = WebDavMediaPrefs2.hostName
        } else {
            hostname?.summary = getString(R.string.webdav_media_hostname_summary)
        }

        val pathname = findPreference<EditTextPreference>("webdav_media2_pathname")
        if (pathname?.text.toStringOrEmpty().isNotEmpty() || WebDavMediaPrefs2.pathName.isNotEmpty()) {
            val fixedShareName = SambaHelper.fixShareName(WebDavMediaPrefs2.pathName)
            WebDavMediaPrefs2.pathName = fixedShareName
            pathname?.summary = fixedShareName
            if (pathname?.text.toStringOrEmpty().isNotEmpty()) {
                pathname?.text = fixedShareName
            }
        } else {
            pathname?.summary = getString(R.string.webdav_media_pathname_summary)
        }

        val username = findPreference<EditTextPreference>("webdav_media2_username")
        if (WebDavMediaPrefs2.userName.isNotEmpty()) {
            username?.summary = WebDavMediaPrefs2.userName
        } else {
            username?.summary = getString(R.string.webdav_media_username_summary)
        }

        val password = findPreference<EditTextPreference>("webdav_media2_password")
        if (WebDavMediaPrefs2.password.isNotEmpty()) {
            password?.summary = "*".repeat(WebDavMediaPrefs2.password.length)
        } else {
            password?.summary = getString(R.string.webdav_media_password_summary)
        }
    }

    private fun limitTextInput() {
        preferenceScreen.findPreference<EditTextPreference>("webdav_media2_hostname")?.setOnBindEditTextListener { it.setSingleLine() }
        preferenceScreen.findPreference<EditTextPreference>("webdav_media2_pathname")?.setOnBindEditTextListener { it.setSingleLine() }
        preferenceScreen.findPreference<EditTextPreference>("webdav_media2_username")?.setOnBindEditTextListener { it.setSingleLine() }
        preferenceScreen.findPreference<EditTextPreference>("webdav_media2_password")?.setOnBindEditTextListener { it.setSingleLine() }
    }

    private suspend fun testWebDavConnection() {
        val loadingMessage = getString(R.string.message_media_searching)
        val progressDialog =
            DialogHelper.progressDialog(
                requireContext(),
                loadingMessage,
            )
        progressDialog.show()

        val provider = WebDavMediaProvider(requireContext(), WebDavMediaPrefs2)
        val result = provider.fetchTest()

        progressDialog.dismiss()
        DialogHelper.showOnMain(requireContext(), resources.getString(R.string.webdav_media_test_results), result)
    }
}
