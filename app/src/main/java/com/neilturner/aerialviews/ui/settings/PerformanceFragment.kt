@file:Suppress("unused")

package com.neilturner.aerialviews.ui.settings

import android.Manifest
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import com.google.modernstorage.permissions.StoragePermissions
import com.google.modernstorage.storage.AndroidFileSystem
import com.google.modernstorage.storage.toOkioPath
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.models.AppleVideoQuality
import com.neilturner.aerialviews.models.LocationStyle
import com.neilturner.aerialviews.models.prefs.*
import com.neilturner.aerialviews.utils.FileHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.text.split
import okio.buffer
import java.io.ByteArrayInputStream
import java.util.*

class PerformanceFragment : PreferenceFragmentCompat(),
    PreferenceManager.OnPreferenceTreeClickListener {
    private lateinit var fileSystem: AndroidFileSystem
    private lateinit var storagePermissions: StoragePermissions
    private lateinit var requestReadPermission: ActivityResultLauncher<String>
    private lateinit var requestWritePermission: ActivityResultLauncher<String>

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings_performance, rootKey)

        // Import/read permission request
        requestReadPermission = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (!isGranted) {
                lifecycleScope.launch {
                    showDialog("Import failed", "Unable to read setting file: permission denied")
                }
            } else {
                lifecycleScope.launch {
                    importSettings()
                }
            }
        }

        // Export/write permission request
        requestWritePermission = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (!isGranted) {
                lifecycleScope.launch {
                    showDialog("Export failed", "Unable to write setting file: permission denied")
                }
            } else {
                lifecycleScope.launch {
                    exportSettings()
                }
            }
        }
    }

    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        if (preference.key.isNullOrEmpty()) {
            return super.onPreferenceTreeClick(preference)
        }

        if (preference.key.contains("general_import_export_settings")) {
            importExportSettings()
            return true
        }

        return super.onPreferenceTreeClick(preference)
    }

    private fun importExportSettings() {
        AlertDialog.Builder(requireContext()).apply {
            setTitle(R.string.general_import_export_settings_title)
            setMessage(R.string.general_import_export_settings_summary)
            setNeutralButton(R.string.button_cancel, null)
            setNegativeButton(R.string.button_import) { _, _ ->
                checkImportPermissions()
            }
            setPositiveButton(R.string.button_export) { _, _ ->
                checkExportPermissions()
            }
            create().show()
        }
    }

    private fun checkImportPermissions() {
        val canReadFiles = storagePermissions.hasAccess(
            action = StoragePermissions.Action.READ,
            types = listOf(StoragePermissions.FileType.Document),
            createdBy = StoragePermissions.CreatedBy.AllApps
        )

        if (!canReadFiles) {
            Log.i(TAG, "Asking for permission")
            requestReadPermission.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
        } else {
            lifecycleScope.launch {
                importSettings()
            }
        }
    }

    @Suppress("BlockingMethodInNonBlockingContext") // code runs inside Dispatcher.IO
    private suspend fun importSettings() = withContext(Dispatchers.IO) {
        Log.i(TAG, "Importing settings from Downloads folder")

        val filename = "aerial-views-settings.txt"
        val directory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath
        val uri = Uri.parse("$directory/$filename")
        val path = uri.toOkioPath()
        val properties = Properties()

        if (!FileHelper.fileExists(uri)) {
            showDialog("Import failed", "Can't find settings file in Downloads folder: $filename")
            return@withContext
        }

        try {
            fileSystem.source(path).use { file ->
                file.buffer().use { buffer ->
                    val byteArray = buffer.readByteArray()
                    properties.load(ByteArrayInputStream(byteArray))
                }
            }
        } catch (e: Exception) {
            showDialog("Import failed", "Error while reading and parsing file. Please check the file again for mistakes or invalid characters.")
            return@withContext
        }

        try {
            // Apple prefs
            AppleVideoPrefs.enabled = properties["apple_videos_enabled"] as Boolean
            AppleVideoPrefs.quality = AppleVideoQuality.valueOf(properties["apple_videos_quality"] as String)

            // Local video prefs
            LocalVideoPrefs.enabled = properties["local_videos_enabled"] as Boolean
            LocalVideoPrefs.filter_enabled = properties["local_videos_filter_enabled"] as Boolean
            LocalVideoPrefs.filter_folder_name = properties["local_videos_filter_folder_name"] as String

            // Network video prefs?
            NetworkVideoPrefs.enabled = properties["network_videos_enabled"] as Boolean
            NetworkVideoPrefs.enableEncryption  = properties["network_videos_enable_encryption"] as Boolean
            val smbDialects = (properties["network_videos_smb_dialects"] as String).split(",").toList()
            NetworkVideoPrefs.smbDialects.clear()
            NetworkVideoPrefs.smbDialects.addAll(smbDialects)

            // Interface prefs
            InterfacePrefs.showClock = properties["show_clock"] as Boolean
            InterfacePrefs.showLocation = properties["show_location"] as Boolean
            InterfacePrefs.showLocationStyle = LocationStyle.valueOf(properties["show_location_style"] as String)
            InterfacePrefs.alternateTextPosition = properties["alt_text_position"] as Boolean

            // General prefs
            GeneralPrefs.muteVideos = properties["mute_videos"] as Boolean
            GeneralPrefs.shuffleVideos = properties["shuffle_videos"] as Boolean
            GeneralPrefs.removeDuplicates = properties["remove_duplicates"] as Boolean
            GeneralPrefs.enableSkipVideos = properties["enable_skip_videos"] as Boolean
            GeneralPrefs.enablePlaybackSpeedChange = properties["enable_playback_speed_change"] as Boolean
            GeneralPrefs.playbackSpeed = properties["playback_speed"] as String

            GeneralPrefs.enableTunneling = properties["enable_tunneling"] as Boolean
            GeneralPrefs.exceedRenderer = properties["exceed_renderer"] as Boolean
            GeneralPrefs.bufferingStrategy = properties["performance_buffering_strategy"] as String

            GeneralPrefs.filenameAsLocation = properties["any_videos_filename_location"] as Boolean
            GeneralPrefs.useAppleManifests = properties["any_videos_use_apple_manifests"] as Boolean
            GeneralPrefs.useCustomManifests = properties["any_videos_use_custom_manifests"] as Boolean
            GeneralPrefs.ignoreNonManifestVideos = properties["any_videos_ignore_non_manifest_videos"] as Boolean
        } catch (ex: Exception) {
            showDialog("Import failed", "Unable to save imported settings")
            return@withContext
        }

        Log.i(TAG, properties.toString())
        showDialog("Import successful", "Settings successfully imported from $filename")
    }

    private fun checkExportPermissions() {
        val canWriteFiles = storagePermissions.hasAccess(
            action = StoragePermissions.Action.READ_AND_WRITE,
            types = listOf(StoragePermissions.FileType.Document),
            createdBy = StoragePermissions.CreatedBy.AllApps
        )

        if (!canWriteFiles) {
            Log.i(TAG, "Asking for permission")
            requestWritePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            lifecycleScope.launch {
                exportSettings()
            }
        }
    }

    private suspend fun exportSettings() = withContext(Dispatchers.IO) {
        Log.i(TAG, "Exporting settings to Downloads folder")

        // Build settings list
        val settings = mutableMapOf<String, String>()

        // Apple prefs
        settings["apple_videos_enabled"] = AppleVideoPrefs.enabled.toString()
        settings["apple_videos_quality"] = AppleVideoPrefs.quality.toString()

        // Local video prefs
        settings["local_videos_enabled"] = LocalVideoPrefs.enabled.toString()
        settings["local_videos_filter_enabled"] = LocalVideoPrefs.filter_enabled.toString()
        settings["local_videos_filter_folder_name"] = LocalVideoPrefs.filter_folder_name

        // Network video prefs?
        settings["network_videos_enabled"] = NetworkVideoPrefs.enabled.toString()
        settings["network_videos_enable_encryption"] = NetworkVideoPrefs.enableEncryption.toString()
        val smbDialects = NetworkVideoPrefs.smbDialects.joinToString(separator = ",")
        settings["network_videos_smb_dialects"] = smbDialects

        // Interface prefs
        settings["show_clock"] = InterfacePrefs.showClock.toString()
        settings["show_location"] = InterfacePrefs.showLocation.toString()
        settings["show_location_style"] = InterfacePrefs.showLocationStyle.toString()
        settings["alt_text_position"] = InterfacePrefs.alternateTextPosition.toString()

        // General prefs
        settings["mute_videos"] = GeneralPrefs.muteVideos.toString()
        settings["shuffle_videos"] = GeneralPrefs.shuffleVideos.toString()
        settings["remove_duplicates"] = GeneralPrefs.removeDuplicates.toString()
        settings["enable_skip_videos"] = GeneralPrefs.enableSkipVideos.toString()
        settings["enable_playback_speed_change"] = GeneralPrefs.enablePlaybackSpeedChange.toString()
        settings["playback_speed"] = GeneralPrefs.playbackSpeed

        settings["enable_tunneling"] = GeneralPrefs.enableTunneling.toString()
        settings["exceed_renderer"] = GeneralPrefs.exceedRenderer.toString()
        settings["performance_buffering_strategy"] = GeneralPrefs.bufferingStrategy

        settings["any_videos_filename_location"] = GeneralPrefs.filenameAsLocation.toString()
        settings["any_videos_use_apple_manifests"] = GeneralPrefs.useAppleManifests.toString()
        settings["any_videos_use_custom_manifests"] = GeneralPrefs.useCustomManifests.toString()
        settings["any_videos_ignore_non_manifest_videos"] = GeneralPrefs.ignoreNonManifestVideos.toString()

        val filename = "aerial-views-smb-settings.txt"
        val uri: Uri
        try {
            // Prep file handle
            uri = fileSystem.createMediaStoreUri(
                filename = filename,
                collection = MediaStore.Files.getContentUri("external"),
                directory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath
            )!!
        } catch (ex: Exception) {
            showDialog("Export failed", "The SMB settings file $filename already exists in the Downloads folder")
            return@withContext
        }

        try {
            // Write to file
            val path = uri.toOkioPath()
            fileSystem.write(path, false) {
                for ((key, value) in settings) {
                    writeUtf8(key)
                    writeUtf8("=")
                    writeUtf8(value)
                    writeUtf8("\n")
                }
            }
        } catch (ex: Exception) {
            showDialog("Export failed", "Error while trying to write settings to $filename in the Downloads folder")
            return@withContext
        }

        showDialog("Export successful", "Successfully exported settings to $filename in the Downloads folder")
    }

    private suspend fun showDialog(title: String = "", message: String) = withContext(Dispatchers.Main) {
        AlertDialog.Builder(requireContext()).apply {
            setTitle(title)
            setMessage(message)
            setPositiveButton(R.string.button_ok, null)
            create().show()
        }
    }

    companion object {
        private const val TAG = "PerfVideosFragment"
    }
}
