@file:Suppress("unused")

package com.neilturner.aerialviews.ui.sources

import android.Manifest
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
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
import com.neilturner.aerialviews.models.prefs.LocalVideoPrefs
import com.neilturner.aerialviews.models.videos.AerialVideo
import com.neilturner.aerialviews.utils.DeviceHelper
import com.neilturner.aerialviews.utils.FileHelper
import com.neilturner.aerialviews.utils.StorageHelper
import com.neilturner.aerialviews.utils.toStringOrEmpty
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LocalVideosFragment :
    PreferenceFragmentCompat(),
    PreferenceManager.OnPreferenceTreeClickListener,
    SharedPreferences.OnSharedPreferenceChangeListener {
    private lateinit var storagePermissions: StoragePermissions
    private lateinit var requestPermission: ActivityResultLauncher<String>

    @RequiresApi(Build.VERSION_CODES.N)
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.sources_local_videos, rootKey)
        preferenceManager.sharedPreferences?.registerOnSharedPreferenceChangeListener(this)

        storagePermissions = StoragePermissions(requireContext())
        requestPermission = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (!isGranted) {
                resetPreference()
            }
        }

        limitTextInput()
        findVolumeList()
        showNoticeIfNeeded()
    }

    override fun onDestroy() {
        preferenceManager.sharedPreferences?.unregisterOnSharedPreferenceChangeListener(this)
        super.onDestroy()
    }

    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        if (preference.key.isNullOrEmpty()) {
            return super.onPreferenceTreeClick(preference)
        }

        if (preference.key.contains("local_videos_test_filter")) {
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

        if (key == "local_videos_legacy_volume") {
            val listPref = preferenceScreen.findPreference<ListPreference>("local_videos_legacy_volume")
            Log.i(TAG, "Value: ${listPref?.entry}")
        }
    }

    private fun limitTextInput() {
        val textPref = preferenceScreen.findPreference<EditTextPreference>("local_videos_filter_folder_name")
        textPref?.setOnBindEditTextListener { it.setSingleLine() }
    }

    private suspend fun testLocalVideosFilter() {
        if (LocalVideoPrefs.filter_folder_name.isEmpty() &&
            LocalVideoPrefs.filter_enabled
        ) {
            showDialog("Error", "No folder has been specified.")
            return
        }

        val videos = mutableListOf<AerialVideo>()
        val localVideos = FileHelper.findAllMedia(requireContext())
        var excluded = 0
        var filtered = 0

        for (video in localVideos) {
            val uri = Uri.parse(video)
            val filename = uri.lastPathSegment.toStringOrEmpty()

            if (!FileHelper.isVideoFilename(filename)) {
                // Log.i(TAG, "Probably not a video: $filename")
                excluded++
                continue
            }

            if (LocalVideoPrefs.filter_enabled && FileHelper.shouldFilter(uri, LocalVideoPrefs.filter_folder_name)) {
                // Log.i(TAG, "Filtering out video: $filename")
                filtered++
                continue
            }

            videos.add(AerialVideo(uri, ""))
        }

        var message = "Videos found by Media Scanner: ${localVideos.size}\n"
        message += "Videos with supported file extensions: ${localVideos.size - excluded}\n"
        message += if (LocalVideoPrefs.filter_enabled) {
            "Videos removed by filter: $filtered\n"
        } else {
            "Videos removed by filter: (disabled)\n"
        }

        message += "Videos selected for playback: ${localVideos.size - (filtered + excluded)}"
        showDialog("Results", message)
    }

    private fun requiresPermission(): Boolean {
        return LocalVideoPrefs.enabled
    }

    private fun resetPreference() {
        val pref = findPreference<SwitchPreference>("local_videos_enabled")
        pref?.isChecked = false
    }

    private suspend fun showDialog(title: String = "", message: String) = withContext(Dispatchers.Main) {
        AlertDialog.Builder(requireContext()).apply {
            setTitle(title)
            setMessage(message)
            setPositiveButton(R.string.button_ok, null)
            create().show()
        }
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private fun findVolumeList() {
        val listPref = preferenceScreen.findPreference<ListPreference>("local_videos_legacy_volume")

        val vols = StorageHelper.getStoragePaths(requireContext())
        val entries = vols.map { it.value }.toTypedArray()
        val values = vols.map { it.key }.toTypedArray()

        listPref?.entries = entries
        listPref?.entryValues = values
        listPref?.setDefaultValue(listPref.entries.first())
    }

    private fun showNoticeIfNeeded() {
        if (!DeviceHelper.isNvidiaShield()) {
            return
        }
        val notice = findPreference<Preference>("local_videos_notice")
        notice?.isVisible = true
    }

    companion object {
        private const val TAG = "LocalVideosFragment"
    }
}
