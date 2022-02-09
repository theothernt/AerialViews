@file:Suppress("unused")

package com.neilturner.aerialviews.ui.settings

import android.Manifest
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
import com.neilturner.aerialviews.models.prefs.LocalVideoPrefs
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
    private var requestReadPermission: ActivityResultLauncher<String>? = null
    private var requestWritePermission: ActivityResultLauncher<String>? = null

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings_network_videos, rootKey)
        preferenceManager.sharedPreferences?.registerOnSharedPreferenceChangeListener(this)

        storagePermissions = StoragePermissions(requireContext())
        requestReadPermission = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (!isGranted) {
                showMessage("Unable to read SMB setting file: permission denied")
            } else {
                importSettings()
            }
        }
        requestWritePermission = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (!isGranted) {
                showMessage("Unable to write SMB setting file: permission denied")
            } else {
                exportSettings()
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
                checkImportPermissions()
            }
            setPositiveButton("Export") { _, _ ->
                checkExportPermissions()
            }
        }
        val dialog = builder.create()
        dialog.show()
    }

    private fun checkImportPermissions() {
        val canReadFiles = storagePermissions?.hasAccess(
            action = StoragePermissions.Action.READ,
            types = listOf(StoragePermissions.FileType.Document),
            createdBy = StoragePermissions.CreatedBy.AllApps
        )!!

        Log.i(TAG, "Can Read Files: $canReadFiles")
        if (!canReadFiles) {
            requestReadPermission?.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    private fun importSettings() {

    }

    private fun checkExportPermissions() {
        val canWriteFiles = storagePermissions?.hasAccess(
            action = StoragePermissions.Action.READ_AND_WRITE,
            types = listOf(StoragePermissions.FileType.Document),
            createdBy = StoragePermissions.CreatedBy.AllApps
        )!!

        Log.i(TAG, "Can Write Files: $canWriteFiles")
        if (!canWriteFiles) {
            requestWritePermission?.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }

    private fun exportSettings() {

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
