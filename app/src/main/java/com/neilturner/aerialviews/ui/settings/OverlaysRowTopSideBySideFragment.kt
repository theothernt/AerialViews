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

class OverlaysRowTopSideBySideFragment :
    MenuStateFragment(),
    SharedPreferences.OnSharedPreferenceChangeListener {
    
    private lateinit var leftSlotsPreference: TwoListPreferenceSideBySide
    private lateinit var rightSlotsPreference: TwoListPreferenceSideBySide
    
    // Individual ListPreferences that will be hidden but still functional
    private lateinit var topLeft1: ListPreference
    private lateinit var topLeft2: ListPreference
    private lateinit var topRight1: ListPreference
    private lateinit var topRight2: ListPreference

    override fun onCreatePreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        setPreferencesFromResource(R.xml.settings_overlays_row_top_side_by_side, rootKey)
        
        // Find our custom preferences
        leftSlotsPreference = preferenceScreen.findPreference("left_slots_top_row")!!
        rightSlotsPreference = preferenceScreen.findPreference("right_slots_top_row")!!
        
        // Create hidden individual ListPreferences
        createHiddenListPreferences()
        
        // Set up the side-by-side preferences
        setupSideBySidePreferences()
    }

    override fun onResume() {
        super.onResume()
        FirebaseHelper.logScreenView("Top Row (Side by Side)", this)
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
    
    private fun createHiddenListPreferences() {
        // Create ListPreferences that mirror the original ones but are hidden
        topLeft1 = ListPreference(requireContext()).apply {
            key = "slot_top_left1"
            title = getString(R.string.appearance_slot1_title)
            summary = getString(R.string.appearance_slot_summary)
            isVisible = false
        }
        
        topLeft2 = ListPreference(requireContext()).apply {
            key = "slot_top_left2"
            title = getString(R.string.appearance_slot2_title)
            summary = getString(R.string.appearance_slot_summary)
            isVisible = false
        }
        
        topRight1 = ListPreference(requireContext()).apply {
            key = "slot_top_right1"
            title = getString(R.string.appearance_slot1_title)
            summary = getString(R.string.appearance_slot_summary)
            isVisible = false
        }
        
        topRight2 = ListPreference(requireContext()).apply {
            key = "slot_top_right2"
            title = getString(R.string.appearance_slot2_title)
            summary = getString(R.string.appearance_slot_summary)
            isVisible = false
        }
        
        // Add them to the preference screen (they will be invisible)
        preferenceScreen.addPreference(topLeft1)
        preferenceScreen.addPreference(topLeft2)
        preferenceScreen.addPreference(topRight1)
        preferenceScreen.addPreference(topRight2)
    }
    
    private fun setupSideBySidePreferences() {
        // Set up left slots (topLeft2 and topLeft1)
        leftSlotsPreference.setLeftPreference(topLeft2)
        leftSlotsPreference.setRightPreference(topLeft1)
        
        // Set up right slots (topRight2 and topRight1)
        rightSlotsPreference.setLeftPreference(topRight2)
        rightSlotsPreference.setRightPreference(topRight1)
    }

    private fun updateDropDownAndSummary() {
        val overlayData = SlotHelper.entriesAndValues(requireContext())

        // Update summaries for individual preferences
        SlotHelper.updateSummary(topLeft1, overlayData.first, GeneralPrefs.slotTopLeft1 ?: OverlayType.entries.first())
        SlotHelper.updateSummary(topLeft2, overlayData.first, GeneralPrefs.slotTopLeft2 ?: OverlayType.entries.first())
        SlotHelper.updateSummary(topRight1, overlayData.first, GeneralPrefs.slotTopRight1 ?: OverlayType.entries.first())
        SlotHelper.updateSummary(topRight2, overlayData.first, GeneralPrefs.slotTopRight2 ?: OverlayType.entries.first())

        val slotPrefs = SlotHelper.slotPrefs(requireContext())

        // Build overlay lists for individual preferences
        SlotHelper.buildOverlayList(topLeft1, overlayData.first, overlayData.second, slotPrefs)
        SlotHelper.buildOverlayList(topLeft2, overlayData.first, overlayData.second, slotPrefs)
        SlotHelper.buildOverlayList(topRight1, overlayData.first, overlayData.second, slotPrefs)
        SlotHelper.buildOverlayList(topRight2, overlayData.first, overlayData.second, slotPrefs)
        
        // Update side-by-side preference summaries
        leftSlotsPreference.updateLeftSummary(topLeft2.summary?.toString() ?: "")
        leftSlotsPreference.updateRightSummary(topLeft1.summary?.toString() ?: "")
        rightSlotsPreference.updateLeftSummary(topRight2.summary?.toString() ?: "")
        rightSlotsPreference.updateRightSummary(topRight1.summary?.toString() ?: "")
    }
}
