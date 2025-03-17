@file:Suppress("SameReturnValue")

package com.neilturner.aerialviews.ui.settings

import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.utils.FirebaseHelper
import com.neilturner.aerialviews.utils.MenuStateFragment
import kotlinx.coroutines.launch
import timber.log.Timber

class ImportExportFragment :
    MenuStateFragment(),
    PreferenceManager.OnPreferenceTreeClickListener {
    override fun onCreatePreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        setPreferencesFromResource(R.xml.settings_import_export, rootKey)

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
                arguments?.getParcelable("dataUri")
            }

        Timber.i("Data: $dataUri")
    }

    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        if (preference.key.contains("export_settings")) {
            lifecycleScope.launch { exportSettings() }
            return true
        }

        return super.onPreferenceTreeClick(preference)
    }

    private fun exportSettings() {
        // Export settings
        Toast.makeText(requireContext(), "Settings exported", Toast.LENGTH_SHORT).show()
    }
}
