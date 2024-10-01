package com.neilturner.aerialviews.ui.sources

import android.content.SharedPreferences
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.models.prefs.ImmichMediaPrefs
import com.neilturner.aerialviews.providers.ImmichMediaProvider
import com.neilturner.aerialviews.utils.toStringOrEmpty
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ImmichVideosFragment :
    PreferenceFragmentCompat(),
    SharedPreferences.OnSharedPreferenceChangeListener,
    PreferenceManager.OnPreferenceTreeClickListener {
    override fun onCreatePreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        setPreferencesFromResource(R.xml.sources_immich_videos, rootKey)
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

        if (preference.key.contains("immich_media_test_connection")) {
            lifecycleScope.launch { testImmichConnection() }
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
        // Host name
        val hostname = findPreference<EditTextPreference>("immich_media_hostname")
        if (hostname?.text.toStringOrEmpty().isNotEmpty()) {
            hostname?.summary = hostname?.text
        } else {
            hostname?.summary = getString(R.string.immich_media_hostname_summary)
        }

        // Path name
        val pathname = findPreference<EditTextPreference>("immich_media_pathname")
        if (pathname?.text.toStringOrEmpty().isNotEmpty()) {
            val fixedShareName = ImmichMediaPrefs.pathName
            ImmichMediaPrefs.pathName = fixedShareName
            pathname?.summary = fixedShareName
            pathname?.text = fixedShareName
        } else {
            pathname?.summary = getString(R.string.immich_media_pathname_summary)
        }

        // Password
        val password = findPreference<EditTextPreference>("immich_media_password")
        if (password?.text.toStringOrEmpty().isNotEmpty()) {
            password?.summary = "*".repeat(ImmichMediaPrefs.password.length)
        } else {
            password?.summary = getString(R.string.immich_media_password_summary)
        }
    }

    private fun limitTextInput() {
        preferenceScreen.findPreference<EditTextPreference>("immich_media_hostname")
            ?.setOnBindEditTextListener { it.setSingleLine() }
        preferenceScreen.findPreference<EditTextPreference>("immich_media_path")
            ?.setOnBindEditTextListener { it.setSingleLine() }
        preferenceScreen.findPreference<EditTextPreference>("immich_media_username")
            ?.setOnBindEditTextListener { it.setSingleLine() }
        preferenceScreen.findPreference<EditTextPreference>("immich_media_password")
            ?.setOnBindEditTextListener { it.setSingleLine() }
    }

    private suspend fun testImmichConnection() =
        withContext(Dispatchers.IO) {
            val provider = ImmichMediaProvider(requireContext(), ImmichMediaPrefs)
            val result = provider.fetchTest()
            showDialog(resources.getString(R.string.immich_media_test_results), result)
        }

    private suspend fun showDialog(
        title: String = "",
        message: String,
    ) = withContext(Dispatchers.Main) {
        AlertDialog.Builder(requireContext()).apply {
            setTitle(title)
            setMessage(message)
            setPositiveButton(R.string.button_ok, null)
            create().show()
        }
    }

    companion object {
        private const val TAG = "ImmichVideosFragment"
    }
}
