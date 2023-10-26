package com.neilturner.aerialviews.ui.sources

import android.Manifest
import android.content.SharedPreferences
import android.content.res.Resources
import android.os.Build
import android.os.Bundle
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import androidx.preference.SwitchPreference
import com.google.modernstorage.permissions.StoragePermissions
import com.google.modernstorage.permissions.StoragePermissions.Action
import com.google.modernstorage.permissions.StoragePermissions.FileType
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.models.enums.SearchType
import com.neilturner.aerialviews.models.prefs.LocalVideoPrefs
import com.neilturner.aerialviews.providers.LocalVideoProvider
import com.neilturner.aerialviews.utils.DeviceHelper
import com.neilturner.aerialviews.utils.FileHelper
import com.neilturner.aerialviews.utils.StorageHelper
import com.neilturner.aerialviews.utils.toStringOrEmpty
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Suppress("unused")
class LocalVideosFragment :
    PreferenceFragmentCompat(),
    PreferenceManager.OnPreferenceTreeClickListener,
    SharedPreferences.OnSharedPreferenceChangeListener {
    private lateinit var storagePermissions: StoragePermissions
    private lateinit var requestPermission: ActivityResultLauncher<String>
    private lateinit var resources: Resources

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.sources_local_videos, rootKey)
        preferenceManager.sharedPreferences?.registerOnSharedPreferenceChangeListener(this)

        resources = context?.resources!!
        storagePermissions = StoragePermissions(requireContext())
        requestPermission = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (!isGranted) {
                resetPreference()
            }
        }

        limitTextInput()
        showNoticeIfNeeded()

        updateEnabledOptions()
        updateVolumeAndFolderSummary()
        findVolumeList()
    }

    override fun onDestroy() {
        preferenceManager.sharedPreferences?.unregisterOnSharedPreferenceChangeListener(this)
        super.onDestroy()
    }

    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        if (preference.key.isNullOrEmpty()) {
            return super.onPreferenceTreeClick(preference)
        }

        if (preference.key.contains("local_videos_search_test")) {
            lifecycleScope.launch {
                testLocalVideosFilter()
            }
            return true
        }

        return super.onPreferenceTreeClick(preference)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String) {
        if (key == "local_videos_enabled" &&
            requiresPermission()
        ) {
            val canReadVideos = storagePermissions.hasAccess(
                action = Action.READ,
                types = listOf(FileType.Video),
                createdBy = StoragePermissions.CreatedBy.AllApps
            )

            if (!canReadVideos) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    requestPermission.launch(Manifest.permission.READ_MEDIA_VIDEO)
                } else {
                    requestPermission.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                }
            }
        }

        if (key == "local_videos_legacy_volume" ||
            key == "local_videos_legacy_folder"
        ) {
            LocalVideoPrefs.legacy_folder = FileHelper.fixLegacyFolder(LocalVideoPrefs.legacy_folder)

            val volume = preferenceScreen.findPreference<ListPreference>("local_videos_legacy_volume")
            LocalVideoPrefs.legacy_volume_label = volume?.entry.toStringOrEmpty()

            updateVolumeAndFolderSummary()
        }

        updateEnabledOptions()
    }

    private fun updateEnabledOptions() {
        if (LocalVideoPrefs.enabled) {
            enabledOptions(LocalVideoPrefs.searchType)
        } else {
            enabledOptions(null)
        }
    }

    private fun enabledOptions(type: SearchType? = null) {
        val mediaStoreOptions = preferenceScreen.findPreference<Preference>("local_videos_media_store_notice")
        val legacyOptions = preferenceScreen.findPreference<Preference>("local_videos_legacy_notice")

        when (type) {
            SearchType.MEDIA_STORE -> {
                mediaStoreOptions?.isEnabled = true
                legacyOptions?.isEnabled = false
            }
            SearchType.FOLDER_ACCESS -> {
                mediaStoreOptions?.isEnabled = false
                legacyOptions?.isEnabled = true
            }
            else -> {
                mediaStoreOptions?.isEnabled = false
                legacyOptions?.isEnabled = false
            }
        }
    }

    private fun limitTextInput() {
        preferenceScreen.findPreference<EditTextPreference>("local_videos_media_store_filter_folder")?.setOnBindEditTextListener { it.setSingleLine() }
        preferenceScreen.findPreference<EditTextPreference>("local_videos_legacy_folder")?.setOnBindEditTextListener { it.setSingleLine() }
    }

    private suspend fun testLocalVideosFilter() {
        val provider = LocalVideoProvider(requireContext(), LocalVideoPrefs)
        val result = provider.fetchTest()
        showDialog(resources.getString(R.string.local_videos_test_results), result)
    }

    private fun requiresPermission(): Boolean {
        return LocalVideoPrefs.enabled
    }

    private fun resetPreference() {
        val pref = findPreference<SwitchPreference>("local_videos_enabled")
        pref?.isChecked = false
    }

    @Suppress("SameParameterValue")
    private suspend fun showDialog(title: String, message: String) = withContext(Dispatchers.Main) {
        AlertDialog.Builder(requireContext()).apply {
            setTitle(title)
            setMessage(message)
            setPositiveButton(R.string.button_ok, null)
            create().show()
        }
    }

    private fun updateVolumeAndFolderSummary() {
        val volume = preferenceScreen.findPreference<ListPreference>("local_videos_legacy_volume")
        val folder = preferenceScreen.findPreference<EditTextPreference>("local_videos_legacy_folder")

        if (LocalVideoPrefs.legacy_volume.isEmpty()) {
            volume?.summary = resources.getString(R.string.local_videos_legacy_volume_summary)
        } else {
            volume?.summary = LocalVideoPrefs.legacy_volume_label
        }

        if (LocalVideoPrefs.legacy_folder.isEmpty()) {
            folder?.summary = resources.getString(R.string.local_videos_legacy_folder_summary)
        } else {
            folder?.summary = LocalVideoPrefs.legacy_folder
        }
    }

    private fun findVolumeList() {
        val listPref = preferenceScreen.findPreference<ListPreference>("local_videos_legacy_volume")

        val vols = StorageHelper.getStoragePaths(requireContext())
        val entries = vols.map { it.value }.toMutableList()
        val values = vols.map { it.key }.toMutableList()

        entries.add(resources.getString(R.string.local_videos_legacy_all_entry))
        values.add("/all") // values should not be translated!

        listPref?.entries = entries.toTypedArray()
        listPref?.entryValues = values.toTypedArray()
    }

    private fun showNoticeIfNeeded() {
        if (!DeviceHelper.isNvidiaShield()) {
            return
        }
        val notice = findPreference<Preference>("local_videos_shield_notice")
        notice?.isVisible = true
    }

    companion object {
        private const val TAG = "LocalVideosFragment"
    }
}
