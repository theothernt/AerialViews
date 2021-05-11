package com.codingbuffalo.aerialdream.ui.settings

import android.Manifest
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.preference.ListPreference
import androidx.preference.PreferenceFragmentCompat
import com.codingbuffalo.aerialdream.R
import com.codingbuffalo.aerialdream.models.AppleVideoLocation
import com.codingbuffalo.aerialdream.models.prefs.AppleVideoPrefs
import com.codingbuffalo.aerialdream.utils.PermissionHelper

class AppleVideosFragment :
    PreferenceFragmentCompat(),
    SharedPreferences.OnSharedPreferenceChangeListener {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings_apple_videos, rootKey)
        preferenceManager.sharedPreferences.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String) {
        if (key == "apple_videos_location" &&
            requiresPermission() &&
            !PermissionHelper.hasStoragePermission(requireContext())) {
            requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), 1)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        when (requestCode) {
            1 -> if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_DENIED) {
                resetPreference()
            } else {
                // Update summary
            }
        }
    }

    private fun requiresPermission(): Boolean {
        return AppleVideoPrefs.location != AppleVideoLocation.REMOTE
    }

    private fun resetPreference() {
        val pref = findPreference<ListPreference>("apple_videos_location")
        pref?.value = getString(R.string.apple_videos_location_default)
    }
}