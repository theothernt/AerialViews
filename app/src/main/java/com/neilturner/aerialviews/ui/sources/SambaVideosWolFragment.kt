package com.neilturner.aerialviews.ui.sources

import android.content.SharedPreferences
import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.models.prefs.SambaMediaPrefs
import com.neilturner.aerialviews.utils.MenuStateFragment
import com.neilturner.aerialviews.utils.NetworkHelper
import com.neilturner.aerialviews.utils.toStringOrEmpty
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
                NetworkHelper.sendWakeOnLan(SambaMediaPrefs.wakeOnLanMacAddress)
            }
            return true
        }
        return super.onPreferenceTreeClick(preference)
    }

    private fun updateSummary() {
        val macAddress = findPreference<EditTextPreference>("samba_media_wake_on_lan_mac_address")
        if (macAddress?.text.toStringOrEmpty().isNotEmpty()) {
            macAddress?.summary = macAddress.text
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
