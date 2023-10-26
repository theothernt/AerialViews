package com.neilturner.aerialviews.ui.sources

import android.Manifest
import android.content.SharedPreferences
import android.content.res.Resources
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
import androidx.preference.CheckBoxPreference
import androidx.preference.EditTextPreference
import androidx.preference.MultiSelectListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import com.google.modernstorage.permissions.StoragePermissions
import com.google.modernstorage.storage.AndroidFileSystem
import com.google.modernstorage.storage.toOkioPath
import com.hierynomus.mssmb2.SMB2Dialect
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.models.prefs.SambaVideoPrefs
import com.neilturner.aerialviews.providers.SambaVideoProvider
import com.neilturner.aerialviews.utils.FileHelper
import com.neilturner.aerialviews.utils.SambaHelper
import com.neilturner.aerialviews.utils.enumContains
import com.neilturner.aerialviews.utils.setSummaryFromValues
import com.neilturner.aerialviews.utils.toBoolean
import com.neilturner.aerialviews.utils.toStringOrEmpty
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okio.buffer
import java.io.ByteArrayInputStream
import java.util.Properties

class SambaVideosFragment :
    PreferenceFragmentCompat(),
    SharedPreferences.OnSharedPreferenceChangeListener,
    PreferenceManager.OnPreferenceTreeClickListener {
    private lateinit var fileSystem: AndroidFileSystem
    private lateinit var storagePermissions: StoragePermissions
    private lateinit var requestReadPermission: ActivityResultLauncher<String>
    private lateinit var requestWritePermission: ActivityResultLauncher<String>
    private lateinit var resources: Resources

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.sources_samba_videos, rootKey)
        preferenceManager.sharedPreferences?.registerOnSharedPreferenceChangeListener(this)

        resources = context?.resources!!
        fileSystem = AndroidFileSystem(requireContext())
        storagePermissions = StoragePermissions(requireContext())

        // Import/read permission request
        requestReadPermission = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (!isGranted) {
                lifecycleScope.launch {
                    showDialog(resources.getString(R.string.samba_videos_import_failed), resources.getString(R.string.samba_videos_permission_denied))
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
                    showDialog(resources.getString(R.string.samba_videos_export_failed), resources.getString(R.string.samba_videos_permission_denied))
                }
            } else {
                lifecycleScope.launch {
                    exportSettings()
                }
            }
        }

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
        updateSummary()
    }

    private fun updateSummary() {
        val dialects = findPreference<MultiSelectListPreference>("samba_videos_smb_dialects")
        dialects?.setSummaryFromValues(dialects.values)

        // Host name
        val hostname = findPreference<EditTextPreference>("samba_videos_hostname")
        if (hostname?.text.toStringOrEmpty().isNotEmpty()) {
            hostname?.summary = hostname?.text
        } else {
            hostname?.summary = getString(R.string.samba_videos_hostname_summary)
        }

        // Domain name
        val domainname = findPreference<EditTextPreference>("samba_videos_domainname")
        if (domainname?.text.toStringOrEmpty().isNotEmpty()) {
            domainname?.summary = domainname?.text
        } else {
            domainname?.summary = getString(R.string.samba_videos_domainname_summary)
        }

        // Share name
        val sharename = findPreference<EditTextPreference>("samba_videos_sharename")
        if (sharename?.text.toStringOrEmpty().isNotEmpty()) {
            val fixedShareName = SambaHelper.fixShareName(SambaVideoPrefs.shareName)
            SambaVideoPrefs.shareName = fixedShareName
            sharename?.summary = fixedShareName
            sharename?.text = fixedShareName
        } else {
            sharename?.summary = getString(R.string.samba_videos_sharename_summary)
        }

        // Username
        val username = findPreference<EditTextPreference>("samba_videos_username")
        if (username?.text.toStringOrEmpty().isNotEmpty()) {
            username?.summary = username?.text
        } else {
            username?.summary = getString(R.string.samba_videos_username_summary)
        }

        // Password
        val password = findPreference<EditTextPreference>("samba_videos_password")
        if (password?.text.toStringOrEmpty().isNotEmpty()) {
            password?.summary = "*".repeat(SambaVideoPrefs.password.length)
        } else {
            password?.summary = getString(R.string.samba_videos_password_summary)
        }

        // Subfolders
        val subfolders = findPreference<CheckBoxPreference>("samba_videos_search_subfolders")
        subfolders?.isChecked = SambaVideoPrefs.searchSubfolders

        // Encryption
        val encryption = findPreference<CheckBoxPreference>("samba_videos_enable_encryption")
        encryption?.isChecked = SambaVideoPrefs.enableEncryption
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

    private suspend fun importSettings() = withContext(Dispatchers.IO) {
        Log.i(TAG, "Importing SMB settings from Downloads folder")

        val directory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath
        val uri = Uri.parse("$directory/$SMB_SETTINGS_FILENAME")
        val path = uri.toOkioPath()
        val properties = Properties()

        if (!FileHelper.fileExists(uri)) {
            showDialog(resources.getString(R.string.samba_videos_import_failed), String.format(resources.getString(R.string.samba_videos_file_not_found), SMB_SETTINGS_FILENAME))
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
            showDialog(resources.getString(R.string.samba_videos_import_failed), resources.getString(R.string.samba_videos_error_parsing))
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

            val dialects = properties["smb_dialects"].toStringOrEmpty().split(",")
            val validDialects = dialects.filter { enumContains<SMB2Dialect>(it.trim()) }
            SambaVideoPrefs.smbDialects.clear()
            SambaVideoPrefs.smbDialects.addAll(validDialects)

            SambaVideoPrefs.searchSubfolders = properties["search_subfolders"].toBoolean()
            SambaVideoPrefs.enableEncryption = properties["enable_encryption"].toBoolean()
        } catch (ex: Exception) {
            showDialog(resources.getString(R.string.samba_videos_import_failed), resources.getString(R.string.samba_videos_unable_to_save))
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

            preferenceScreen.findPreference<CheckBoxPreference>("samba_videos_search_subfolders")?.isChecked
            SambaVideoPrefs.searchSubfolders
            preferenceScreen.findPreference<CheckBoxPreference>("samba_videos_enable_encryption")?.isChecked
            SambaVideoPrefs.enableEncryption

            preferenceScreen.findPreference<MultiSelectListPreference>("samba_videos_smb_dialects")?.values =
                SambaVideoPrefs.smbDialects.toSet()

            updateSummary()
        }

        showDialog(resources.getString(R.string.samba_videos_import_success), String.format(resources.getString(R.string.samba_videos_import_save_success), SMB_SETTINGS_FILENAME))
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

        smbSettings["smb_dialects"] = SambaVideoPrefs.smbDialects.joinToString(",").replace(" ", "")

        smbSettings["search_subfolders"] = SambaVideoPrefs.searchSubfolders.toString()
        smbSettings["enable_encryption"] = SambaVideoPrefs.enableEncryption.toString()

        val uri: Uri
        try {
            // Prep file handle
            uri = fileSystem.createMediaStoreUri(
                filename = SMB_SETTINGS_FILENAME,
                collection = MediaStore.Files.getContentUri("external"),
                directory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath
            )!!
        } catch (ex: Exception) {
            showDialog(resources.getString(R.string.samba_videos_export_failed), String.format(resources.getString(R.string.samba_videos_file_already_exists), SMB_SETTINGS_FILENAME))
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
            showDialog(resources.getString(R.string.samba_videos_export_failed), String.format(resources.getString(R.string.samba_videos_unable_to_write), SMB_SETTINGS_FILENAME))
            Log.e(TAG, "Export failed", ex)
            ex.cause?.let { Firebase.crashlytics.recordException(it) }
            return@withContext
        }

        showDialog(resources.getString(R.string.samba_videos_export_success), String.format(resources.getString(R.string.samba_videos_export_write_success), SMB_SETTINGS_FILENAME))
    }

    private suspend fun testSambaConnection() = withContext(Dispatchers.IO) {
        val provider = SambaVideoProvider(requireContext(), SambaVideoPrefs)
        val result = provider.fetchTest()
        showDialog(resources.getString(R.string.samba_videos_test_results), result)
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
