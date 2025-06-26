package com.neilturner.aerialviews.ui.settings

import android.os.Bundle
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.utils.DeviceIPHelper
import com.neilturner.aerialviews.utils.FirebaseHelper
import com.neilturner.aerialviews.utils.MenuStateFragment

class OverlaysMessageApiFragment : MenuStateFragment() {
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

            ipPreference.summary = "Device IP: $ipAddress\nAPI URL: http://$ipAddress:$port"
        }

        if (usagePreference != null) {
            val port = GeneralPrefs.messageApiPort
            val usageText =
                """
                GET /status - Check server status
                GET /message1?text=Hello - Display message on slot 1
                GET /message2?text=Hello - Display message on slot 2
                GET /message3?text=Hello - Display message on slot 3
                GET /message4?text=Hello - Display message on slot 4
                GET /message1?text= - Clear message on slot 1
                
                Parameters:
                • text (required) - Message content, empty to clear
                • duration (optional) - Display duration in seconds
                • textSize (optional) - small/medium/large/xl/xxl
                • textWeight (optional) - light/normal/bold/heavy
                
                Examples:
                http://YOUR_IP:$port/message1?text=Hello%20World
                http://YOUR_IP:$port/message2?text=Test&duration=20&textSize=large&textWeight=bold
                http://YOUR_IP:$port/message3?text= (clears message)
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
