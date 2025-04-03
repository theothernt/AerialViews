package com.neilturner.aerialviews.ui.settings

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import androidx.preference.SwitchPreference
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.utils.FirebaseHelper
import com.neilturner.aerialviews.utils.MenuStateFragment
import com.neilturner.aerialviews.utils.PermissionHelper
import timber.log.Timber

class OverlaysNowPlayingFragment :
    MenuStateFragment(),
    PreferenceManager.OnPreferenceTreeClickListener {
    override fun onCreatePreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        setPreferencesFromResource(R.xml.settings_overlays_nowplaying, rootKey)

    }

    override fun onResume() {
        super.onResume()
        FirebaseHelper.logScreenView("Now Playing", this)
        checkPermission()

    }

    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        if (preference.key.isNullOrEmpty()) {
            return super.onPreferenceTreeClick(preference)
        }

        if (preference.key.contains("nowplaying_permission") &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
        ) {
            openNotificationSettings()
        }

        return super.onPreferenceTreeClick(preference)
    }

    private fun openNotificationSettings() {
        try {
            val intent = Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            startActivity(intent)
        } catch (ex: Exception) {
            Timber.e(ex, "Unable to open notification settings: ${ex.message}")
            try {
                val intent = Intent(android.provider.Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS)
                startActivity(intent)

                val toast = Toast.makeText(requireContext(), R.string.nowplaying_toast_text, Toast.LENGTH_LONG)
                toast.show()
            } catch (ex2: Exception) {
                Timber.e(ex2, "Unable to open manage application settings: ${ex2.message}")
            }
        }
    }

    private fun checkPermission() {
        val toggle = preferenceScreen.findPreference<SwitchPreference>("nowplaying_permission")
        val hasPermission = PermissionHelper.hasNotificationListenerPermission(requireContext())

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            toggle?.isEnabled = true
        }

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
            Build.VERSION.SDK_INT == Build.VERSION_CODES.Q
        ) {
            val notice = preferenceScreen.findPreference<Preference>("nowplaying_permission_notice")
            notice?.isVisible = true
        }
    }
}
