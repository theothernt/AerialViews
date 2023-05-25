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
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import com.google.modernstorage.permissions.StoragePermissions
import com.google.modernstorage.storage.AndroidFileSystem
import com.google.modernstorage.storage.toOkioPath
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.models.LocationType
import com.neilturner.aerialviews.models.VideoQuality
import com.neilturner.aerialviews.models.prefs.AppleVideoPrefs
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.models.prefs.InterfacePrefs
import com.neilturner.aerialviews.models.prefs.LocalVideoPrefs
import com.neilturner.aerialviews.models.prefs.SambaVideoPrefs
import com.neilturner.aerialviews.utils.FileHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okio.buffer
import java.io.ByteArrayInputStream
import java.util.*

class SettingsFragment :
    PreferenceFragmentCompat(),
    PreferenceManager.OnPreferenceTreeClickListener {
    private lateinit var fileSystem: AndroidFileSystem
    private lateinit var storagePermissions: StoragePermissions
    private lateinit var requestReadPermission: ActivityResultLauncher<String>
    private lateinit var requestWritePermission: ActivityResultLauncher<String>

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings, rootKey)

        fileSystem = AndroidFileSystem(requireContext())
        storagePermissions = StoragePermissions(requireContext())

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
            setTitle(R.string.settings_import_export_settings_title)
            setMessage(R.string.settings_import_export_settings_summary)
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

    private suspend fun importSettings() = withContext(Dispatchers.IO) {
        Log.i(TAG, "Importing settings from Downloads folder")

        val directory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath
        val uri = Uri.parse("$directory/$SETTINGS_FILENAME")
        val path = uri.toOkioPath()
        val properties = Properties()

        if (!FileHelper.fileExists(uri)) {
            showDialog("Import failed", "Can't find settings file in Downloads folder: $SETTINGS_FILENAME")
            return@withContext
        }

        try {
            fileSystem.source(path).use { file ->
                file.buffer().use { buffer ->
                    val byteArray = buffer.readByteArray()
                    properties.load(ByteArrayInputStream(byteArray))
                }
            }
        } catch (ex: Exception) {
            showDialog("Import failed", "Error while reading and parsing file. Please check the file again for mistakes or invalid characters.")
            Log.e(TAG, "Import failed", ex)
            ex.cause?.let { Firebase.crashlytics.recordException(it) }
            return@withContext
        }

        try {
            // Apple prefs
            AppleVideoPrefs.enabled = properties["apple_videos_enabled"].toString().toBoolean()
            AppleVideoPrefs.quality = VideoQuality.valueOf(properties["apple_videos_quality"] as String)

            // Local video prefs
            LocalVideoPrefs.enabled = properties["local_videos_enabled"].toString().toBoolean()
            LocalVideoPrefs.filter_enabled = properties["local_videos_filter_enabled"].toString().toBoolean()
            LocalVideoPrefs.filter_folder = properties["local_videos_filter_folder_name"] as String

            // Samba video prefs?
            SambaVideoPrefs.enabled = properties["samba_videos_enabled"].toString().toBoolean()
            SambaVideoPrefs.enableEncryption = properties["samba_videos_enable_encryption"].toString().toBoolean()
            val smbDialects = (properties["samba_videos_smb_dialects"] as String).split(",").toList()
            SambaVideoPrefs.smbDialects.clear()
            SambaVideoPrefs.smbDialects.addAll(smbDialects)

            // Interface prefs
            InterfacePrefs.clockStyle = properties["show_clock"].toString().toBoolean()
            InterfacePrefs.locationStyle = LocationType.valueOf(properties["location_style"] as String)
            InterfacePrefs.alternateTextPosition = properties["alt_text_position"].toString().toBoolean()

            // General prefs
            GeneralPrefs.muteVideos = properties["mute_videos"].toString().toBoolean()
            GeneralPrefs.shuffleVideos = properties["shuffle_videos"].toString().toBoolean()
            GeneralPrefs.removeDuplicates = properties["remove_duplicates"].toString().toBoolean()
            GeneralPrefs.enableSkipVideos = properties["enable_skip_videos"].toString().toBoolean()
            GeneralPrefs.enablePlaybackSpeedChange = properties["enable_playback_speed_change"].toString().toBoolean()
            GeneralPrefs.playbackSpeed = properties["playback_speed"] as String

            GeneralPrefs.enableTunneling = properties["enable_tunneling"].toString().toBoolean()
            GeneralPrefs.exceedRenderer = properties["exceed_renderer"].toString().toBoolean()
            // GeneralPrefs.bufferingStrategy = properties["performance_buffering_strategy"] as String

            GeneralPrefs.filenameAsLocation = properties["any_videos_filename_location"].toString().toBoolean()
            GeneralPrefs.useAppleManifests = properties["any_videos_use_apple_manifests"].toString().toBoolean()
            GeneralPrefs.useCustomManifests = properties["any_videos_use_custom_manifests"].toString().toBoolean()
            GeneralPrefs.ignoreNonManifestVideos = properties["any_videos_ignore_non_manifest_videos"].toString().toBoolean()
        } catch (ex: Exception) {
            showDialog("Import failed", "Unable to save imported settings")
            Log.e(TAG, "Import failed", ex)
            ex.cause?.let { Firebase.crashlytics.recordException(it) }
            return@withContext
        }

        showDialog("Import successful", "Settings successfully imported from $SETTINGS_FILENAME")
    }

    private fun checkExportPermissions() {
        val canWriteFiles = storagePermissions.hasAccess(
            action = StoragePermissions.Action.READ_AND_WRITE,
            types = listOf(StoragePermissions.FileType.Document),
            createdBy = StoragePermissions.CreatedBy.Self
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
        settings["local_videos_filter_folder_name"] = LocalVideoPrefs.filter_folder

        // Samba video prefs?
        settings["samba_videos_enabled"] = SambaVideoPrefs.enabled.toString()
        settings["samba_videos_enable_encryption"] = SambaVideoPrefs.enableEncryption.toString()
        val smbDialects = SambaVideoPrefs.smbDialects.joinToString(separator = ",")
        settings["samba_videos_smb_dialects"] = smbDialects

        // Interface prefs
        settings["show_clock"] = InterfacePrefs.clockStyle.toString()
        settings["location_style"] = InterfacePrefs.locationStyle.toString()
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
        // settings["performance_buffering_strategy"] = GeneralPrefs.bufferingStrategy

        settings["any_videos_filename_location"] = GeneralPrefs.filenameAsLocation.toString()
        settings["any_videos_use_apple_manifests"] = GeneralPrefs.useAppleManifests.toString()
        settings["any_videos_use_custom_manifests"] = GeneralPrefs.useCustomManifests.toString()
        settings["any_videos_ignore_non_manifest_videos"] = GeneralPrefs.ignoreNonManifestVideos.toString()

        val uri: Uri
        try {
            // Prep file handle
            uri = fileSystem.createMediaStoreUri(
                filename = SETTINGS_FILENAME,
                collection = MediaStore.Files.getContentUri("external"),
                directory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath
            )!!
        } catch (ex: Exception) {
            showDialog("Export failed", "The settings file $SETTINGS_FILENAME already exists in the Downloads folder")
            Log.e(TAG, "Export failed", ex)
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
            showDialog("Export failed", "Error while trying to write settings to $SETTINGS_FILENAME in the Downloads folder")
            Log.e(TAG, "Import failed", ex)
            ex.cause?.let { Firebase.crashlytics.recordException(it) }
            return@withContext
        }

        showDialog("Export successful", "Successfully exported settings to $SETTINGS_FILENAME in the Downloads folder")
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
        private const val TAG = "CustomiseVideosFragment"
        private const val SETTINGS_FILENAME = "aerial-views-settings.txt"
    }
}
