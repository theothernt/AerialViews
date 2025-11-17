package com.neilturner.aerialviews.ui.settings

import android.content.SharedPreferences
import android.os.Bundle
import androidx.preference.Preference
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.models.prefs.ProjectivyPrefs
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
        if (key == "projectivy_shared_providers") {
            updateVideoOptionsVisibility()
        }
    }

    private fun updateVideoOptionsVisibility() {
        val selectedProviders = ProjectivyPrefs.sharedProviders

        findPreference<Preference>("projectivy_apple_videos_options")?.isVisible =
            selectedProviders.contains("APPLE")
        findPreference<Preference>("projectivy_amazon_videos_options")?.isVisible =
            selectedProviders.contains("AMAZON")
        findPreference<Preference>("projectivy_comm1_videos_options")?.isVisible =
            selectedProviders.contains("COMM1")
        findPreference<Preference>("projectivy_comm2_videos_options")?.isVisible =
            selectedProviders.contains("COMM2")
    }
}
