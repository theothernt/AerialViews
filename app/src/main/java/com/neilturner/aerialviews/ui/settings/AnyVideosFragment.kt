package com.neilturner.aerialviews.ui.settings

import android.Manifest
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import com.neilturner.aerialviews.R
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreference
import com.neilturner.aerialviews.models.prefs.LocalVideoPrefs
import com.neilturner.aerialviews.utils.PermissionHelper

class AnyVideosFragment :
    PreferenceFragmentCompat(),
    SharedPreferences.OnSharedPreferenceChangeListener {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings_any_videos, rootKey)
        preferenceManager.sharedPreferences.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onDestroy() {
        preferenceManager.sharedPreferences.unregisterOnSharedPreferenceChangeListener(this)
        super.onDestroy()
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String) {
        if (key == "local_videos_enabled" &&
            requiresPermission() &&
            !PermissionHelper.hasStoragePermission(requireContext())) {
            requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), 1)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        when (requestCode) {
            1 -> if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_DENIED) {
                resetPreference()
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