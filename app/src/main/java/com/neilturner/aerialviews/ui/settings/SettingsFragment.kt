package com.neilturner.aerialviews.ui.settings

import android.content.Intent
import android.os.Bundle
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import com.neilturner.aerialviews.R
import android.os.Parcelable
import android.util.Log
import android.widget.Toast
import java.lang.Exception

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
            openSystemScreensaverSettings()
            return true
        }

        if (preference.key.contains("test_screensaver_settings")) {
            testScreensaverSettings()
            return true
        }

        return super.onPreferenceTreeClick(preference)
    }

    private fun testScreensaverSettings() {
        try {
            val intent = Intent().setClassName(requireContext(), TEST_SCREENSAVER)
            startActivity(intent)
        } catch (ex: Exception) {
            Log.e(TAG, ex.message!!)
        }
    }

    private fun openSystemScreensaverSettings() {
        val intents = mutableListOf<Intent>()
        intents += Intent(SCREENSAVER_SETTINGS)
        intents += Intent(Intent.ACTION_MAIN).setClassName("com.android.tv.settings", "com.android.tv.settings.device.display.daydream.DaydreamActivity")
        intents += Intent(SETTINGS)

        intents.forEach { intent ->
            //Log.i(TAG, intent.toString())
            if (intentAvailable(intent)) {
                try {
                    startActivity(intent)
                    return
                } catch (ex: Exception) {
                    Log.e(TAG, ex.message!!)
                }
            }
        }

        Toast.makeText(requireContext(), "Unable to open your device's screensaver options", Toast.LENGTH_LONG).show()
    }

    private fun intentAvailable(intent: Intent): Boolean {
        val manager = requireActivity().packageManager
        val info = manager.queryIntentActivities(intent, 0)
        return info.isNotEmpty()
    }

    companion object {
        const val SETTINGS = "android.settings.SETTINGS"
        const val SCREENSAVER_SETTINGS = "android.settings.DREAM_SETTINGS"
        const val TEST_SCREENSAVER = "com.neilturner.aerialviews.ui.screensaver.TestActivity"
        const val TAG = "SettingsFragment"
    }
}
