package com.neilturner.aerialviews.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.models.prefs.LocalMediaPrefs
import com.neilturner.aerialviews.utils.DeviceHelper
import com.neilturner.aerialviews.utils.PermissionHelper
import com.neilturner.aerialviews.utils.ToastHelper
import kotlinx.coroutines.launch
import java.lang.Exception

class MainFragment :
    PreferenceFragmentCompat(),
    PreferenceManager.OnPreferenceTreeClickListener {
    override fun onCreatePreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        setPreferencesFromResource(R.xml.main, rootKey)
        resetLocalPermissionIfNeeded()
        setMenuLocale()
    }

    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        if (preference.key.isNullOrEmpty()) {
            return super.onPreferenceTreeClick(preference)
        }

        if (preference.key.contains("system_options")) {
            openSystemScreensaverSettings()
            return true
        }

        if (preference.key.contains("preview_screensaver")) {
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
            Log.e(TAG, ex.message.toString())
        }
    }

    private fun setMenuLocale() {
        val appLocale =
            if (!GeneralPrefs.localeMenu.startsWith("default")) {
                LocaleListCompat.forLanguageTags(GeneralPrefs.localeMenu)
            } else {
                LocaleListCompat.getEmptyLocaleList()
            }
        AppCompatDelegate.setApplicationLocales(appLocale)
    }

    private fun resetLocalPermissionIfNeeded() {
        // Check if we still have permission on startup as they can be revoked outside the app
        val canReadImagesVideos = PermissionHelper.hasMediaReadPermission(requireContext())
        if (LocalMediaPrefs.enabled &&
            !canReadImagesVideos
        ) {
            LocalMediaPrefs.enabled = false
        }
    }

    private fun openSystemScreensaverSettings() {
        // Show warning but try to invoke screensaver settings anyway
        // just in case device detection is wrong in future, etc
        if (!DeviceHelper.canAccessScreensaverSettings()) {
            lifecycleScope.launch {
                ToastHelper.show(
                    requireContext(),
                    requireContext().getString(R.string.settings_system_options_removed)
                )
            }
        }

        val intents = mutableListOf<Intent>()
        intents +=
            Intent(
                Intent.ACTION_MAIN,
            ).setClassName("com.android.tv.settings", "com.android.tv.settings.device.display.daydream.DaydreamActivity")
        intents += Intent(SCREENSAVER_SETTINGS)
        intents += Intent(SETTINGS)

        intents.forEach { intent ->
            if (intentAvailable(intent)) {
                try {
                    Log.i(TAG, "Trying... $intent")
                    startActivity(intent)
                    return
                } catch (ex: Exception) {
                    Log.e(TAG, ex.message.toString())
                }
            }
        }

        lifecycleScope.launch {
            ToastHelper.show(
                requireContext(),
                requireContext().getString(R.string.settings_system_options_error)
            )
        }
    }

    private fun intentAvailable(intent: Intent): Boolean {
        val manager = requireActivity().packageManager
        val intents =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                manager.queryIntentActivities(
                    intent,
                    PackageManager.ResolveInfoFlags.of(
                        PackageManager.MATCH_DEFAULT_ONLY.toLong(),
                    ),
                )
            } else {
                manager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            }

        if (intents.isEmpty()) {
            Log.i(TAG, "Intent not available... $intent")
        }
        return intents.isNotEmpty()
    }

    companion object {
        const val SETTINGS = "android.settings.SETTINGS"
        const val SCREENSAVER_SETTINGS = "android.settings.DREAM_SETTINGS"
        const val TEST_SCREENSAVER = "com.neilturner.aerialviews.ui.screensaver.TestActivity"
        const val TAG = "MainFragment"
    }
}
