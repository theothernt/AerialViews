@file:Suppress("SameReturnValue")

package com.neilturner.aerialviews.ui.settings

import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.utils.DialogHelper
import com.neilturner.aerialviews.utils.FirebaseHelper
import com.neilturner.aerialviews.utils.MenuStateFragment
import com.neilturner.aerialviews.utils.PermissionHelper
import com.neilturner.aerialviews.utils.PreferencesHelper
import kotlinx.coroutines.launch
import timber.log.Timber

class ImportExportFragment :
    MenuStateFragment(),
    PreferenceManager.OnPreferenceTreeClickListener {
    private lateinit var requestWritePermission: ActivityResultLauncher<String>

    override fun onCreatePreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        setPreferencesFromResource(R.xml.settings_import_export, rootKey)

        requestWritePermission =
            registerForActivityResult(
                ActivityResultContracts.RequestPermission(),
            ) { isGranted: Boolean ->
                exportSettings()
            }

        lifecycleScope.launch {
            processDataUri()
        }
    }

    override fun onResume() {
        super.onResume()
        FirebaseHelper.logScreenView("ImportExport", this)
    }

    private fun processDataUri() {
        val dataUri: Uri? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                arguments?.getParcelable("dataUri", Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                arguments?.getParcelable("dataUri")
            }?.apply {
                PreferencesHelper.importPreferences(
                    requireContext(),
                    this,
                )
            }
        Timber.i("Data: $dataUri")
    }

    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        if (preference.key.contains("export_settings")) {
            checkWritePermission()
            return true
        }
        return super.onPreferenceTreeClick(preference)
    }

    private fun checkWritePermission() =
        lifecycleScope.launch {
            val hasPermission = PermissionHelper.hasDocumentWritePermission(requireContext())
            if (!hasPermission) {
                requestWritePermission.launch(PermissionHelper.getWriteDocumentPermission())
            } else {
                exportSettings()
            }
        }

    private fun exportSettings() =
        lifecycleScope.launch {
            val success = PreferencesHelper.exportPreferences(requireContext())
            val res = requireContext().resources
            if (success) {
                DialogHelper
                    .show(
                        requireContext(),
                        "",
                        res.getString(R.string.import_export_successful),
                    ).show()
            } else {
                DialogHelper
                    .show(
                        requireContext(),
                        "",
                        res.getString(R.string.import_export_failed),
                    ).show()
            }
        }
}
