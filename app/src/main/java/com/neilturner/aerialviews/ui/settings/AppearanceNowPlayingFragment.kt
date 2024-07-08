package com.neilturner.aerialviews.ui.settings

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat
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

        val intent = Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
        startActivity(intent)
    }

    override fun onResume() {
        super.onResume()
        LoggingHelper.logScreenView("Now Playing", TAG)
    }

    companion object {
        private const val TAG = "NowPlayingFragment"
    }
}
