package com.neilturner.aerialviews.ui.settings

import android.Manifest
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.preference.ListPreference
import androidx.preference.PreferenceFragmentCompat
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.models.AppleVideoLocation
import com.neilturner.aerialviews.models.prefs.AppleVideoPrefs
import com.neilturner.aerialviews.utils.PermissionHelper

class AppleVideosFragment :
    PreferenceFragmentCompat(),
    SharedPreferences.OnSharedPreferenceChangeListener {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings_apple_videos, rootKey)
        preferenceManager.sharedPreferences.registerOnSharedPreferenceChangeListener(this)
        updateSummaries()
    }

    override fun onDestroy() {
        preferenceManager.sharedPreferences.unregisterOnSharedPreferenceChangeListener(this)
        super.onDestroy()
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String) {
        updateSummaries()
    }

    private fun updateSummaries() {
        val quality = findPreference<ListPreference>("apple_videos_quality") as ListPreference
        val qualityTitle = context?.getString(R.string.apple_videos_quality_title)
        quality.title = "$qualityTitle - ${quality.entry}"
    }
}