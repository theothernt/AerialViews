@file:Suppress("unused")

package com.neilturner.aerialviews.ui.settings

import android.Manifest
import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreference
import com.google.modernstorage.permissions.StoragePermissions
import com.google.modernstorage.permissions.StoragePermissions.Action
import com.google.modernstorage.permissions.StoragePermissions.FileType
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.models.prefs.LocalVideoPrefs

class AnyVideosFragment :
    PreferenceFragmentCompat(),
    SharedPreferences.OnSharedPreferenceChangeListener {
    private var storagePermissions: StoragePermissions? = null
    private var requestPermission: ActivityResultLauncher<String>? = null

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings_any_videos, rootKey)
        preferenceManager.sharedPreferences?.registerOnSharedPreferenceChangeListener(this)

        storagePermissions = StoragePermissions(requireContext())
        requestPermission = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (!isGranted) {
                resetPreference()
            }
        }
    }

    override fun onDestroy() {
        preferenceManager.sharedPreferences?.unregisterOnSharedPreferenceChangeListener(this)
        super.onDestroy()
    }

    override fun onResume() {
        val canReadVideos = storagePermissions?.hasAccess(
            action = Action.READ,
            types = listOf(FileType.Video),
            createdBy = StoragePermissions.CreatedBy.AllApps
        )!!

        if (!canReadVideos &&
            requiresPermission()
        ) {
            resetPreference()
        }
        super.onResume()
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String) {
        if (key == "local_videos_enabled" &&
            requiresPermission()
        ) {

            val canReadVideos = storagePermissions?.hasAccess(
                action = Action.READ,
                types = listOf(FileType.Video),
                createdBy = StoragePermissions.CreatedBy.AllApps
            )!!

            if (!canReadVideos) {
                requestPermission?.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
    }

    private fun requiresPermission(): Boolean {
        return LocalVideoPrefs.enabled
    }

    private fun resetPreference() {
        val pref = findPreference<SwitchPreference>("local_videos_enabled")
        pref?.isChecked = false
    }
}
