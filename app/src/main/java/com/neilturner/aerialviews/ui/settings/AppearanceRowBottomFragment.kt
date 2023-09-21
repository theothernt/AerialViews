package com.neilturner.aerialviews.ui.settings

import android.os.Bundle
import androidx.preference.ListPreference
import androidx.preference.PreferenceFragmentCompat
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.models.prefs.InterfacePrefs
import com.neilturner.aerialviews.models.videos.SlotType

class AppearanceRowBottomFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings_appearance_row_bottom, rootKey)

        updateSlots()
    }

    private fun updateSlots() {
        val bottomLeft1 = preferenceScreen.findPreference<ListPreference>("slot_bottom_left1")
        val bottomLeft2 = preferenceScreen.findPreference<ListPreference>("slot_bottom_left2")
        val bottomRight1 = preferenceScreen.findPreference<ListPreference>("slot_bottom_right1")
        val bottomRight2 = preferenceScreen.findPreference<ListPreference>("slot_bottom_right2")

        val res = context?.resources!!
        val slotEntries = res.getStringArray(R.array.slot_entries) // Empty, Clock, etc
        val slotValues = res.getStringArray(R.array.slot_values) // Empty, Clock, etc

        updateSlotSummary(bottomLeft1, slotEntries, InterfacePrefs.slotBottomLeft1)
        updateSlotSummary(bottomLeft2, slotEntries, InterfacePrefs.slotBottomLeft2)
        updateSlotSummary(bottomRight1, slotEntries, InterfacePrefs.slotBottomRight1)
        updateSlotSummary(bottomRight2, slotEntries, InterfacePrefs.slotBottomRight2)

        // Given a list of slots types (entries, values)
        // Find which slot type is used in which slot
        // If slot type is used, get slot name (eg. Bottom Left Slot 1)

        val slotPrefs = mutableListOf<Pair<SlotType, String>>()
        slotPrefs.add(Pair(InterfacePrefs.slotBottomLeft1, "Bottom Left Slot 1"))
        slotPrefs.add(Pair(InterfacePrefs.slotBottomLeft2, "Bottom Left Slot 2"))
        slotPrefs.add(Pair(InterfacePrefs.slotBottomRight1, "Bottom Right Slot 1"))
        slotPrefs.add(Pair(InterfacePrefs.slotBottomRight2, "Bottom Right Slot 2"))

        updateSlotList(bottomLeft1, slotEntries, slotValues, slotPrefs)

        // slotPrefs.find { it -> it.first == SlotType.EMPTY }

//        val entries = slotEntries.toMutableList()
//        val values = slotValues.toMutableList()
//
//        bottomLeft1?.entries = slotEntries
//        bottomLeft1?.entryValues = slotValues

    }

    private fun updateSlotSummary(list: ListPreference?, summaryList: Array<String>, slot: SlotType) {
        val index = SlotType.valueOf(slot.toString()).ordinal
        val summary = summaryList[index]
        list?.summary = summary
    }

    private fun updateSlotList(list: ListPreference?, slotEntries: Array<String>, slotValues: Array<String>, slotPrefs: List<Pair<SlotType, String>>) {
        slotValues.forEachIndexed { index, value ->
            if (value == SlotType.EMPTY.toString()) {
                return@forEachIndexed
            }

            val found = slotPrefs.find { it.first.toString() == value }
            if (found != null) {
                slotEntries[index] += " (${found.second})"
            }
        }

        list?.entries = slotEntries
        list?.entryValues = slotValues
    }
}
