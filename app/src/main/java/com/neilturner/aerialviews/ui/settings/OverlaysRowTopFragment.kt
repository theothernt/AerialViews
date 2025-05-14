package com.neilturner.aerialviews.ui.settings

import android.content.SharedPreferences
import android.os.Bundle
import androidx.preference.ListPreference
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.models.enums.OverlayType
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.ui.preference.DualDropdownPreference
import com.neilturner.aerialviews.utils.FirebaseHelper
import com.neilturner.aerialviews.utils.MenuStateFragment
import com.neilturner.aerialviews.utils.SlotHelper

class OverlaysRowTopFragment :
    MenuStateFragment(),
    SharedPreferences.OnSharedPreferenceChangeListener {
    override fun onCreatePreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        setPreferencesFromResource(R.xml.settings_overlays_row_top, rootKey)
        setupDualDropdowns()
    }

    private fun setupDualDropdowns() {
        // Get the original ListPreferences (they are set to invisible in XML)
        val topLeft1 = preferenceScreen.findPreference<ListPreference>("slot_top_left1")
        val topLeft2 = preferenceScreen.findPreference<ListPreference>("slot_top_left2")
        val topRight1 = preferenceScreen.findPreference<ListPreference>("slot_top_right1")
        val topRight2 = preferenceScreen.findPreference<ListPreference>("slot_top_right2")

        // Get the dual preference containers
        val dualTopLeft = preferenceScreen.findPreference<DualDropdownPreference>("dual_top_left")
        val dualTopRight = preferenceScreen.findPreference<DualDropdownPreference>("dual_top_right")

        // Set up the dual preferences
        dualTopLeft?.apply {
            topLeft2?.let { setLeftPreference(it) }
            topLeft1?.let { setRightPreference(it) }
        }

        dualTopRight?.apply {
            topRight2?.let { setLeftPreference(it) }
            topRight1?.let { setRightPreference(it) }
        }
    }

    override fun onResume() {
        super.onResume()
        FirebaseHelper.logScreenView("Top Row", this)
        preferenceManager.sharedPreferences?.registerOnSharedPreferenceChangeListener(this)
        updateDropDownAndSummary()
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
        val topLeft1 = preferenceScreen.findPreference<ListPreference>("slot_top_left1")
        val topLeft2 = preferenceScreen.findPreference<ListPreference>("slot_top_left2")
        val topRight1 = preferenceScreen.findPreference<ListPreference>("slot_top_right1")
        val topRight2 = preferenceScreen.findPreference<ListPreference>("slot_top_right2")

        val overlayData = SlotHelper.entriesAndValues(requireContext())

        SlotHelper.updateSummary(topLeft1, overlayData.first, GeneralPrefs.slotTopLeft1 ?: OverlayType.entries.first())
        SlotHelper.updateSummary(topLeft2, overlayData.first, GeneralPrefs.slotTopLeft2 ?: OverlayType.entries.first())
        SlotHelper.updateSummary(topRight1, overlayData.first, GeneralPrefs.slotTopRight1 ?: OverlayType.entries.first())
        SlotHelper.updateSummary(topRight2, overlayData.first, GeneralPrefs.slotTopRight2 ?: OverlayType.entries.first())

        val slotPrefs = SlotHelper.slotPrefs(requireContext())

        SlotHelper.buildOverlayList(topLeft1, overlayData.first, overlayData.second, slotPrefs)
        SlotHelper.buildOverlayList(topLeft2, overlayData.first, overlayData.second, slotPrefs)
        SlotHelper.buildOverlayList(topRight1, overlayData.first, overlayData.second, slotPrefs)
        SlotHelper.buildOverlayList(topRight2, overlayData.first, overlayData.second, slotPrefs)

        // Update dual preferences after list preferences have been updated
        setupDualDropdowns()
    }
}
