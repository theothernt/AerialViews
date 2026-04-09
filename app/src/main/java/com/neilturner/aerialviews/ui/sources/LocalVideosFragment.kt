package com.neilturner.aerialviews.ui.sources

import android.Manifest
import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.MultiSelectListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import androidx.preference.SwitchPreference
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.models.enums.SearchType
import com.neilturner.aerialviews.models.prefs.LocalMediaPrefs
import com.neilturner.aerialviews.models.prefs.MediaSelection
import com.neilturner.aerialviews.providers.LocalMediaProvider
import com.neilturner.aerialviews.utils.DeviceHelper
import com.neilturner.aerialviews.utils.DialogHelper
import com.neilturner.aerialviews.utils.FileHelper
import com.neilturner.aerialviews.utils.MenuStateFragment
import com.neilturner.aerialviews.utils.PermissionHelper
import com.neilturner.aerialviews.utils.StorageHelper
import com.neilturner.aerialviews.utils.setSummaryFromValues
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
    private lateinit var requestAudioPermission: ActivityResultLauncher<String>

    // Track previous selection to detect when music is added
    private var previousMediaSelection: Set<String> = LocalMediaPrefs.mediaSelection

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
                // Only disable if photo+video permission is missing
                if (!PermissionHelper.isVideoImagePermissionGranted(permissions)) {
                    disableLocalMediaPreference()
                }
                // Audio is optional — if it's missing, music simply won't play
            }

        requestAudioPermission =
            registerForActivityResult(
                ActivityResultContracts.RequestPermission(),
            ) { granted ->
                // No action needed — if denied, music just won't play
                if (!granted) {
                    // Revert music from selection since user denied permission
                    removeMusicFromSelection()
                }
            }

        lifecycleScope.launch {
            limitTextInput()
            showNvidiaShieldNoticeIfNeeded()
            updateEnabledOptions()
            updateMediaSelectionSummary()
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

        // Detect when music is added to the selection and request audio permission
        if (key == "local_media_selection") {
            val current = LocalMediaPrefs.mediaSelection
            val addedMusic = MediaSelection.MUSIC in current && MediaSelection.MUSIC !in previousMediaSelection
            if (addedMusic && !PermissionHelper.hasAudioReadPermission(requireContext())) {
                requestAudioPermissionForMusic()
            }
            previousMediaSelection = current
        }

        updateEnabledOptions()
        updateMediaSelectionSummary()
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

    private fun updateMediaSelectionSummary() {
        preferenceScreen
            .findPreference<MultiSelectListPreference>("local_media_selection")
            ?.setSummaryFromValues(LocalMediaPrefs.mediaSelection)
    }

    private suspend fun testLocalVideosFilter() =
        withContext(Dispatchers.IO) {
            val provider = LocalMediaProvider(requireContext(), LocalMediaPrefs)
            val result = provider.fetchTest()
            ensureActive()
            DialogHelper.showOnMain(requireContext(), resources.getString(R.string.local_videos_test_results), result)
        }

    private fun checkForMediaPermission() {
        if (PermissionHelper.hasVideoImagePermission(requireContext())) {
            // If we already have photo+video permission, local media stays enabled.
            // Audio is optional — music won't play without it but that's fine.
            return
        }

        requestMultiplePermissions.launch(PermissionHelper.getReadMediaPermissions())
    }

    private fun requestAudioPermissionForMusic() {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) {
            // Pre-TIRAMISU: READ_EXTERNAL_STORAGE already covers audio
            return
        }
        requestAudioPermission.launch(Manifest.permission.READ_MEDIA_AUDIO)
    }

    // Kotpref's stringSetPref is read-only (val), so we modify SharedPreferences directly.
    private fun removeMusicFromSelection() {
        val current = LocalMediaPrefs.mediaSelection
        if (MediaSelection.MUSIC in current) {
            val updated = (current - MediaSelection.MUSIC).toMutableSet()
            LocalMediaPrefs.preferences.edit().putStringSet("local_media_selection", updated).apply()
            updateMediaSelectionSummary()
        }
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
