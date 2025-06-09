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

class OverlaysRowBottomSideBySideFragment :
    MenuStateFragment(),
    SharedPreferences.OnSharedPreferenceChangeListener {
    
    private lateinit var leftSlotsPreference: TwoListPreferenceSideBySide
    private lateinit var rightSlotsPreference: TwoListPreferenceSideBySide
    
    // Individual ListPreferences that will be hidden but still functional
    private lateinit var bottomLeft1: ListPreference
    private lateinit var bottomLeft2: ListPreference
    private lateinit var bottomRight1: ListPreference
    private lateinit var bottomRight2: ListPreference

    override fun onCreatePreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        setPreferencesFromResource(R.xml.settings_overlays_row_bottom_side_by_side, rootKey)
        
        // Find our custom preferences
        leftSlotsPreference = preferenceScreen.findPreference("left_slots_bottom_row")!!
        rightSlotsPreference = preferenceScreen.findPreference("right_slots_bottom_row")!!
        
        // Create hidden individual ListPreferences
        createHiddenListPreferences()
        
        // Set up the side-by-side preferences
        setupSideBySidePreferences()
    }

    override fun onResume() {
        super.onResume()
        FirebaseHelper.logScreenView("Bottom Row (Side by Side)", this)
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
        bottomLeft1 = ListPreference(requireContext()).apply {
            key = "slot_bottom_left1"
            title = getString(R.string.appearance_slot1_title)
            summary = getString(R.string.appearance_slot_summary)
            isVisible = false
        }
        
        bottomLeft2 = ListPreference(requireContext()).apply {
            key = "slot_bottom_left2"
            title = getString(R.string.appearance_slot2_title)
            summary = getString(R.string.appearance_slot_summary)
            isVisible = false
        }
        
        bottomRight1 = ListPreference(requireContext()).apply {
            key = "slot_bottom_right1"
            title = getString(R.string.appearance_slot1_title)
            summary = getString(R.string.appearance_slot_summary)
            isVisible = false
        }
        
        bottomRight2 = ListPreference(requireContext()).apply {
            key = "slot_bottom_right2"
            title = getString(R.string.appearance_slot2_title)
            summary = getString(R.string.appearance_slot_summary)
            isVisible = false
        }
        
        // Add them to the preference screen (they will be invisible)
        preferenceScreen.addPreference(bottomLeft1)
        preferenceScreen.addPreference(bottomLeft2)
        preferenceScreen.addPreference(bottomRight1)
        preferenceScreen.addPreference(bottomRight2)
    }
    
    private fun setupSideBySidePreferences() {
        // Set up left slots (bottomLeft2 and bottomLeft1)
        leftSlotsPreference.setLeftPreference(bottomLeft2)
        leftSlotsPreference.setRightPreference(bottomLeft1)
        
        // Set up right slots (bottomRight2 and bottomRight1)
        rightSlotsPreference.setLeftPreference(bottomRight2)
        rightSlotsPreference.setRightPreference(bottomRight1)
    }

    private fun updateDropDownAndSummary() {
        val overlayData = SlotHelper.entriesAndValues(requireContext())

        // Update summaries for individual preferences
        SlotHelper.updateSummary(bottomLeft1, overlayData.first, GeneralPrefs.slotBottomLeft1 ?: OverlayType.entries.first())
        SlotHelper.updateSummary(bottomLeft2, overlayData.first, GeneralPrefs.slotBottomLeft2 ?: OverlayType.entries.first())
        SlotHelper.updateSummary(bottomRight1, overlayData.first, GeneralPrefs.slotBottomRight1 ?: OverlayType.entries.first())
        SlotHelper.updateSummary(bottomRight2, overlayData.first, GeneralPrefs.slotBottomRight2 ?: OverlayType.entries.first())

        val slotPrefs = SlotHelper.slotPrefs(requireContext())

        // Build overlay lists for individual preferences
        SlotHelper.buildOverlayList(bottomLeft1, overlayData.first, overlayData.second, slotPrefs)
        SlotHelper.buildOverlayList(bottomLeft2, overlayData.first, overlayData.second, slotPrefs)
        SlotHelper.buildOverlayList(bottomRight1, overlayData.first, overlayData.second, slotPrefs)
        SlotHelper.buildOverlayList(bottomRight2, overlayData.first, overlayData.second, slotPrefs)
        
        // Update side-by-side preference summaries
        leftSlotsPreference.updateLeftSummary(bottomLeft2.summary?.toString() ?: "")
        leftSlotsPreference.updateRightSummary(bottomLeft1.summary?.toString() ?: "")
        rightSlotsPreference.updateLeftSummary(bottomRight2.summary?.toString() ?: "")
        rightSlotsPreference.updateRightSummary(bottomRight1.summary?.toString() ?: "")
    }
}
