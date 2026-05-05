package com.neilturner.aerialviews.ui.settings

import android.content.SharedPreferences
import android.os.Bundle
import androidx.preference.Preference
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.utils.FirebaseHelper
import androidx.lifecycle.lifecycleScope
import com.neilturner.aerialviews.ui.helpers.InputHelper
import com.neilturner.aerialviews.ui.controls.MenuStateFragment
import com.neilturner.aerialviews.ui.helpers.PermissionHelper
import com.neilturner.aerialviews.utils.toStringOrEmpty
import kotlinx.coroutines.launch

class DpadRemotePressFragment :
    MenuStateFragment(),
    SharedPreferences.OnSharedPreferenceChangeListener {
    override fun onCreatePreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        setPreferencesFromResource(R.xml.settings_dpadremote_press, rootKey)
    }

    override fun onResume() {
        super.onResume()
        FirebaseHelper.analyticsScreenView("D-Pad/Remote Press", this)
        preferenceManager.sharedPreferences?.registerOnSharedPreferenceChangeListener(this)
        showMusicPermissionOption()
        showStartScreensaverOnLaunchOption()
    }

    override fun onDestroy() {
        preferenceManager.sharedPreferences?.unregisterOnSharedPreferenceChangeListener(this)
        super.onDestroy()
    }

    override fun onSharedPreferenceChanged(
        sharedPreferences: SharedPreferences?,
        key: String?,
    ) {
        showMusicPermissionOption()
        showStartScreensaverOnLaunchOption()
        lifecycleScope.launch {
            InputHelper.checkAndResetPlaybackSpeed(requireContext())
        }
    }

    private fun showStartScreensaverOnLaunchOption() {
        val message = preferenceScreen.findPreference<Preference>("screensaver_on_launch_option")
        var usingExitToSettingsAction = false

        GeneralPrefs.preferences.all.forEach {
            if (it.key.startsWith("button_") &&
                (it.key.endsWith("_press") || it.key.endsWith("_hold")) &&
                it.value.toStringOrEmpty().contains("EXIT_TO_SETTINGS")
            ) {
                usingExitToSettingsAction = true
                return@forEach
            }
        }

        message?.isVisible = !usingExitToSettingsAction && GeneralPrefs.startScreensaverOnLaunch
    }

    private fun showMusicPermissionOption() {
        val permission = preferenceScreen.findPreference<Preference>("music_permission_option")
        var usingMusicActions = false

        GeneralPrefs.preferences.all.forEach {
            if (it.key.startsWith("button_") &&
                it.key.endsWith("_press") &&
                it.value.toStringOrEmpty().contains("MUSIC_")
            ) {
                usingMusicActions = true
                return@forEach
            }
        }

        permission?.isVisible = usingMusicActions && !PermissionHelper.hasNotificationListenerPermission(requireContext())
    }
}
