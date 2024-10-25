package com.neilturner.aerialviews.ui.settings

import android.content.SharedPreferences
import android.os.Bundle
import androidx.preference.ListPreference
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.models.enums.OverlayType
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.utils.FirebaseHelper
import com.neilturner.aerialviews.utils.MenuStateFragment
import com.neilturner.aerialviews.utils.SlotHelper

class OverlaysRowBottomFragment :
    MenuStateFragment(),
    SharedPreferences.OnSharedPreferenceChangeListener {
    override fun onCreatePreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        setPreferencesFromResource(R.xml.settings_overlays_row_bottom, rootKey)
        preferenceManager.sharedPreferences?.registerOnSharedPreferenceChangeListener(this)

        updateDropDownAndSummary()
    }

    override fun onResume() {
        super.onResume()
        FirebaseHelper.logScreenView("Bottom Row", this)
    }

    override fun onDestroy() {
        preferenceManager.sharedPreferences?.unregisterOnSharedPreferenceChangeListener(this)
        super.onDestroy()
    }

    override fun onSharedPreferenceChanged(
        sharedPreferences: SharedPreferences,
        key: String?,
    ) {
        if (key != null && key.contains("slot_", false)) {
            SlotHelper.removeDuplicateOverlays(preferenceScreen, key)
            updateDropDownAndSummary()
        }
    }

    private fun updateDropDownAndSummary() {
        val bottomLeft1 = preferenceScreen.findPreference<ListPreference>("slot_bottom_left1")
        val bottomLeft2 = preferenceScreen.findPreference<ListPreference>("slot_bottom_left2")
        val bottomRight1 = preferenceScreen.findPreference<ListPreference>("slot_bottom_right1")
        val bottomRight2 = preferenceScreen.findPreference<ListPreference>("slot_bottom_right2")

        val overlayData = SlotHelper.entriesAndValues(requireContext())

        SlotHelper.updateSummary(bottomLeft1, overlayData.first, GeneralPrefs.slotBottomLeft1 ?: OverlayType.entries.first())
        SlotHelper.updateSummary(bottomLeft2, overlayData.first, GeneralPrefs.slotBottomLeft2 ?: OverlayType.entries.first())
        SlotHelper.updateSummary(bottomRight1, overlayData.first, GeneralPrefs.slotBottomRight1 ?: OverlayType.entries.first())
        SlotHelper.updateSummary(bottomRight2, overlayData.first, GeneralPrefs.slotBottomRight2 ?: OverlayType.entries.first())

        val slotPrefs = SlotHelper.slotPrefs(requireContext())

        SlotHelper.buildOverlayList(bottomLeft1, overlayData.first, overlayData.second, slotPrefs)
        SlotHelper.buildOverlayList(bottomLeft2, overlayData.first, overlayData.second, slotPrefs)
        SlotHelper.buildOverlayList(bottomRight1, overlayData.first, overlayData.second, slotPrefs)
        SlotHelper.buildOverlayList(bottomRight2, overlayData.first, overlayData.second, slotPrefs)
    }
}
