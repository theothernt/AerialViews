package com.neilturner.aerialviews.ui.settings

import android.os.Bundle
import androidx.preference.Preference
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.data.network.NetworkHelper
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.ui.controls.MenuStateFragment
import com.neilturner.aerialviews.utils.FirebaseHelper

class OverlaysMessageApiFragment : MenuStateFragment() {
    override fun onCreatePreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        setPreferencesFromResource(R.xml.settings_overlays_message_api, rootKey)
    }

    override fun onResume() {
        super.onResume()
        FirebaseHelper.analyticsScreenView("Message API", this)

        updateIPAddressDisplay()
    }

    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        if (preference.key.isNullOrEmpty()) {
            return super.onPreferenceTreeClick(preference)
        }

        return super.onPreferenceTreeClick(preference)
    }

    private fun updateIPAddressDisplay() {
        val ipPreference = findPreference<Preference>("message_api_current_ip")

        if (ipPreference != null) {
            val ipAddress = NetworkHelper.getIPAddress(requireContext())
            val port = GeneralPrefs.messageApiPort

            // ipPreference.summary = "Device IP: $ipAddress\nAPI URL: http://$ipAddress:$port"
            ipPreference.summary = "API URL: http://$ipAddress:$port"
        }
    }
}
