package com.neilturner.aerialviews.ui.sources

import android.content.SharedPreferences
import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.models.prefs.SambaMediaPrefs2
import com.neilturner.aerialviews.providers.samba.SambaMediaProvider
import com.neilturner.aerialviews.utils.DialogHelper
import com.neilturner.aerialviews.utils.MenuStateFragment
import com.neilturner.aerialviews.utils.SambaHelper
import com.neilturner.aerialviews.utils.toStringOrEmpty
import kotlinx.coroutines.launch

class SambaVideos2Fragment :
    MenuStateFragment(),
    SharedPreferences.OnSharedPreferenceChangeListener,
    PreferenceManager.OnPreferenceTreeClickListener {
    override fun onCreatePreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        setPreferencesFromResource(R.xml.sources_samba_videos2, rootKey)
        preferenceManager.sharedPreferences?.registerOnSharedPreferenceChangeListener(this)

        limitTextInput()
        updateSummary()
    }

    override fun onDestroy() {
        preferenceManager.sharedPreferences?.unregisterOnSharedPreferenceChangeListener(this)
        super.onDestroy()
    }

    override fun onSharedPreferenceChanged(
        sharedPreferences: SharedPreferences,
        key: String?,
    ) {
        updateSummary()
    }

    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        if (preference.key.isNullOrEmpty()) {
            return super.onPreferenceTreeClick(preference)
        }

        if (preference.key.contains("samba_videos2_test_connection")) {
            lifecycleScope.launch { testSambaConnection() }
            return true
        }

        return super.onPreferenceTreeClick(preference)
    }

    private fun updateSummary() {
        val hostname = findPreference<EditTextPreference>("samba_videos2_hostname")
        if (SambaMediaPrefs2.hostName.isNotEmpty()) {
            hostname?.summary = SambaMediaPrefs2.hostName
        } else {
            hostname?.summary = getString(R.string.samba_videos_hostname_summary)
        }

        val domainname = findPreference<EditTextPreference>("samba_videos2_domainname")
        if (SambaMediaPrefs2.domainName.isNotEmpty()) {
            domainname?.summary = SambaMediaPrefs2.domainName
        } else {
            domainname?.summary = getString(R.string.samba_videos_domainname_summary)
        }

        val sharename = findPreference<EditTextPreference>("samba_videos2_sharename")
        if (sharename?.text.toStringOrEmpty().isNotEmpty() || SambaMediaPrefs2.shareName.isNotEmpty()) {
            val fixedShareName = SambaHelper.fixShareName(SambaMediaPrefs2.shareName)
            SambaMediaPrefs2.shareName = fixedShareName
            sharename?.summary = fixedShareName
            if (sharename?.text.toStringOrEmpty().isNotEmpty()) {
                sharename?.text = fixedShareName
            }
        } else {
            sharename?.summary = getString(R.string.samba_videos_sharename_summary)
        }

        val username = findPreference<EditTextPreference>("samba_videos2_username")
        if (SambaMediaPrefs2.userName.isNotEmpty()) {
            username?.summary = SambaMediaPrefs2.userName
        } else {
            username?.summary = getString(R.string.samba_videos_username_summary)
        }

        val password = findPreference<EditTextPreference>("samba_videos2_password")
        if (SambaMediaPrefs2.password.isNotEmpty()) {
            password?.summary = "*".repeat(SambaMediaPrefs2.password.length)
        } else {
            password?.summary = getString(R.string.samba_videos_password_summary)
        }
    }

    private fun limitTextInput() {
        listOf(
            "samba_videos2_hostname",
            "samba_videos2_domainname",
            "samba_videos2_sharename",
            "samba_videos2_username",
            "samba_videos2_password",
        ).forEach { key ->
            findPreference<EditTextPreference>(key)?.setOnBindEditTextListener { it.setSingleLine() }
        }
    }

    private suspend fun testSambaConnection() {
        val loadingMessage = getString(R.string.message_media_searching)
        val progressDialog =
            DialogHelper.progressDialog(
                requireContext(),
                loadingMessage,
            )
        progressDialog.show()

        val provider = SambaMediaProvider(requireContext(), SambaMediaPrefs2)
        val result = provider.fetchTest()

        progressDialog.dismiss()
        DialogHelper.showOnMain(
            requireContext(),
            resources.getString(R.string.samba_videos_test_results),
            result,
        )
    }
}
