package com.neilturner.aerialviews.ui.settings

import android.content.SharedPreferences
import android.os.Bundle
import androidx.preference.ListPreference
import androidx.preference.PreferenceFragmentCompat
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.models.prefs.InterfacePrefs
import com.neilturner.aerialviews.utils.SlotHelper

class AppearanceRowTopFragment :
    PreferenceFragmentCompat(),
    SharedPreferences.OnSharedPreferenceChangeListener {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings_appearance_row_top, rootKey)
        preferenceManager.sharedPreferences?.registerOnSharedPreferenceChangeListener(this)

        updateDropDownAndSummary()
    }

    override fun onDestroy() {
        preferenceManager.sharedPreferences?.unregisterOnSharedPreferenceChangeListener(this)
        super.onDestroy()
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String) {
        if (key.contains("slot_", false)) {
            SlotHelper.removeDuplicateOverlays(preferenceScreen, key)
            updateDropDownAndSummary()
        }
    }

    private fun updateDropDownAndSummary() {
        val topLeft1 = preferenceScreen.findPreference<ListPreference>("slot_top_left1")
        val topLeft2 = preferenceScreen.findPreference<ListPreference>("slot_top_left2")
        val topRight1 = preferenceScreen.findPreference<ListPreference>("slot_top_right1")
        val topRight2 = preferenceScreen.findPreference<ListPreference>("slot_top_right2")

        val overlayData = SlotHelper.entriesAndValues(requireContext())

        SlotHelper.updateSummary(topLeft1, overlayData.first, InterfacePrefs.slotTopLeft1)
        SlotHelper.updateSummary(topLeft2, overlayData.first, InterfacePrefs.slotTopLeft2)
        SlotHelper.updateSummary(topRight1, overlayData.first, InterfacePrefs.slotTopRight1)
        SlotHelper.updateSummary(topRight2, overlayData.first, InterfacePrefs.slotTopRight2)

        val slotPrefs = SlotHelper.slotPrefs(requireContext())

        SlotHelper.buildOverlayList(topLeft1, overlayData.first, overlayData.second, slotPrefs)
        SlotHelper.buildOverlayList(topLeft2, overlayData.first, overlayData.second, slotPrefs)
        SlotHelper.buildOverlayList(topRight1, overlayData.first, overlayData.second, slotPrefs)
        SlotHelper.buildOverlayList(topRight2, overlayData.first, overlayData.second, slotPrefs)
    }
}
