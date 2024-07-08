package com.neilturner.aerialviews.ui.settings

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AlertDialog
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreference
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.utils.LoggingHelper
import com.neilturner.aerialviews.utils.PermissionHelper

class AppearanceNowPlayingFragment :
    PreferenceFragmentCompat(),
    SharedPreferences.OnSharedPreferenceChangeListener {
    override fun onCreatePreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        setPreferencesFromResource(R.xml.settings_appearance_nowplaying, rootKey)
        preferenceManager.sharedPreferences?.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onDestroy() {
        preferenceManager.sharedPreferences?.unregisterOnSharedPreferenceChangeListener(this)
        super.onDestroy()
    }

    override fun onSharedPreferenceChanged(
        sharedPreferences: SharedPreferences,
        key: String?,
    ) {
        if (key == "nowplaying_enabled" &&
            GeneralPrefs.nowPlayingEnabled
        ) {
            checkForMediaPermission()
        }
    }

    private fun checkForMediaPermission() {
        // If we already have permission, exit
        if (PermissionHelper.hasNotificationListenerPermission(requireContext())) {
            return
        }

        try {
            val intent = Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
            startActivity(intent)
        } catch (ex: Exception) {
            Log.e(TAG, ex.message.toString())
            featureUnsupported()
        }
    }

    private fun featureUnsupported() {
        AlertDialog.Builder(requireContext()).apply {
            setTitle(R.string.nowplaying_unsupported_title)
            setMessage(R.string.nowplaying_unsupported_summary)
            setPositiveButton(R.string.button_ok) { _, _ ->
                resetPreference()
            }
            create().show()
        }
    }

    private fun resetPreference() {
        val toggle = findPreference<SwitchPreference>("nowplaying_enabled")
        toggle?.isChecked = false
    }

    override fun onResume() {
        super.onResume()
        LoggingHelper.logScreenView("Now Playing", TAG)
    }

    companion object {
        private const val TAG = "NowPlayingFragment"
    }
}
