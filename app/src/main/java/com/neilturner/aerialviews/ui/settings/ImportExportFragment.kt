@file:Suppress("SameReturnValue")

package com.neilturner.aerialviews.ui.settings

import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.databinding.DialogImportSettingsBinding
import com.neilturner.aerialviews.utils.DialogHelper
import com.neilturner.aerialviews.utils.FirebaseHelper
import com.neilturner.aerialviews.utils.MenuStateFragment
import com.neilturner.aerialviews.utils.PermissionHelper
import com.neilturner.aerialviews.utils.PreferenceHelper
import kotlinx.coroutines.launch

class ImportExportFragment :
    MenuStateFragment(),
    PreferenceManager.OnPreferenceTreeClickListener {
    private lateinit var requestWritePermission: ActivityResultLauncher<String>

    override fun onCreatePreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        setPreferencesFromResource(R.xml.settings_import_export, rootKey)

        lifecycleScope.launch {
            processDataUri()

            requestWritePermission =
                registerForActivityResult(
                    ActivityResultContracts.RequestPermission(),
                ) { isGranted: Boolean ->
                    exportSettings()
                }
        }
    }

    override fun onResume() {
        super.onResume()
        FirebaseHelper.logScreenView("ImportExport", this)
    }

    private fun processDataUri() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arguments?.getParcelable("dataUri", Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            arguments?.getParcelable("dataUri")
        }?.apply {
            importSettings(this)
        }
    }

    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        if (preference.key.contains("export_settings")) {
            checkWritePermission()
            return true
        }
        return super.onPreferenceTreeClick(preference)
    }

    private fun checkWritePermission() {
        val hasPermission = PermissionHelper.hasDocumentWritePermission(requireContext())
        if (!hasPermission) {
            requestWritePermission.launch(PermissionHelper.getWriteDocumentPermission())
        } else {
            exportSettings()
        }
    }

    private fun exportSettings() {
        val success = PreferenceHelper.exportPreferences(requireContext())
        val res = requireContext().resources
        if (success) {
            DialogHelper
                .show(
                    requireContext(),
                    "",
                    res.getString(R.string.settings_export_successful),
                )
        } else {
            DialogHelper
                .show(
                    requireContext(),
                    "",
                    res.getString(R.string.settings_export_failed),
                )
        }
    }

    private fun importSettings(uri: Uri) {
        val binding = DialogImportSettingsBinding.inflate(LayoutInflater.from(requireContext()))
        val res = requireContext().resources

        AlertDialog
            .Builder(requireContext())
            .setMessage(res.getString(R.string.settings_import_settings))
            .setView(binding.root)
            .setPositiveButton(res.getString(R.string.button_import)) { _, _ ->
                val clearExisting = binding.checkboxClearSettings.isChecked
                val success =
                    PreferenceHelper.importPreferences(
                        requireContext(),
                        uri,
                        clearExisting,
                    )

                val message =
                    if (success) {
                        res.getString(R.string.settings_export_successful)
                    } else {
                        res.getString(R.string.settings_export_failed)
                    }

                DialogHelper
                    .show(
                        requireContext(),
                        "",
                        message,
                    )
            }.setNegativeButton(res.getString(R.string.button_cancel)) { dialog, _ ->
                dialog.dismiss()
            }.show()
    }
}
