package com.neilturner.aerialviews.ui.settings

import android.os.Bundle
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.utils.DeviceIPHelper
import com.neilturner.aerialviews.utils.FirebaseHelper
import com.neilturner.aerialviews.utils.MenuStateFragment

class OverlaysMessageApiFragment :
    MenuStateFragment() {

    override fun onCreatePreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        setPreferencesFromResource(R.xml.settings_overlays_message_api, rootKey)
    }

    override fun onResume() {
        super.onResume()
        FirebaseHelper.logScreenView("Message API", this)

        updateIPAddressDisplay()
        limitTextInput()
    }

    override fun onDestroy() {

        super.onDestroy()
    }

    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        if (preference.key.isNullOrEmpty()) {
            return super.onPreferenceTreeClick(preference)
        }

        return super.onPreferenceTreeClick(preference)
    }

    private fun updateIPAddressDisplay() {
        val ipPreference = findPreference<Preference>("message_api_current_ip")
        val usagePreference = findPreference<Preference>("message_api_usage")
        
        if (ipPreference != null) {
            val ipAddress = DeviceIPHelper.getIPAddress(requireContext())
            val port = GeneralPrefs.messageApiPort

            val summary =
                "Device IP: $ipAddress\nAPI URL: http://$ipAddress:$port"

            ipPreference.summary = summary
        }

        if (usagePreference != null) {
            val port = GeneralPrefs.messageApiPort
            val usageText = """
                GET /api/messages - Get current messages
                POST /api/messages/1 - Set message line 1
                POST /api/messages/2 - Set message line 2
                
                Example: curl -X POST http://YOUR_IP:$port/api/messages/1 -H "Content-Type: application/json" -d '{"message":"Hello World"}'
            """.trimIndent()
            usagePreference.summary = usageText
        }
    }

    private fun limitTextInput() {
        preferenceScreen.findPreference<EditTextPreference>("message_api_port")?.setOnBindEditTextListener { editText ->
            editText.setSingleLine()
            editText.inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }
    }
}
