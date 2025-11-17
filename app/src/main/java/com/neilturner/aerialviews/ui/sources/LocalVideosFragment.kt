package com.neilturner.aerialviews.ui.sources

import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import androidx.preference.SwitchPreference
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.models.enums.SearchType
import com.neilturner.aerialviews.models.prefs.LocalMediaPrefs
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

class LocalVideosFragment :
    MenuStateFragment(),
    PreferenceManager.OnPreferenceTreeClickListener,
    SharedPreferences.OnSharedPreferenceChangeListener {
    private lateinit var requestMultiplePermissions: ActivityResultLauncher<Array<String>>

    override fun onCreatePreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        setPreferencesFromResource(R.xml.sources_local_videos, rootKey)
        preferenceManager.sharedPreferences?.registerOnSharedPreferenceChangeListener(this)

        requestMultiplePermissions =
            registerForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions(),
            ) { permissions ->
                if (!PermissionHelper.isReadMediaPermissionGranted(permissions)) {
                    disableLocalMediaPreference()
                }
            }

        lifecycleScope.launch {
            limitTextInput()
            showNvidiaShieldNoticeIfNeeded()
            updateEnabledOptions()
            updateVolumeAndFolderSummary()
            findVolumeList()
        }
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
            lifecycleScope.launch { testLocalVideosFilter() }
            return true
        }

        return super.onPreferenceTreeClick(preference)
    }

    override fun onSharedPreferenceChanged(
        sharedPreferences: SharedPreferences,
        key: String?,
    ) {
        if (key == "local_videos_enabled" &&
            LocalMediaPrefs.enabled
        ) {
            checkForMediaPermission()
        }

        if (key == "local_videos_legacy_volume" ||
            key == "local_videos_legacy_folder"
        ) {
            LocalMediaPrefs.legacyFolder = FileHelper.fixLegacyFolder(LocalMediaPrefs.legacyFolder)
            val volume = preferenceScreen.findPreference<ListPreference>("local_videos_legacy_volume")
            LocalMediaPrefs.legacyVolumeLabel = volume?.entry.toStringOrEmpty()
            updateVolumeAndFolderSummary()
        }

        updateEnabledOptions()
    }

    private fun updateEnabledOptions() {
        if (LocalMediaPrefs.enabled) {
            enabledOptions(LocalMediaPrefs.searchType)
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
        preferenceScreen
            .findPreference<EditTextPreference>("local_videos_media_store_filter_folder")
            ?.setOnBindEditTextListener { it.setSingleLine() }

        preferenceScreen
            .findPreference<EditTextPreference>("local_videos_legacy_folder")
            ?.setOnBindEditTextListener { it.setSingleLine() }
    }

    private suspend fun testLocalVideosFilter() =
        withContext(Dispatchers.IO) {
            val provider = LocalMediaProvider(requireContext(), LocalMediaPrefs)
            val result = provider.fetchTest()
            ensureActive()
            DialogHelper.showOnMain(requireContext(), resources.getString(R.string.local_videos_test_results), result)
        }

    private fun checkForMediaPermission() {
        if (PermissionHelper.hasMediaReadPermission(requireContext())) {
            // If we already have permission, exit
            return
        }

        requestMultiplePermissions.launch(PermissionHelper.getReadMediaPermissions())
    }

    private fun disableLocalMediaPreference() {
        val pref = findPreference<SwitchPreference>("local_videos_enabled")
        pref?.isChecked = false
    }

    private fun updateVolumeAndFolderSummary() {
        val volume = preferenceScreen.findPreference<ListPreference>("local_videos_legacy_volume")
        val folder = preferenceScreen.findPreference<EditTextPreference>("local_videos_legacy_folder")

        if (LocalMediaPrefs.legacyVolume.isEmpty()) {
            volume?.summary = resources.getString(R.string.local_videos_legacy_volume_summary)
        } else {
            volume?.summary = LocalMediaPrefs.legacyVolumeLabel
        }

        if (LocalMediaPrefs.legacyFolder.isEmpty()) {
            folder?.summary = resources.getString(R.string.local_videos_legacy_folder_summary)
        } else {
            folder?.summary = LocalMediaPrefs.legacyFolder
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

    private fun showNvidiaShieldNoticeIfNeeded() {
        if (!DeviceHelper.isNvidiaShield()) {
            return
        }
        val notice = findPreference<Preference>("local_videos_shield_notice")
        notice?.isVisible = true
    }
}
