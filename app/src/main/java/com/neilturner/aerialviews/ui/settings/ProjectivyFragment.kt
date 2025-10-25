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
        updateAppleVideosOptionsVisibility()
    }

    override fun onDestroy() {
        preferenceManager.sharedPreferences?.unregisterOnSharedPreferenceChangeListener(this)
        super.onDestroy()
    }

    override fun onSharedPreferenceChanged(
        sharedPreferences: SharedPreferences,
        key: String?,
    ) {
        if (key == "projectivy_apple_videos_enabled") {
            updateAppleVideosOptionsVisibility()
        }
    }

    private fun updateAppleVideosOptionsVisibility() {
        val appleVideosEnabled = preferenceManager.sharedPreferences
            ?.getBoolean("projectivy_apple_videos_enabled", false) ?: false

        val appleVideosOptions = preferenceScreen.findPreference<Preference>("projectivy_apple_videos_options")
        appleVideosOptions?.isVisible = appleVideosEnabled
    }
}
