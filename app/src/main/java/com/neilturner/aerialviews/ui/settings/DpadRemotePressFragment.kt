package com.neilturner.aerialviews.ui.settings

import android.content.SharedPreferences
import android.os.Bundle
import androidx.preference.Preference
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.utils.FirebaseHelper
import com.neilturner.aerialviews.utils.MenuStateFragment
import com.neilturner.aerialviews.utils.toStringOrEmpty

class DpadRemotePressFragment :
    MenuStateFragment(),
    SharedPreferences.OnSharedPreferenceChangeListener {
    override fun onCreatePreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        setPreferencesFromResource(R.xml.settings_dpadremote_press, rootKey)
        preferenceManager.sharedPreferences?.registerOnSharedPreferenceChangeListener(this)
        showMusicPermissionOptionIfNeeded()
    }

    override fun onResume() {
        super.onResume()
        FirebaseHelper.logScreenView("D-Pad/Remote Press", this)
    }

    override fun onDestroy() {
        preferenceManager.sharedPreferences?.unregisterOnSharedPreferenceChangeListener(this)
        super.onDestroy()
    }

    override fun onSharedPreferenceChanged(
        sharedPreferences: SharedPreferences?,
        key: String?,
    ) {
        showMusicPermissionOptionIfNeeded()
    }

    private fun showMusicPermissionOptionIfNeeded() {
        val permission = preferenceScreen.findPreference<Preference>("music_permission_option")
        var showPermission = false

        GeneralPrefs.preferences.all.forEach {
            if (it.key.startsWith("button_") &&
                it.key.endsWith("_press") &&
                it.value.toStringOrEmpty().contains("MUSIC_")
            ) {
                showPermission = true
                return@forEach
            }
        }

        permission?.isVisible = showPermission
    }
}
