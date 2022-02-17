@file:Suppress("unused")

package com.neilturner.aerialviews.ui.settings

import android.Manifest
import android.content.SharedPreferences
import android.net.Uri
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
import com.google.modernstorage.permissions.StoragePermissions
import com.google.modernstorage.storage.AndroidFileSystem
import com.google.modernstorage.storage.toOkioPath
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.protocol.commons.EnumWithValue
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.connection.Connection
import com.hierynomus.smbj.session.Session
import com.hierynomus.smbj.share.DiskShare
import com.hierynomus.smbj.share.Share
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.models.prefs.NetworkVideoPrefs
import com.neilturner.aerialviews.utils.FileHelper
import com.neilturner.aerialviews.utils.SmbHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okio.buffer
import java.io.ByteArrayInputStream
import java.util.Properties


@Suppress("DEPRECATION")
class NetworkVideosFragment :
    PreferenceFragmentCompat(),
    PreferenceManager.OnPreferenceTreeClickListener,
    SharedPreferences.OnSharedPreferenceChangeListener {
    private var fileSystem: AndroidFileSystem? = null
    private var storagePermissions: StoragePermissions? = null
    private var requestReadPermission: ActivityResultLauncher<String>? = null
    private var requestWritePermission: ActivityResultLauncher<String>? = null

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings_network_videos, rootKey)
        preferenceManager.sharedPreferences?.registerOnSharedPreferenceChangeListener(this)

        fileSystem = AndroidFileSystem(requireContext())
        storagePermissions = StoragePermissions(requireContext())
        requestReadPermission = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (!isGranted) {
                showSimpleDialog("Unable to read SMB setting file: permission denied")
            } else {
                importSettings()
            }
        }
        requestWritePermission = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (!isGranted) {
                showSimpleDialog("Unable to write SMB setting file: permission denied")
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
            lifecycleScope.launch {
                withContext(Dispatchers.IO) {
                    testNetworkConnection()
                }
            }
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
        AlertDialog.Builder(requireContext()).apply {
            setTitle(R.string.network_videos_import_export_settings_title)
            setMessage(R.string.network_videos_import_export_settings_summary)
            setNeutralButton("Cancel", null)
            setNegativeButton("Import") { _, _ ->
                checkImportPermissions()
            }
            setPositiveButton("Export") { _, _ ->
                checkExportPermissions()
            }
            create().show()
        }
    }

    private fun checkImportPermissions() {
        val canReadFiles = storagePermissions?.hasAccess(
            action = StoragePermissions.Action.READ,
            types = listOf(StoragePermissions.FileType.Document),
            createdBy = StoragePermissions.CreatedBy.AllApps
        )!!

        if (!canReadFiles) {
            Log.i(TAG, "Asking for permission")
            requestReadPermission?.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
        } else {
            importSettings()
        }
    }

    private fun importSettings() {
        Log.i(TAG, "Importing settings from Downloads folder")

        val filename = "aerial-views-smb-settings.txt"
        val directory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath
        val uri = Uri.parse("$directory/$filename")
        val path = uri.toOkioPath()
        val properties = Properties()

        if (!FileHelper.fileExists(uri)) {
            showSimpleDialog("Can't find SMB settings file in Downloads folder: $filename")
            return
        }

        try {
            fileSystem?.source(path).use { file ->
                file?.buffer().use { buffer ->
                    val byteArray = buffer?.readByteArray()
                    properties.load(ByteArrayInputStream(byteArray))
                }
            }
        } catch (ex: Exception) {
            showSimpleDialog("Error while reading file")
            return
        }

        try {
            NetworkVideoPrefs.hostName = properties["hostname"] as String
            NetworkVideoPrefs.shareName = properties["sharename"] as String
            NetworkVideoPrefs.userName = properties["username"] as String
            NetworkVideoPrefs.password = properties["password"] as String
        } catch (ex: Exception) {
            showSimpleDialog("Error while trying to parse SMB settings")
            return
        }

        preferenceScreen.findPreference<EditTextPreference>("network_videos_hostname")?.text = NetworkVideoPrefs.hostName
        preferenceScreen.findPreference<EditTextPreference>("network_videos_sharename")?.text = NetworkVideoPrefs.shareName
        preferenceScreen.findPreference<EditTextPreference>("network_videos_username")?.text = NetworkVideoPrefs.userName
        preferenceScreen.findPreference<EditTextPreference>("network_videos_password")?.text = NetworkVideoPrefs.password

        Log.i(TAG, properties.toString())
        showSimpleDialog("Successfully imported SMB settings from Downloads folder")
    }

    private fun checkExportPermissions() {
        val canWriteFiles = storagePermissions?.hasAccess(
            action = StoragePermissions.Action.READ_AND_WRITE,
            types = listOf(StoragePermissions.FileType.Document),
            createdBy = StoragePermissions.CreatedBy.AllApps
        )!!

        if (!canWriteFiles) {
            Log.i(TAG, "Asking for permission")
            requestWritePermission?.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            exportSettings()
        }
    }

    private fun exportSettings() {
        Log.i(TAG, "Exporting settings to Downloads folder")

        // Build SMB config string
        val smbSettings = mutableMapOf<String, String>()
        smbSettings["hostname"] = NetworkVideoPrefs.hostName
        smbSettings["sharename"] = NetworkVideoPrefs.shareName
        smbSettings["username"] = NetworkVideoPrefs.userName
        smbSettings["password"] = NetworkVideoPrefs.password

        val filename = "aerial-views-smb-settings-${System.currentTimeMillis()}.txt"
        try {
            // Prep file handle
            val uri = fileSystem?.createMediaStoreUri(
                filename = filename,
                collection = MediaStore.Files.getContentUri("external"),
                directory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath
                // directory = context?.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.absolutePath
            )!!

            // Write to file
            val path = uri.toOkioPath()
            fileSystem?.write(path, false) {
                for ((key, value) in smbSettings) {
                    writeUtf8(key)
                    writeUtf8("=")
                    writeUtf8(value)
                    writeUtf8("\n")
                }
            }
        } catch (ex: Exception) {
            showSimpleDialog("Error while trying to write SMB settings file to Downloads folder: $filename")
            return
        }

        showSimpleDialog("Successfully exported SMB settings to Downloads folder: $filename")
    }

    private suspend fun testNetworkConnection() {
        // Check hostname
        val config = SmbHelper.buildSmbConfig()
        val smbClient = SMBClient(config)
        val connection: Connection
        try {
            connection = smbClient.connect(NetworkVideoPrefs.hostName)
        } catch (e: Exception) {
            Log.e(TAG, e.message!!)
            val message = "Unable to connect to hostname: ${NetworkVideoPrefs.hostName}...\n\n${e.message!!}"
            showDialog(message)
            return
        }
        Log.i(TAG, "Connected to ${NetworkVideoPrefs.hostName}")

        // Check username + password
        // Domain name fixed to default
        // Handles anonymous logins also
        val session: Session?
        try {
            val authContext = SmbHelper.buildAuthContext(NetworkVideoPrefs.userName, NetworkVideoPrefs.password, NetworkVideoPrefs.domainName)
            session = connection?.authenticate(authContext)
        } catch (e: Exception) {
            Log.e(TAG, e.message!!)
            val message = "Authentication failed...\n\n${e.message!!}"
            showDialog(message)
            return
        }
        Log.i(TAG, "Authentication successful")

        // Check sharename
        val share: Share?
        val path: String?
        var shareName = ""
        try {
            val shareNameAndPath = SmbHelper.parseShareAndPathName(Uri.parse(NetworkVideoPrefs.shareName))
            shareName = shareNameAndPath.first
            path = shareNameAndPath.second
            share = session?.connectShare(shareName) as DiskShare
            val shareAccess = hashSetOf<SMB2ShareAccess>()
            shareAccess.add(SMB2ShareAccess.ALL.iterator().next())
        } catch (e: Exception) {
            Log.e(TAG, e.message!!)
            val message = "Unable to connect to share: $shareName...\n\n${e.message!!}"
            showDialog(message)
            return
        }
        Log.i(TAG, "Connected to share: $shareName")

        // Check for any files
        // Check for any video files
        var files = 0 // ignore dot files
        var videos = 0
        try {
            share.list(path).forEach loop@{ item ->
                //Log.i(TAG, item.fileName)
                val isFolder = EnumWithValue.EnumUtils.isSet(
                    item.fileAttributes,
                    FileAttributes.FILE_ATTRIBUTE_DIRECTORY
                )
                if (!isFolder) {
                    if (FileHelper.isVideoFilename(item.fileName)) {
                        videos++
                    }
                    files++
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, e.message!!)
            val message = "Unable to list files from: $shareName...\n\n${e.message!!}"
            showDialog(message)
            return
        }

        val message = "Found $files files in total, $videos of which are videos."
        showDialog(message)
        Log.i(TAG, "Listed $files files from: $shareName")

        try {
            smbClient.close()
        } catch (e: Exception) {
            Log.e(TAG, e.message!!)
            return
        }
        Log.i(TAG, "Finished SMB connection test")
    }

    private fun showSimpleDialog(message: String) {
        AlertDialog.Builder(requireContext()).apply {
            setTitle("")
            setMessage(message)
            setPositiveButton("OK", null)
            create().show()
        }
    }

    private suspend fun showDialog(message: String) {
        withContext(Dispatchers.Main) {
            AlertDialog.Builder(requireContext()).apply {
                setTitle("")
                setMessage(message)
                setPositiveButton("OK", null)
                create().show()
            }
        }
    }

    companion object {
        private const val TAG = "NetworkVideoFragment"
    }
}
