package com.codingbuffalo.aerialdream.ui.settings

import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Toast
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import com.codingbuffalo.aerialdream.R
import com.codingbuffalo.aerialdream.models.prefs.NetworkVideoPrefs
import com.codingbuffalo.aerialdream.models.videos.AerialVideo
import com.codingbuffalo.aerialdream.providers.NetworkVideoProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

class NetworkVideosFragment :
    PreferenceFragmentCompat(),
    PreferenceManager.OnPreferenceTreeClickListener,
    SharedPreferences.OnSharedPreferenceChangeListener {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings_network_videos, rootKey)
        preferenceManager.sharedPreferences.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onPreferenceTreeClick(preference: Preference): Boolean {

        if (preference.key == null || !preference.key.contains("network_videos_test_connection"))
            return super.onPreferenceTreeClick(preference)

        testNetworkConnection()
        return true
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String) {
        if (key == "network_videos_sharename") {
            val shareName = NetworkVideoPrefs.shareName

            if (shareName.first() != '/')
                NetworkVideoPrefs.shareName = "/$shareName"

            if (shareName.last() == '/')
                NetworkVideoPrefs.shareName = shareName.dropLast(1)
        }
    }

    private fun testNetworkConnection() {

        showMessage("Connecting...")
        val provider = NetworkVideoProvider(this.requireContext(), NetworkVideoPrefs)

        try {
            var videos: List<AerialVideo>
            runBlocking {
                withContext(Dispatchers.IO) {
                    videos = provider.fetchVideos()
                }
            }
            showMessage("Connected, found ${videos.size} video file(s)")
        } catch (e: Exception) {
            showMessage("Failed to connect")
        }
    }

    private fun showMessage(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
    }
}