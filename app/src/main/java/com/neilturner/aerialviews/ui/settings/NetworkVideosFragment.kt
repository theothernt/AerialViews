@file:Suppress("unused")

package com.neilturner.aerialviews.ui.settings

import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import com.google.modernstorage.permissions.StoragePermissions
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.models.prefs.NetworkVideoPrefs
import com.neilturner.aerialviews.models.videos.AerialVideo
import com.neilturner.aerialviews.providers.NetworkVideoProvider
import com.neilturner.aerialviews.utils.SmbHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

class NetworkVideosFragment :
    PreferenceFragmentCompat(),
    PreferenceManager.OnPreferenceTreeClickListener,
    SharedPreferences.OnSharedPreferenceChangeListener {
    private var storagePermissions: StoragePermissions? = null
    private var requestPermission: ActivityResultLauncher<String>? = null

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings_network_videos, rootKey)
        preferenceManager.sharedPreferences?.registerOnSharedPreferenceChangeListener(this)

        storagePermissions = StoragePermissions(requireContext())
        requestPermission = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (!isGranted) {
            }
        }

        limitTextInput()
    }

    override fun onDestroy() {
        preferenceManager.sharedPreferences?.unregisterOnSharedPreferenceChangeListener(this)
        super.onDestroy()
    }

    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        if (preference.key.isNullOrEmpty())
            return super.onPreferenceTreeClick(preference)

        if (preference.key.contains("network_videos_test_connection")) {
            testNetworkConnection()
            return true
        }

        if (preference.key.contains("network_videos_import_export_settings")) {
            importExportSettings()
            return true
        }

        return super.onPreferenceTreeClick(preference)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String) {
        if (key == "network_videos_sharename") {
            NetworkVideoPrefs.shareName = SmbHelper.fixShareName(NetworkVideoPrefs.shareName)
        }
    }

    private fun limitTextInput() {
        preferenceScreen.findPreference<EditTextPreference>("network_videos_hostname")?.setOnBindEditTextListener { it.setSingleLine() }
        preferenceScreen.findPreference<EditTextPreference>("network_videos_sharename")?.setOnBindEditTextListener { it.setSingleLine() }
        preferenceScreen.findPreference<EditTextPreference>("network_videos_username")?.setOnBindEditTextListener { it.setSingleLine() }
        preferenceScreen.findPreference<EditTextPreference>("network_videos_password")?.setOnBindEditTextListener { it.setSingleLine() }
    }

    private fun importExportSettings() {
        val builder = AlertDialog.Builder(requireContext())
        builder.apply {
            setTitle(R.string.network_videos_import_export_settings_title)
            setMessage(R.string.network_videos_import_export_settings_summary)
            setNeutralButton("Cancel", null)
            setNegativeButton("Import") { _, _ ->
                Log.i("", "Import...")
            }
            setPositiveButton("Export") { _, _ ->
                Log.i("", "Export...")
            }
        }
        val dialog = builder.create()
        dialog.show()
    }

    private fun testNetworkConnection() {
        showMessage("Connecting...")
        val provider = NetworkVideoProvider(this.requireContext(), NetworkVideoPrefs)

        try {
            val videos = mutableListOf<AerialVideo>()
            runBlocking {
                withContext(Dispatchers.IO) {
                    videos.addAll(provider.fetchVideos())
                }
            }
            showMessage("Connected, found ${videos.size} video file(s)")

            videos.forEach { video ->
                Log.i(TAG, "${video.location}: ${video.uri}")
            }
        } catch (e: Exception) {
            showMessage("Failed to connect")
        }
    }

    private fun showMessage(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
    }

    companion object {
        private const val TAG = "NetworkVideoFragment"
    }
}
