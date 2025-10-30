package com.neilturner.aerialviews.ui.settings

import android.content.SharedPreferences
import android.os.Bundle
import androidx.preference.Preference
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.utils.MenuStateFragment

class ProjectivyFragment :
    MenuStateFragment(),
    SharedPreferences.OnSharedPreferenceChangeListener {

    override fun onCreatePreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        setPreferencesFromResource(R.xml.settings_projectivy, rootKey)
        preferenceManager.sharedPreferences?.registerOnSharedPreferenceChangeListener(this)
        updateVideoOptionsVisibility()
    }

    override fun onDestroy() {
        preferenceManager.sharedPreferences?.unregisterOnSharedPreferenceChangeListener(this)
        super.onDestroy()
    }

    override fun onSharedPreferenceChanged(
        sharedPreferences: SharedPreferences,
        key: String?,
    ) {
        if (key == "projectivy_select_videos") {
            updateVideoOptionsVisibility()
        }
    }

    private fun updateVideoOptionsVisibility() {
        val selectedVideos = preferenceManager.sharedPreferences
            ?.getStringSet("projectivy_select_videos", emptySet()) ?: emptySet()

        // Update visibility for each video provider option
        findPreference<Preference>("projectivy_apple_videos_options")?.isVisible =
            selectedVideos.contains("APPLE")
        findPreference<Preference>("projectivy_amazon_videos_options")?.isVisible =
            selectedVideos.contains("AMAZON")
        findPreference<Preference>("projectivy_comm1_videos_options")?.isVisible =
            selectedVideos.contains("COMM1")
        findPreference<Preference>("projectivy_comm2_videos_options")?.isVisible =
            selectedVideos.contains("COMM2")
    }
}
