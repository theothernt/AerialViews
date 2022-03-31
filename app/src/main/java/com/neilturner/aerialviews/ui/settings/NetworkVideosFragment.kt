@file:Suppress("unused", "BlockingMethodInNonBlockingContext")

package com.neilturner.aerialviews.ui.settings

import android.Manifest
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.util.Patterns
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
import com.hierynomus.smbj.SmbConfig
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
    private lateinit var fileSystem: AndroidFileSystem
    private lateinit var storagePermissions: StoragePermissions
    private lateinit var requestReadPermission: ActivityResultLauncher<String>
    private lateinit var requestWritePermission: ActivityResultLauncher<String>

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings_network_videos, rootKey)
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
        if (preference.key.isNullOrEmpty())
            return super.onPreferenceTreeClick(preference)

        if (preference.key.contains("network_videos_test_connection")) {
            lifecycleScope.launch {
                testNetworkConnection()
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

        val filename = "aerial-views-smb-settings.txt"
        val directory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath
        val uri = Uri.parse("$directory/$filename")
        val path = uri.toOkioPath()
        val properties = Properties()

        if (!FileHelper.fileExists(uri)) {
            showDialog("Import failed", "Can't find SMB settings file in Downloads folder: $filename")
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
            NetworkVideoPrefs.hostName = properties["hostname"] as String
            NetworkVideoPrefs.shareName = properties["sharename"] as String
            NetworkVideoPrefs.userName = properties["username"] as String
            NetworkVideoPrefs.password = properties["password"] as String
        } catch (ex: Exception) {
            showDialog("Import failed", "Unable to save imported settings")
            return@withContext
        }

        withContext(Dispatchers.Main) {
            preferenceScreen.findPreference<EditTextPreference>("network_videos_hostname")?.text = NetworkVideoPrefs.hostName
            preferenceScreen.findPreference<EditTextPreference>("network_videos_sharename")?.text = NetworkVideoPrefs.shareName
            preferenceScreen.findPreference<EditTextPreference>("network_videos_username")?.text = NetworkVideoPrefs.userName
            preferenceScreen.findPreference<EditTextPreference>("network_videos_password")?.text = NetworkVideoPrefs.password
        }

        Log.i(TAG, properties.toString())
        showDialog("Import successful", "SMB settings successfully imported from $filename")
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

        // Build SMB config string
        val smbSettings = mutableMapOf<String, String>()
        smbSettings["hostname"] = NetworkVideoPrefs.hostName
        smbSettings["sharename"] = NetworkVideoPrefs.shareName
        smbSettings["username"] = NetworkVideoPrefs.userName
        smbSettings["password"] = NetworkVideoPrefs.password

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
                for ((key, value) in smbSettings) {
                    writeUtf8(key)
                    writeUtf8("=")
                    writeUtf8(value)
                    writeUtf8("\n")
                }
            }
        } catch (ex: Exception) {
            showDialog("Export failed", "Error while trying to write SMB settings to $filename in the Downloads folder")
            return@withContext
        }

        showDialog("Export successful", "Successfully exported SMB settings to $filename in the Downloads folder")
    }

    @Suppress("BlockingMethodInNonBlockingContext") // ran on an IO/background context
    private suspend fun testNetworkConnection() = withContext(Dispatchers.IO) {

        // Check hostname
        val validIpAddress = Patterns.IP_ADDRESS.matcher(NetworkVideoPrefs.hostName).matches()
        if (!validIpAddress) {
            val message = "Hostname must be a valid IP address."
            Log.e(TAG, message)
            showDialog("Error", message)
            return@withContext
        }

        // Check hostname
        val config: SmbConfig
        try {
            config = SmbHelper.buildSmbConfig()
        } catch (e: Exception) {
            Log.e(TAG, e.message!!)
            val message = "Failed to create SMB config...\n\n${e.message!!}"
            showDialog("Connection error", message)
            return@withContext
        }
        Log.i(TAG, "SMB config successful")

        val smbClient = SMBClient(config)
        val connection: Connection
        try {
            connection = smbClient.connect(NetworkVideoPrefs.hostName)
        } catch (e: Exception) {
            Log.e(TAG, e.message!!)
            val message = "Hostname error: ${NetworkVideoPrefs.hostName}...\n\n${e.message!!}"
            showDialog("Connection error", message)
            return@withContext
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
            val message = "Authentication failed. Please check the username and password, or server settings if using anonymous login"
            showDialog("Connection error", message)
            return@withContext
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
            val message = "Unable to connect to share: $shareName. Please check the spelling of the share name or the server permissions."
            showDialog("Connection error", message)
            return@withContext
        }
        Log.i(TAG, "Connected to share: $shareName")

        // Check for any files
        // Check for any video files
        var files = 0 // ignore dot files
        var videos = 0
        var folders = 0
        try {
            share.list(path).forEach { item ->
                // Log.i(TAG, item.fileName)
                val isFolder = EnumWithValue.EnumUtils.isSet(
                    item.fileAttributes,
                    FileAttributes.FILE_ATTRIBUTE_DIRECTORY
                )
                if (isFolder) {
                    folders++
                } else {
                    if (FileHelper.isVideoFilename(item.fileName)) {
                        videos++
                    }
                    files++
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, e.message!!)
            val message = "Unable to list files from: $shareName. Please check server permissions for this share."
            showDialog("Connection error", message)
            return@withContext
        }

        var message: String
        val ignored = files - videos
        if (files == 0) {
            message = "No files or videos found!"
        } else {
            message = "Found $videos videos. "
            if (ignored > 0) {
                message += "$ignored non-video files were ignored."
            }
        }
        showDialog("Connection successful", message)
        Log.i(TAG, message)

        try {
            smbClient.close()
        } catch (e: Exception) {
            Log.e(TAG, e.message!!)
            return@withContext
        }
        Log.i(TAG, "Finished SMB connection test")
    }

    private suspend fun showDialog(title: String = "", message: String) = withContext(Dispatchers.Main) {
        AlertDialog.Builder(requireContext()).apply {
            setTitle(title)
            setMessage(message)
            setPositiveButton("OK", null)
            create().show()
        }
    }

    companion object {
        private const val TAG = "NetworkVideosFragment"
    }
}
