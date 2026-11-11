package com.neilturner.aerialviews.ui.settings

import android.content.SharedPreferences
import android.os.Bundle
import androidx.preference.CheckBoxPreference
import androidx.preference.Preference
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.ui.controls.MenuStateFragment
import com.neilturner.aerialviews.ui.screensaver.ScreensaverUiFactory
import com.neilturner.aerialviews.utils.FirebaseHelper
import com.neilturner.aerialviews.utils.toStringOrEmpty

class AppearanceStartupFragment :
    MenuStateFragment(),
    SharedPreferences.OnSharedPreferenceChangeListener {
    override fun onCreatePreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        setPreferencesFromResource(R.xml.settings_appearance_startup, rootKey)
    }

    override fun onResume() {
        super.onResume()
        FirebaseHelper.analyticsScreenView("Startup", this)
        showStartScreensaverOnLaunchOption()
        configureComposeScreensaverPreference()
    }

    override fun onDestroy() {
        preferenceManager.sharedPreferences?.unregisterOnSharedPreferenceChangeListener(this)
        super.onDestroy()
    }

    override fun onSharedPreferenceChanged(
        sharedPreferences: SharedPreferences?,
        key: String?,
    ) {
        showStartScreensaverOnLaunchOption()
    }

    private fun configureComposeScreensaverPreference() {
        findPreference<CheckBoxPreference>("use_compose_screensaver")?.setOnPreferenceChangeListener { _, newValue ->
            if (newValue == true) {
                // The guard switches Compose off after repeated startup failures. Explicitly
                // re-enabling the beta must clear that old count so it gets a fresh chance.
                ScreensaverUiFactory.resetComposeFailureCount()
            }
            true
        }
    }

    private fun showStartScreensaverOnLaunchOption() {
        val message = preferenceScreen.findPreference<Preference>("exit_to_settings")
        var showMessage = true

        GeneralPrefs.preferences.all.forEach {
            if (it.key.startsWith("button_") &&
                (it.key.endsWith("_press") || it.key.endsWith("_hold")) &&
                it.value.toStringOrEmpty().contains("EXIT_TO_SETTINGS")
            ) {
                showMessage = false
                return@forEach
            }
        }

        message?.isVisible = showMessage
    }
}
