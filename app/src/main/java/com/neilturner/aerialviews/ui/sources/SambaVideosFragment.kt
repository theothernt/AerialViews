package com.neilturner.aerialviews.ui.sources

import android.content.SharedPreferences
import android.os.Bundle
import android.os.Environment
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.preference.CheckBoxPreference
import androidx.preference.EditTextPreference
import androidx.preference.MultiSelectListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import com.hierynomus.mssmb2.SMB2Dialect
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.models.prefs.SambaMediaPrefs
import com.neilturner.aerialviews.providers.SambaMediaProvider
import com.neilturner.aerialviews.utils.DialogHelper
import com.neilturner.aerialviews.utils.FirebaseHelper.logException
import com.neilturner.aerialviews.utils.MenuStateFragment
import com.neilturner.aerialviews.utils.PermissionHelper
import com.neilturner.aerialviews.utils.SambaHelper
import com.neilturner.aerialviews.utils.enumContains
import com.neilturner.aerialviews.utils.setSummaryFromValues
import com.neilturner.aerialviews.utils.toBoolean
import com.neilturner.aerialviews.utils.toStringOrEmpty
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Properties

class SambaVideosFragment :
    MenuStateFragment(),
    SharedPreferences.OnSharedPreferenceChangeListener,
    PreferenceManager.OnPreferenceTreeClickListener {
    private lateinit var requestReadPermission: ActivityResultLauncher<String>
    private lateinit var requestWritePermission: ActivityResultLauncher<String>

    override fun onCreatePreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        setPreferencesFromResource(R.xml.sources_samba_videos, rootKey)
        preferenceManager.sharedPreferences?.registerOnSharedPreferenceChangeListener(this)

        // Import/read permission request
        requestReadPermission =
            registerForActivityResult(
                ActivityResultContracts.RequestPermission(),
            ) { isGranted: Boolean ->
                if (!isGranted) {
                    lifecycleScope.launch {
                        DialogHelper.show(
                            requireContext(),
                            resources.getString(R.string.samba_videos_import_failed),
                            resources.getString(R.string.samba_videos_permission_denied),
                        )
                    }
                } else {
                    lifecycleScope.launch {
                        importSettings()
                    }
                }
            }

        // Export/write permission request
        requestWritePermission =
            registerForActivityResult(
                ActivityResultContracts.RequestPermission(),
            ) { isGranted: Boolean ->
                if (!isGranted) {
                    lifecycleScope.launch {
                        DialogHelper.show(
                            requireContext(),
                            resources.getString(R.string.samba_videos_export_failed),
                            resources.getString(R.string.samba_videos_permission_denied),
                        )
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
            lifecycleScope.launch { testSambaConnection() }
            return true
        }

        if (preference.key.contains("samba_videos_import_export_settings")) {
            importExportSettings()
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
        val dialects = findPreference<MultiSelectListPreference>("samba_videos_smb_dialects")
        dialects?.setSummaryFromValues(dialects.values)

        // Host name
        val hostname = findPreference<EditTextPreference>("samba_videos_hostname")
        if (hostname?.text.toStringOrEmpty().isNotEmpty()) {
            hostname?.summary = hostname.text
        } else {
            hostname?.summary = getString(R.string.samba_videos_hostname_summary)
        }

        // Domain name
        val domainname = findPreference<EditTextPreference>("samba_videos_domainname")
        if (domainname?.text.toStringOrEmpty().isNotEmpty()) {
            domainname?.summary = domainname.text
        } else {
            domainname?.summary = getString(R.string.samba_videos_domainname_summary)
        }

        // Share name
        val sharename = findPreference<EditTextPreference>("samba_videos_sharename")
        if (sharename?.text.toStringOrEmpty().isNotEmpty()) {
            val fixedShareName = SambaHelper.fixShareName(SambaMediaPrefs.shareName)
            SambaMediaPrefs.shareName = fixedShareName
            sharename?.summary = fixedShareName
            sharename?.text = fixedShareName
        } else {
            sharename?.summary = getString(R.string.samba_videos_sharename_summary)
        }

        // Username
        val username = findPreference<EditTextPreference>("samba_videos_username")
        if (username?.text.toStringOrEmpty().isNotEmpty()) {
            username?.summary = username.text
        } else {
            username?.summary = getString(R.string.samba_videos_username_summary)
        }

        // Password
        val password = findPreference<EditTextPreference>("samba_videos_password")
        if (password?.text.toStringOrEmpty().isNotEmpty()) {
            password?.summary = "*".repeat(SambaMediaPrefs.password.length)
        } else {
            password?.summary = getString(R.string.samba_videos_password_summary)
        }

        // Subfolders
        val subfolders = findPreference<CheckBoxPreference>("samba_videos_search_subfolders")
        subfolders?.isChecked = SambaMediaPrefs.searchSubfolders

        // Encryption
        val encryption = findPreference<CheckBoxPreference>("samba_videos_enable_encryption")
        encryption?.isChecked = SambaMediaPrefs.enableEncryption
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
            //setTitle(R.string.samba_videos_import_export_settings_title)
            //setMessage(R.string.samba_videos_import_export_settings_summary)
            setNeutralButton(R.string.button_cancel, null)
            setNegativeButton(R.string.button_import) { _, _ ->
                if (PermissionHelper.hasDocumentReadPermission(requireContext())) {
                    lifecycleScope.launch { importSettings() }
                } else {
                    requestReadPermission.launch(PermissionHelper.getReadDocumentPermission())
                }
            }
            setPositiveButton(R.string.button_export) { _, _ ->
                if (PermissionHelper.hasDocumentWritePermission(requireContext())) {
                    lifecycleScope.launch { exportSettings() }
                } else {
                    requestWritePermission.launch(PermissionHelper.getWriteDocumentPermission())
                }
            }
            create().show()
        }
    }

    private suspend fun importSettings() =
        withContext(Dispatchers.IO) {
            Timber.i("Importing SMB settings from Document folder")

            val directory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath
            val file = File(directory, SMB_SETTINGS_FILENAME)
            val properties = Properties()

            if (!file.exists()) {
                DialogHelper.show(
                    requireContext(),
                    resources.getString(R.string.samba_videos_import_failed),
                    String.format(resources.getString(R.string.samba_videos_file_not_found), SMB_SETTINGS_FILENAME),
                )
                return@withContext
            }

            try {
                val stream = FileInputStream(file)
                stream.use {
                    properties.load(stream)
                }
            } catch (ex: Exception) {
                DialogHelper.show(
                    requireContext(),
                    resources.getString(R.string.samba_videos_import_failed),
                    resources.getString(R.string.samba_videos_error_parsing),
                )
                Timber.e(ex, "Import failed")
                ex.cause?.let { logException(it) }
                return@withContext
            }

            try {
                SambaMediaPrefs.hostName = properties["hostname"] as String
                SambaMediaPrefs.domainName = properties["domainname"] as String
                SambaMediaPrefs.shareName = properties["sharename"] as String
                SambaMediaPrefs.userName = properties["username"] as String
                SambaMediaPrefs.password = properties["password"] as String

                val dialects = properties["smb_dialects"].toStringOrEmpty().split(",")
                val validDialects = dialects.filter { enumContains<SMB2Dialect>(it.trim()) }
                SambaMediaPrefs.smbDialects.clear()
                SambaMediaPrefs.smbDialects.addAll(validDialects)

                SambaMediaPrefs.searchSubfolders = properties["search_subfolders"].toBoolean()
                SambaMediaPrefs.enableEncryption = properties["enable_encryption"].toBoolean()
            } catch (ex: Exception) {
                DialogHelper.show(
                    requireContext(),
                    resources.getString(R.string.samba_videos_import_failed),
                    resources.getString(R.string.samba_videos_unable_to_save),
                )
                Timber.e(ex, "Import failed")
                ex.cause?.let { logException(it) }
                return@withContext
            }

            withContext(Dispatchers.Main) {
                preferenceScreen.findPreference<EditTextPreference>("samba_videos_hostname")?.text = SambaMediaPrefs.hostName
                preferenceScreen.findPreference<EditTextPreference>("samba_videos_domainname")?.text = SambaMediaPrefs.domainName
                preferenceScreen.findPreference<EditTextPreference>("samba_videos_sharename")?.text = SambaMediaPrefs.shareName
                preferenceScreen.findPreference<EditTextPreference>("samba_videos_username")?.text = SambaMediaPrefs.userName
                preferenceScreen.findPreference<EditTextPreference>("samba_videos_password")?.text = SambaMediaPrefs.password

                preferenceScreen.findPreference<CheckBoxPreference>("samba_videos_search_subfolders")?.isChecked
                SambaMediaPrefs.searchSubfolders
                preferenceScreen.findPreference<CheckBoxPreference>("samba_videos_enable_encryption")?.isChecked
                SambaMediaPrefs.enableEncryption

                preferenceScreen.findPreference<MultiSelectListPreference>("samba_videos_smb_dialects")?.values =
                    SambaMediaPrefs.smbDialects.toSet()

                updateSummary()
            }

            DialogHelper.show(
                requireContext(),
                resources.getString(R.string.samba_videos_import_success),
                String.format(resources.getString(R.string.samba_videos_import_save_success), SMB_SETTINGS_FILENAME),
            )
        }

    private suspend fun exportSettings() =
        withContext(Dispatchers.IO) {
            Timber.i("Exporting SMB settings to Documents folder")

            // Build SMB config list
            val smbSettings = Properties()
            smbSettings["hostname"] = SambaMediaPrefs.hostName
            smbSettings["domainname"] = SambaMediaPrefs.domainName
            smbSettings["sharename"] = SambaMediaPrefs.shareName
            smbSettings["username"] = SambaMediaPrefs.userName
            smbSettings["password"] = SambaMediaPrefs.password
            smbSettings["smb_dialects"] = SambaMediaPrefs.smbDialects.joinToString(",").replace(" ", "")
            smbSettings["search_subfolders"] = SambaMediaPrefs.searchSubfolders.toString()
            smbSettings["enable_encryption"] = SambaMediaPrefs.enableEncryption.toString()

            val directory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath
            val file = File(directory, SMB_SETTINGS_FILENAME)

            try {
                val stream = FileOutputStream(file, false)
                stream.use {
                    smbSettings.store(stream, "Aerial Views SMB Settings")
                }
            } catch (ex: Exception) {
                DialogHelper.show(
                    requireContext(),
                    resources.getString(R.string.samba_videos_export_failed),
                    String.format(resources.getString(R.string.samba_videos_unable_to_write), SMB_SETTINGS_FILENAME),
                )
                Timber.e(ex, "Export failed")
                ex.cause?.let { logException(it) }
                return@withContext
            }

            DialogHelper.show(
                requireContext(),
                resources.getString(R.string.samba_videos_export_success),
                String.format(resources.getString(R.string.samba_videos_export_write_success), SMB_SETTINGS_FILENAME),
            )
        }

    private suspend fun testSambaConnection() =
        withContext(Dispatchers.IO) {
            val provider = SambaMediaProvider(requireContext(), SambaMediaPrefs)
            val result = provider.fetchTest()
            ensureActive() // Quick fix for provider methods not cancelling when coroutine is cancelled, etc
            DialogHelper.show(requireContext(), resources.getString(R.string.samba_videos_test_results), result)
        }

    companion object {
        private const val SMB_SETTINGS_FILENAME = "aerial-views-smb-settings.txt"
    }
}
