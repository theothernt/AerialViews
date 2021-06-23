package com.codingbuffalo.aerialdream.ui.settings

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.preference.Preference
import com.codingbuffalo.aerialdream.R
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import androidx.preference.SwitchPreference
import com.codingbuffalo.aerialdream.models.prefs.LocalVideoPrefs
import com.codingbuffalo.aerialdream.models.prefs.NetworkVideoPrefs
import com.codingbuffalo.aerialdream.providers.NetworkVideoProvider
import com.codingbuffalo.aerialdream.utils.PermissionHelper

class NetworkVideosFragment :
    PreferenceFragmentCompat(),
    PreferenceManager.OnPreferenceTreeClickListener {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings_network_videos, rootKey)
    }

    override fun onPreferenceTreeClick(preference: Preference): Boolean {

        if (preference.key == null || !preference.key.contains("network_videos_test_connection"))
            return super.onPreferenceTreeClick(preference)

        testNetworkConnection()
        return true
    }

    private fun testNetworkConnection() {

        showMessage("Connecting...")
        val provider = NetworkVideoProvider(requireContext(), NetworkVideoPrefs)
        if (!provider.testConnection()) {
            showMessage("Failed to connect.")
            return
        }

        showMessage("Connected.")
        val videos = provider.fetchVideos()
        showMessage("Found ${videos.size} video file(s).")
    }

    private fun showMessage(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
    }

}