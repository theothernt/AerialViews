package com.neilturner.aerialviews.ui.settings

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import androidx.preference.SwitchPreference
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.utils.LoggingHelper
import com.neilturner.aerialviews.utils.PermissionHelper

class AppearanceNowPlayingFragment :
    PreferenceFragmentCompat(),
    PreferenceManager.OnPreferenceTreeClickListener {
    override fun onCreatePreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        setPreferencesFromResource(R.xml.settings_appearance_nowplaying, rootKey)
        checkPermission()
    }

    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        if (preference.key.isNullOrEmpty()) {
            return super.onPreferenceTreeClick(preference)
        }

        if (preference.key.contains("nowplaying_permission")) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                openNotificationSettings()
            }
        }

        return super.onPreferenceTreeClick(preference)
    }

    private fun openNotificationSettings() {
        try {
            val intent = Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
            startActivity(intent)
        } catch (ex: Exception) {
            Log.e(TAG, ex.message.toString())
        }
    }

    private fun checkPermission() {
        val toggle = preferenceScreen.findPreference<SwitchPreference>("nowplaying_permission")
        val hasPermission = PermissionHelper.hasNotificationListenerPermission(requireContext())

        if (hasPermission) {
            toggle?.isChecked = true
            return
        } else {
            toggle?.isChecked = false
        }

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.O) {
            val notice = preferenceScreen.findPreference<Preference>("nowplaying_permission_legacy_notice")
            notice?.isVisible = true
        }

        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.P ||
            Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
            val notice = preferenceScreen.findPreference<Preference>("nowplaying_permission_notice")
            notice?.isVisible = true
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            toggle?.isEnabled = true
        }
    }

    override fun onResume() {
        super.onResume()
        LoggingHelper.logScreenView("Now Playing", TAG)
        checkPermission()
    }

    companion object {
        private const val TAG = "NowPlayingFragment"
    }
}
