package com.neilturner.aerialviews.ui.settings

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.appcompat.app.AlertDialog
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import androidx.preference.SwitchPreference
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.utils.FirebaseHelper
import com.neilturner.aerialviews.utils.MenuStateFragment
import com.neilturner.aerialviews.utils.PermissionHelper
import timber.log.Timber

class AdvancedFragment : MenuStateFragment(),
    PreferenceManager.OnPreferenceTreeClickListener {
    override fun onCreatePreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        setPreferencesFromResource(R.xml.settings_advanced, rootKey)
        restartOnLanguageChange()
        checkPermission()
    }

    override fun onResume() {
        super.onResume()
        FirebaseHelper.logScreenView("Advanced", this)
        checkPermission()
    }

    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        if (preference.key.isNullOrEmpty()) {
            return super.onPreferenceTreeClick(preference)
        }

        if (preference.key.contains("application_overlay_permission") &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
        ) {
            openSystemOverlaySettings()
        }

        return super.onPreferenceTreeClick(preference)
    }

    private fun openSystemOverlaySettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return
        }

        try {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
            startActivity(intent)
        } catch (ex: Exception) {
            Timber.e(ex, "Unable to system overlay settings: ${ex.message}")
        }
    }

    private fun checkPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return
        }

        val toggle = preferenceScreen.findPreference<SwitchPreference>("application_overlay_permission")
        val hasPermission = PermissionHelper.hasSystemOverlayPermission(requireContext())

        if (hasPermission) {
            toggle?.isChecked = true
            return
        } else {
            toggle?.isChecked = false
        }
    }

    private fun restartOnLanguageChange() {
        val dialects = findPreference<ListPreference>("locale_menu")
        dialects?.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { _, _ ->
                askForRestart()
                true
            }
    }

    private fun askForRestart() {
        AlertDialog.Builder(requireContext()).apply {
            setTitle(R.string.advanced_locale_restart_app_title)
            setMessage(R.string.advanced_locale_restart_app_summary)
            setNegativeButton(R.string.button_cancel) { _, _ ->
            }
            setPositiveButton(R.string.button_ok) { _, _ ->
                startActivity(Intent.makeRestartActivityTask(activity?.intent?.component))
            }
            create().show()
        }
    }
}
