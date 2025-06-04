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

class OverlaysSlotsFragment :
    MenuStateFragment(),
    SharedPreferences.OnSharedPreferenceChangeListener {
    override fun onCreatePreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        setPreferencesFromResource(R.xml.settings_overlays_slots, rootKey)
        setupDualDropdowns()
    }

    private fun setupDualDropdowns() {
        // Get the original ListPreferences (they are set to invisible in XML)
        // Top Row
        val topLeft1 = preferenceScreen.findPreference<ListPreference>("slot_top_left1")
        val topLeft2 = preferenceScreen.findPreference<ListPreference>("slot_top_left2")
        val topRight1 = preferenceScreen.findPreference<ListPreference>("slot_top_right1")
        val topRight2 = preferenceScreen.findPreference<ListPreference>("slot_top_right2")
        
        // Bottom Row
        val bottomLeft1 = preferenceScreen.findPreference<ListPreference>("slot_bottom_left1")
        val bottomLeft2 = preferenceScreen.findPreference<ListPreference>("slot_bottom_left2")
        val bottomRight1 = preferenceScreen.findPreference<ListPreference>("slot_bottom_right1")
        val bottomRight2 = preferenceScreen.findPreference<ListPreference>("slot_bottom_right2")

        // Get the dual preference containers
        val dualTopLeft = preferenceScreen.findPreference<DualDropdownPreference>("dual_top_left")
        val dualTopRight = preferenceScreen.findPreference<DualDropdownPreference>("dual_top_right")
        val dualBottomLeft = preferenceScreen.findPreference<DualDropdownPreference>("dual_bottom_left")
        val dualBottomRight = preferenceScreen.findPreference<DualDropdownPreference>("dual_bottom_right")        // Set up the dual preferences - Upper Slot (slot2) appears first, Lower Slot (slot1) appears second
        // Note: XML ordering is dual_top_right (slot2) first, then dual_top_left (slot1) second
        dualTopRight?.apply {
            topLeft2?.let { setLeftPreference(it) }
            topRight2?.let { setRightPreference(it) }
        }

        dualTopLeft?.apply {
            topLeft1?.let { setLeftPreference(it) }
            topRight1?.let { setRightPreference(it) }
        }
        
        dualBottomRight?.apply {
            bottomLeft2?.let { setLeftPreference(it) }
            bottomRight2?.let { setRightPreference(it) }
        }

        dualBottomLeft?.apply {
            bottomLeft1?.let { setLeftPreference(it) }
            bottomRight1?.let { setRightPreference(it) }
        }
    }

    override fun onResume() {
        super.onResume()
        FirebaseHelper.logScreenView("Combined Rows", this)
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
        // Top Row
        val topLeft1 = preferenceScreen.findPreference<ListPreference>("slot_top_left1")
        val topLeft2 = preferenceScreen.findPreference<ListPreference>("slot_top_left2")
        val topRight1 = preferenceScreen.findPreference<ListPreference>("slot_top_right1")
        val topRight2 = preferenceScreen.findPreference<ListPreference>("slot_top_right2")
        
        // Bottom Row
        val bottomLeft1 = preferenceScreen.findPreference<ListPreference>("slot_bottom_left1")
        val bottomLeft2 = preferenceScreen.findPreference<ListPreference>("slot_bottom_left2")
        val bottomRight1 = preferenceScreen.findPreference<ListPreference>("slot_bottom_right1")
        val bottomRight2 = preferenceScreen.findPreference<ListPreference>("slot_bottom_right2")

        val overlayData = SlotHelper.entriesAndValues(requireContext())

        // Update Top Row
        SlotHelper.updateSummary(topLeft1, overlayData.first, GeneralPrefs.slotTopLeft1 ?: OverlayType.entries.first())
        SlotHelper.updateSummary(topLeft2, overlayData.first, GeneralPrefs.slotTopLeft2 ?: OverlayType.entries.first())
        SlotHelper.updateSummary(topRight1, overlayData.first, GeneralPrefs.slotTopRight1 ?: OverlayType.entries.first())
        SlotHelper.updateSummary(topRight2, overlayData.first, GeneralPrefs.slotTopRight2 ?: OverlayType.entries.first())

        // Update Bottom Row
        SlotHelper.updateSummary(bottomLeft1, overlayData.first, GeneralPrefs.slotBottomLeft1 ?: OverlayType.entries.first())
        SlotHelper.updateSummary(bottomLeft2, overlayData.first, GeneralPrefs.slotBottomLeft2 ?: OverlayType.entries.first())
        SlotHelper.updateSummary(bottomRight1, overlayData.first, GeneralPrefs.slotBottomRight1 ?: OverlayType.entries.first())
        SlotHelper.updateSummary(bottomRight2, overlayData.first, GeneralPrefs.slotBottomRight2 ?: OverlayType.entries.first())

        val slotPrefs = SlotHelper.slotPrefs(requireContext())

        // Build lists for Top Row
        SlotHelper.buildOverlayList(topLeft1, overlayData.first, overlayData.second, slotPrefs)
        SlotHelper.buildOverlayList(topLeft2, overlayData.first, overlayData.second, slotPrefs)
        SlotHelper.buildOverlayList(topRight1, overlayData.first, overlayData.second, slotPrefs)
        SlotHelper.buildOverlayList(topRight2, overlayData.first, overlayData.second, slotPrefs)
        
        // Build lists for Bottom Row
        SlotHelper.buildOverlayList(bottomLeft1, overlayData.first, overlayData.second, slotPrefs)
        SlotHelper.buildOverlayList(bottomLeft2, overlayData.first, overlayData.second, slotPrefs)
        SlotHelper.buildOverlayList(bottomRight1, overlayData.first, overlayData.second, slotPrefs)
        SlotHelper.buildOverlayList(bottomRight2, overlayData.first, overlayData.second, slotPrefs)
        
        // Update dual preferences after list preferences have been updated
        setupDualDropdowns()
    }
}
