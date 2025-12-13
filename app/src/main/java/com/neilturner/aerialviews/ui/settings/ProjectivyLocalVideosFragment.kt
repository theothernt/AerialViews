package com.neilturner.aerialviews.ui.settings

import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.models.enums.SearchType
import com.neilturner.aerialviews.models.prefs.ProjectivyLocalMediaPrefs
import com.neilturner.aerialviews.providers.LocalMediaProvider
import com.neilturner.aerialviews.utils.DeviceHelper
import com.neilturner.aerialviews.utils.DialogHelper
import com.neilturner.aerialviews.utils.FileHelper
import com.neilturner.aerialviews.utils.MenuStateFragment
import com.neilturner.aerialviews.utils.PermissionHelper
import com.neilturner.aerialviews.utils.StorageHelper
import com.neilturner.aerialviews.utils.toStringOrEmpty
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ProjectivyLocalVideosFragment :
    MenuStateFragment(),
    PreferenceManager.OnPreferenceTreeClickListener,
    SharedPreferences.OnSharedPreferenceChangeListener {
    private lateinit var requestMultiplePermissions: ActivityResultLauncher<Array<String>>

    override fun onCreatePreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        setPreferencesFromResource(R.xml.settings_projectivy_local_videos, rootKey)
        preferenceManager.sharedPreferences?.registerOnSharedPreferenceChangeListener(this)

        requestMultiplePermissions =
            registerForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions(),
            ) { permissions ->
                // If permission isn’t granted, MediaStore scans will return no items.
                // Keep the UI as-is; user can switch to Folder access instead.
                PermissionHelper.isReadMediaPermissionGranted(permissions)
            }

        lifecycleScope.launch {
            limitTextInput()
            showNvidiaShieldNoticeIfNeeded()
            updateEnabledOptions()
            updateVolumeAndFolderSummary()
            findVolumeList()
        }

        checkForMediaPermissionIfNeeded(ProjectivyLocalMediaPrefs.searchType)
    }

    override fun onDestroy() {
        preferenceManager.sharedPreferences?.unregisterOnSharedPreferenceChangeListener(this)
        super.onDestroy()
    }

    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        if (preference.key.isNullOrEmpty()) {
            return super.onPreferenceTreeClick(preference)
        }

        if (preference.key.contains("projectivy_local_videos_search_test")) {
            lifecycleScope.launch { testLocalVideosFilter() }
            return true
        }

        return super.onPreferenceTreeClick(preference)
    }

    override fun onSharedPreferenceChanged(
        sharedPreferences: SharedPreferences,
        key: String?,
    ) {
        if (key == "projectivy_local_videos_search_type") {
            checkForMediaPermissionIfNeeded(ProjectivyLocalMediaPrefs.searchType)
        }

        if (key == "projectivy_local_videos_legacy_volume" ||
            key == "projectivy_local_videos_legacy_folder"
        ) {
            ProjectivyLocalMediaPrefs.legacyFolder = FileHelper.fixLegacyFolder(ProjectivyLocalMediaPrefs.legacyFolder)
            val volume = preferenceScreen.findPreference<ListPreference>("projectivy_local_videos_legacy_volume")
            ProjectivyLocalMediaPrefs.legacyVolumeLabel = volume?.entry.toStringOrEmpty()
            updateVolumeAndFolderSummary()
        }

        updateEnabledOptions()
    }

    private fun updateEnabledOptions() {
        enabledOptions(ProjectivyLocalMediaPrefs.searchType)
    }

    private fun enabledOptions(type: SearchType? = null) {
        val mediaStoreOptions = preferenceScreen.findPreference<Preference>("projectivy_local_videos_media_store_notice")
        val legacyOptions = preferenceScreen.findPreference<Preference>("projectivy_local_videos_legacy_notice")

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
        preferenceScreen
            .findPreference<EditTextPreference>("projectivy_local_videos_media_store_filter_folder")
            ?.setOnBindEditTextListener { it.setSingleLine() }

        preferenceScreen
            .findPreference<EditTextPreference>("projectivy_local_videos_legacy_folder")
            ?.setOnBindEditTextListener { it.setSingleLine() }
    }

    private suspend fun testLocalVideosFilter() =
        withContext(Dispatchers.IO) {
            val provider = LocalMediaProvider(requireContext(), ProjectivyLocalMediaPrefs)
            val result = provider.fetchTest()
            ensureActive()
            DialogHelper.showOnMain(requireContext(), resources.getString(R.string.local_videos_test_results), result)
        }

    private fun checkForMediaPermissionIfNeeded(type: SearchType?) {
        if (type != SearchType.MEDIA_STORE) {
            return
        }

        if (PermissionHelper.hasMediaReadPermission(requireContext())) {
            return
        }

        requestMultiplePermissions.launch(PermissionHelper.getReadMediaPermissions())
    }

    private fun updateVolumeAndFolderSummary() {
        val volume = preferenceScreen.findPreference<ListPreference>("projectivy_local_videos_legacy_volume")
        val folder = preferenceScreen.findPreference<EditTextPreference>("projectivy_local_videos_legacy_folder")

        if (ProjectivyLocalMediaPrefs.legacyVolume.isEmpty()) {
            volume?.summary = resources.getString(R.string.local_videos_legacy_volume_summary)
        } else {
            volume?.summary = ProjectivyLocalMediaPrefs.legacyVolumeLabel
        }

        if (ProjectivyLocalMediaPrefs.legacyFolder.isEmpty()) {
            folder?.summary = resources.getString(R.string.local_videos_legacy_folder_summary)
        } else {
            folder?.summary = ProjectivyLocalMediaPrefs.legacyFolder
        }
    }

    private fun findVolumeList() {
        val listPref = preferenceScreen.findPreference<ListPreference>("projectivy_local_videos_legacy_volume")

        val vols = StorageHelper.getStoragePaths(requireContext())
        val entries = vols.map { it.value }.toMutableList()
        val values = vols.map { it.key }.toMutableList()

        entries.add(resources.getString(R.string.local_videos_legacy_all_entry))
        values.add("/all")

        listPref?.entries = entries.toTypedArray()
        listPref?.entryValues = values.toTypedArray()
    }

    private fun showNvidiaShieldNoticeIfNeeded() {
        if (!DeviceHelper.isNvidiaShield()) {
            return
        }
        val notice = findPreference<Preference>("projectivy_local_videos_shield_notice")
        notice?.isVisible = true
    }
}
