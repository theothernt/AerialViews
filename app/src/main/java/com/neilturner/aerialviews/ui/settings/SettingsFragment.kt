package com.neilturner.aerialviews.ui.settings

import android.content.Intent
import android.os.Bundle
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import com.neilturner.aerialviews.R
import android.os.Parcelable

class SettingsFragment :
    PreferenceFragmentCompat(),
    PreferenceManager.OnPreferenceTreeClickListener {
    private var state: Parcelable? = null

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings, rootKey)
    }

    override fun onPause() {
        state = listView.layoutManager?.onSaveInstanceState()
        super.onPause()
    }

    override fun onResume() {
        if (state != null) listView.layoutManager?.onRestoreInstanceState(state)
        super.onResume()
    }

    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        if (preference.key.isNullOrEmpty())
            return super.onPreferenceTreeClick(preference)

        if (preference.key.contains("open_system_screensaver_options")) {
            val intent = findScreensaverIntent()
            startActivity(intent)
            return true
        }

        return super.onPreferenceTreeClick(preference)
    }

    private fun findScreensaverIntent(): Intent {
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
        return intent
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
