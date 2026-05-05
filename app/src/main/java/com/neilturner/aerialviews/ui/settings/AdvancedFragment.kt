package com.neilturner.aerialviews.ui.settings

import android.content.Context
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
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.ui.helpers.DialogHelper
import com.neilturner.aerialviews.utils.FirebaseHelper
import com.neilturner.aerialviews.utils.LogcatCapture
import com.neilturner.aerialviews.ui.controls.MenuStateFragment
import com.neilturner.aerialviews.ui.helpers.PermissionHelper
import timber.log.Timber

class AdvancedFragment :
    MenuStateFragment(),
    PreferenceManager.OnPreferenceTreeClickListener {
    override fun onCreatePreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        setPreferencesFromResource(R.xml.settings_advanced, rootKey)
    }

    override fun onResume() {
        super.onResume()
        FirebaseHelper.analyticsScreenView("Advanced", this)
        restartOnLanguageChange()
        checkPermission()
        setupLogCapture()
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
        try {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
            startActivity(intent)
        } catch (ex: Exception) {
            Timber.e(ex, "Unable to system overlay settings: ${ex.message}")
        }
    }

    private fun checkPermission() {
        val toggle = preferenceScreen.findPreference<SwitchPreference>("application_overlay_permission")
        val hasPermission = PermissionHelper.hasSystemOverlayPermission(requireContext())

        if (hasPermission) {
            toggle?.isChecked = true
            return
        } else {
            toggle?.isChecked = false
        }
    }

    private fun setupLogCapture() {
        val toggle = findPreference<SwitchPreference>("enable_log_capture")
        toggle?.isChecked = GeneralPrefs.enableLogCapture

        toggle?.setOnPreferenceChangeListener { _, newValue ->
            if (newValue as Boolean) {
                LogcatCapture.start(requireContext())
            } else {
                LogcatCapture.stop()
            }
            true
        }

        val saveLogs = findPreference<Preference>("save_logs")
        saveLogs?.onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
                LogcatCapture.stop()
                toggle?.isChecked = false
                GeneralPrefs.enableLogCapture = false

                val savedFile = LogcatCapture.saveToDocuments(requireContext())
                if (savedFile != null) {
                    DialogHelper.show(
                        requireContext(),
                        getString(R.string.advanced_log_capture_saved_title),
                        getString(R.string.advanced_log_capture_saved_message, savedFile.absolutePath),
                    )
                } else {
                    DialogHelper.show(
                        requireContext(),
                        getString(R.string.advanced_log_capture_error_title),
                        getString(R.string.advanced_log_capture_error_message),
                    )
                }
                true
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
                requireContext().restartApplication()
            }
            create().show()
        }
    }

    fun Context.restartApplication() {
        try {
            val intent = packageManager.getLaunchIntentForPackage(packageName)
            val componentName = intent?.component
            val mainIntent = Intent.makeRestartActivityTask(componentName)
            mainIntent.putExtra("from_app_restart", true)
            startActivity(mainIntent)
            Runtime.getRuntime().exit(0)
        } catch (ex: Exception) {
            Timber.e(ex, "Unable to restart application: ${ex.message}")
        }
    }
}
