package com.neilturner.aerialviews.ui.sources

import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.models.prefs.SambaMediaPrefs
import com.neilturner.aerialviews.utils.DialogHelper
import com.neilturner.aerialviews.utils.MenuStateFragment
import com.neilturner.aerialviews.utils.NetworkHelper
import com.neilturner.aerialviews.utils.toStringOrEmpty
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SambaVideosWolFragment :
    MenuStateFragment(),
    SharedPreferences.OnSharedPreferenceChangeListener,
    PreferenceManager.OnPreferenceTreeClickListener {
    override fun onCreatePreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        setPreferencesFromResource(R.xml.sources_samba_videos_wol, rootKey)
        preferenceManager.sharedPreferences?.registerOnSharedPreferenceChangeListener(this)

        findPreference<EditTextPreference>("samba_media_wake_on_lan_mac_address")?.setOnPreferenceChangeListener { _, newValue ->
            val mac = newValue as String
            if (mac.isNotEmpty() && !NetworkHelper.isValidMacAddress(mac)) {
                Toast.makeText(requireContext(), R.string.advanced_wol_mac_error, Toast.LENGTH_SHORT).show()
                false
            } else {
                true
            }
        }

        limitTextInput()
        updateSummary()
    }

    override fun onDestroy() {
        preferenceManager.sharedPreferences?.unregisterOnSharedPreferenceChangeListener(this)
        super.onDestroy()
    }

    override fun onSharedPreferenceChanged(
        sharedPreferences: SharedPreferences,
        key: String?,
    ) {
        updateSummary()
    }

    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        if (preference.key == "wake_on_lan_now") {
            lifecycleScope.launch {
                testWolConnection()
            }
            return true
        }
        return super.onPreferenceTreeClick(preference)
    }

    private suspend fun testWolConnection() {
        val macAddress = SambaMediaPrefs.wakeOnLanMacAddress
        val hostName = SambaMediaPrefs.hostName
        val delaySeconds = (SambaMediaPrefs.wakeOnLanDelay.toLongOrNull() ?: 5L) * 1_000L
        val smallDelay = 1_000L

        if (hostName.isEmpty()) {
            Toast.makeText(requireContext(), R.string.samba_videos_hostname_summary, Toast.LENGTH_SHORT).show()
            return
        }

        if (macAddress.isEmpty() || !NetworkHelper.isValidMacAddress(macAddress)) {
            Toast.makeText(requireContext(), R.string.advanced_wol_mac_error, Toast.LENGTH_SHORT).show()
            return
        }

        val progressDialog = DialogHelper.progressDialog(requireContext(), "Checking host status...")
        progressDialog.show()
        delay(smallDelay)

        val isReachable = NetworkHelper.isHostReachable(hostName, 445)

        if (!isReachable) {
            DialogHelper.updateProgressMessage(progressDialog, "Host is down. Sending magic packet...")
            delay(smallDelay)
            NetworkHelper.sendWakeOnLan(macAddress)

            DialogHelper.updateProgressMessage(progressDialog, "Waiting for host to wake up...")
            delay(delaySeconds)

            DialogHelper.updateProgressMessage(progressDialog, "Checking host status again...")
            delay(smallDelay)
            val isReachableAfter = NetworkHelper.isHostReachable(hostName, 445)

            progressDialog.dismiss()
            DialogHelper.showOnMain(
                requireContext(),
                getString(R.string.samba_media_wol_title),
                if (isReachableAfter) "Success: Host is now UP" else "Result: Host is still DOWN",
            )
        } else {
            progressDialog.dismiss()
            AlertDialog
                .Builder(requireContext())
                .setTitle(R.string.samba_media_wol_title)
                .setMessage("Host is already awake. Send wake up command anyway?")
                .setPositiveButton(R.string.button_ok) { _, _ ->
                    lifecycleScope.launch {
                        NetworkHelper.sendWakeOnLan(macAddress)
                        Toast.makeText(requireContext(), "Packet sent", Toast.LENGTH_SHORT).show()
                    }
                }.setNegativeButton(R.string.button_cancel, null)
                .show()
        }
    }

    private fun updateSummary() {
        val macAddress = findPreference<EditTextPreference>("samba_media_wake_on_lan_mac_address")
        val macText = macAddress?.text.toStringOrEmpty()
        if (macText.isNotEmpty()) {
            if (NetworkHelper.isValidMacAddress(macText)) {
                macAddress?.summary = macText
            } else {
                macAddress?.summary = getString(R.string.advanced_wol_mac_error)
            }
        } else {
            macAddress?.summary = getString(R.string.advanced_wol_mac_summary)
        }

        val delay = findPreference<EditTextPreference>("samba_media_wake_on_lan_delay")
        if (delay?.text.toStringOrEmpty().isNotEmpty()) {
            delay?.summary = "${delay.text} seconds"
        } else {
            delay?.summary = getString(R.string.advanced_wol_delay_summary)
        }
    }

    private fun limitTextInput() {
        listOf("samba_media_wake_on_lan_mac_address", "samba_media_wake_on_lan_delay").forEach { key ->
            findPreference<EditTextPreference>(key)?.setOnBindEditTextListener { it.setSingleLine() }
        }
    }
}
