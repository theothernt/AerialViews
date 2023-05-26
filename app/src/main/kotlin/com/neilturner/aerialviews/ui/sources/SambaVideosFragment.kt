@file:Suppress("unused", "BlockingMethodInNonBlockingContext")

package com.neilturner.aerialviews.ui.sources

import android.Manifest
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import com.google.modernstorage.permissions.StoragePermissions
import com.google.modernstorage.storage.AndroidFileSystem
import com.google.modernstorage.storage.toOkioPath
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.models.prefs.SambaVideoPrefs
import com.neilturner.aerialviews.providers.SambaVideoProvider
import com.neilturner.aerialviews.utils.FileHelper
import com.neilturner.aerialviews.utils.SambaHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okio.buffer
import java.io.ByteArrayInputStream
import java.util.Properties

class SambaVideosFragment :
    PreferenceFragmentCompat(),
    PreferenceManager.OnPreferenceTreeClickListener,
    SharedPreferences.OnSharedPreferenceChangeListener {
    private lateinit var fileSystem: AndroidFileSystem
    private lateinit var storagePermissions: StoragePermissions
    private lateinit var requestReadPermission: ActivityResultLauncher<String>
    private lateinit var requestWritePermission: ActivityResultLauncher<String>

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.sources_samba_videos, rootKey)
        preferenceManager.sharedPreferences?.registerOnSharedPreferenceChangeListener(this)

        fileSystem = AndroidFileSystem(requireContext())
        storagePermissions = StoragePermissions(requireContext())

        // Import/read permission request
        requestReadPermission = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (!isGranted) {
                lifecycleScope.launch {
                    showDialog("Import failed", "Unable to read SMB setting file: permission denied")
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
                    showDialog("Export failed", "Unable to write SMB setting file: permission denied")
                }
            } else {
                lifecycleScope.launch {
                    exportSettings()
                }
            }
        }

        limitTextInput()
    }

    override fun onDestroy() {
        preferenceManager.sharedPreferences?.unregisterOnSharedPreferenceChangeListener(this)
        super.onDestroy()
    }

    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        if (preference.key.isNullOrEmpty()) {
            return super.onPreferenceTreeClick(preference)
        }

        if (preference.key.contains("samba_videos_test_connection")) {
            lifecycleScope.launch {
                testSambaConnection()
            }
            return true
        }

        if (preference.key.contains("samba_videos_import_export_settings")) {
            importExportSettings()
            return true
        }

        return super.onPreferenceTreeClick(preference)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String) {
        if (key == "samba_videos_sharename") {
            SambaVideoPrefs.shareName = SambaHelper.fixShareName(SambaVideoPrefs.shareName)
        }
    }

    private fun limitTextInput() {
        preferenceScreen.findPreference<EditTextPreference>("samba_videos_hostname")?.setOnBindEditTextListener { it.setSingleLine() }
        preferenceScreen.findPreference<EditTextPreference>("samba_videos_domainname")?.setOnBindEditTextListener { it.setSingleLine() }
        preferenceScreen.findPreference<EditTextPreference>("samba_videos_sharename")?.setOnBindEditTextListener { it.setSingleLine() }
        preferenceScreen.findPreference<EditTextPreference>("samba_videos_username")?.setOnBindEditTextListener { it.setSingleLine() }
        preferenceScreen.findPreference<EditTextPreference>("samba_videos_password")?.setOnBindEditTextListener { it.setSingleLine() }
    }

    private fun importExportSettings() {
        AlertDialog.Builder(requireContext()).apply {
            setTitle(R.string.samba_videos_import_export_settings_title)
            setMessage(R.string.samba_videos_import_export_settings_summary)
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
            createdBy = StoragePermissions.CreatedBy.Self
        )

        if (!canReadFiles) {
            Log.i(TAG, "Asking for permission")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                requestReadPermission.launch(Manifest.permission.READ_MEDIA_VIDEO)
            } else {
                requestReadPermission.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        } else {
            lifecycleScope.launch {
                importSettings()
            }
        }
    }

    @Suppress("BlockingMethodInNonBlockingContext") // code runs inside Dispatcher.IO
    private suspend fun importSettings() = withContext(Dispatchers.IO) {
        Log.i(TAG, "Importing SMB settings from Downloads folder")

        val directory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath
        val uri = Uri.parse("$directory/$SMB_SETTINGS_FILENAME")
        val path = uri.toOkioPath()
        val properties = Properties()

        if (!FileHelper.fileExists(uri)) {
            showDialog("Import failed", "Can't find SMB settings file in Downloads folder: $SMB_SETTINGS_FILENAME")
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
            SambaVideoPrefs.hostName = properties["hostname"] as String
            SambaVideoPrefs.domainName = properties["domainname"] as String
            SambaVideoPrefs.shareName = properties["sharename"] as String
            SambaVideoPrefs.userName = properties["username"] as String
            SambaVideoPrefs.password = properties["password"] as String
        } catch (ex: Exception) {
            showDialog("Import failed", "Unable to save imported settings")
            Log.e(TAG, "Import failed", ex)
            ex.cause?.let { Firebase.crashlytics.recordException(it) }
            return@withContext
        }

        withContext(Dispatchers.Main) {
            preferenceScreen.findPreference<EditTextPreference>("samba_videos_hostname")?.text = SambaVideoPrefs.hostName
            preferenceScreen.findPreference<EditTextPreference>("samba_videos_domainname")?.text = SambaVideoPrefs.domainName
            preferenceScreen.findPreference<EditTextPreference>("samba_videos_sharename")?.text = SambaVideoPrefs.shareName
            preferenceScreen.findPreference<EditTextPreference>("samba_videos_username")?.text = SambaVideoPrefs.userName
            preferenceScreen.findPreference<EditTextPreference>("samba_videos_password")?.text = SambaVideoPrefs.password
        }

        showDialog("Import successful", "SMB settings successfully imported from $SMB_SETTINGS_FILENAME")
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
        Log.i(TAG, "Exporting SMB settings to Downloads folder")

        // Build SMB config list
        val smbSettings = mutableMapOf<String, String>()
        smbSettings["hostname"] = SambaVideoPrefs.hostName
        smbSettings["domainname"] = SambaVideoPrefs.domainName
        smbSettings["sharename"] = SambaVideoPrefs.shareName
        smbSettings["username"] = SambaVideoPrefs.userName
        smbSettings["password"] = SambaVideoPrefs.password

        val uri: Uri
        try {
            // Prep file handle
            uri = fileSystem.createMediaStoreUri(
                filename = SMB_SETTINGS_FILENAME,
                collection = MediaStore.Files.getContentUri("external"),
                directory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath
            )!!
        } catch (ex: Exception) {
            showDialog("Export failed", "The SMB settings file $SMB_SETTINGS_FILENAME already exists in the Downloads folder")
            Log.e(TAG, "Export failed", ex)
            return@withContext
        }

        try {
            // Write to file
            val path = uri.toOkioPath()
            fileSystem.write(path, false) {
                for ((key, value) in smbSettings) {
                    writeUtf8(key)
                    writeUtf8("=")
                    writeUtf8(value)
                    writeUtf8("\n")
                }
            }
        } catch (ex: Exception) {
            showDialog("Export failed", "Error while trying to write SMB settings to $SMB_SETTINGS_FILENAME in the Downloads folder")
            Log.e(TAG, "Export failed", ex)
            ex.cause?.let { Firebase.crashlytics.recordException(it) }
            return@withContext
        }

        showDialog("Export successful", "Successfully exported SMB settings to $SMB_SETTINGS_FILENAME in the Downloads folder")
    }

    private suspend fun testSambaConnection() = withContext(Dispatchers.IO) {
        val provider = SambaVideoProvider(requireContext(), SambaVideoPrefs)
        val result = provider.fetchTest()
        showDialog("Results", result)
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
        private const val TAG = "SambaVideosFragment"
        private const val SMB_SETTINGS_FILENAME = "aerial-views-smb-settings.txt"
    }
}
