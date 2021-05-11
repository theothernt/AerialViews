package com.codingbuffalo.aerialdream.ui.settings

import android.content.Intent
import android.os.Bundle
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import com.codingbuffalo.aerialdream.R

class SettingsFragment :
    PreferenceFragmentCompat(),
    PreferenceManager.OnPreferenceTreeClickListener {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings, rootKey)
    }

    override fun onPreferenceTreeClick(preference: Preference): Boolean {

        if(preference.key == null || !preference.key.contains("activate_screensaver"))
            return super.onPreferenceTreeClick(preference)

        // Check if the daydream intent is available - some devices (e.g. Nvidia Shield) do not support it
        var intent = Intent(SCREENSAVER_SETTINGS)
        if (!intentAvailable(intent)) {
            // Try opening the daydream settings activity directly: https://gist.github.com/reines/bc798a2cb539f51877bb279125092104
            intent = Intent(Intent.ACTION_MAIN).setClassName("com.android.tv.settings", "com.android.tv.settings.device.display.daydream.DaydreamActivity")
            if (!intentAvailable(intent)) {
                // If all else fails, open the normal settings screen
                intent = Intent(SETTINGS)
            }
        }
        startActivity(intent)
        return true
    }

    private fun intentAvailable(intent: Intent): Boolean {
        val manager = requireActivity().packageManager
        val info = manager.queryIntentActivities(intent, 0)
        return info.isNotEmpty()
    }

    companion object {
        const val SETTINGS = "android.settings.SETTINGS"
        const val SCREENSAVER_SETTINGS = "android.settings.DREAM_SETTINGS"
    }
}
