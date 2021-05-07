package com.codingbuffalo.aerialdream.ui.settings

import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat
import com.codingbuffalo.aerialdream.R

class SettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings, rootKey)
    }
}

//    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
//        setPreferencesFromResource(R.xml.settings, rootKey)
//        findPreference("system_options").onPreferenceClickListener = this
//        preferenceManager.sharedPreferences.registerOnSharedPreferenceChangeListener(this)
//        //updateSummaries()
//    }
//
//    override fun onPreferenceClick(preference: Preference): Boolean {
//        // Check if the daydream intent is available - some devices (e.g. NVidia Shield) do not support it
//        var intent = Intent(SCREENSAVER_SETTINGS)
//        if (!intentAvailable(intent)) {
//            // Try opening the daydream settings activity directly: https://gist.github.com/reines/bc798a2cb539f51877bb279125092104
//            intent = Intent(Intent.ACTION_MAIN).setClassName("com.android.tv.settings", "com.android.tv.settings.device.display.daydream.DaydreamActivity")
//            if (!intentAvailable(intent)) {
//                // If all else fails, open the normal settings screen
//                intent = Intent(SETTINGS)
//            }
//        }
//        startActivity(intent)
//        return true
//    }
//
//    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String) {
//        if (key == VIDEO_SOURCE) {
//            checkUserPermission(sharedPreferences)
//        } else {
//            updateSummaries()
//        }
//    }
//
//    private fun checkUserPermission(sharedPreferences: SharedPreferences) {
//        val pref = sharedPreferences.getString(VIDEO_SOURCE, "0")!!.toInt()
//        if (pref != 0 && !hasStoragePermission()) {
//            requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
//                    PERMISSION_READ_EXTERNAL_STORAGE)
//        } else {
//            updateSummaries()
//        }
//    }
//
//    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
//        when (requestCode) {
//            PERMISSION_READ_EXTERNAL_STORAGE -> if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_DENIED) {
//                resetVideoSource()
//            } else {
//                updateSummaries()
//            }
//        }
//    }
//
//    private fun hasStoragePermission(): Boolean {
//        return (ContextCompat.checkSelfPermission(this.activity!!,
//                Manifest.permission.READ_EXTERNAL_STORAGE)
//                == PackageManager.PERMISSION_GRANTED)
//    }
//
//    private fun resetVideoSource() {
//        val pref = findPreference(VIDEO_SOURCE) as ListPreference
//        pref.value = "0"
//    }
//
//    private fun updateSummaries() {
//        var listPrefs = findPreference(SOURCE_APPLE_2019) as ListPreference
//        listPrefs.summary = listPrefs.entry
//        listPrefs = findPreference(VIDEO_SOURCE) as ListPreference
//        listPrefs.summary = listPrefs.entry
//    }
//
//    private fun intentAvailable(intent: Intent): Boolean {
//        val manager = activity!!.packageManager
//        val info = manager.queryIntentActivities(intent, 0)
//        return info.isNotEmpty()
//    }
//
//    class AppleVideosSettingsFragment : PreferenceFragmentCompat() {
//        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
//            setPreferencesFromResource(R.xml.settings_apple_videos, rootKey)
//        }
//    }
//
//    class LocalVideosSettingsFragment : PreferenceFragmentCompat() {
//        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
//            setPreferencesFromResource(R.xml.settings_local_videos, rootKey)
//        }
//    }
//
//    companion object {
//        const val SETTINGS = "android.settings.SETTINGS"
//        const val SCREENSAVER_SETTINGS = "android.settings.DREAM_SETTINGS"
//        const val VIDEO_SOURCE = "video_source"
//        const val SOURCE_APPLE_2019 = "source_apple_2019"
//        const val PERMISSION_READ_EXTERNAL_STORAGE = 1
//    }
//}