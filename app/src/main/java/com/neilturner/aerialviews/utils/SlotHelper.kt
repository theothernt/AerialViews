package com.neilturner.aerialviews.utils

import android.content.Context
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.models.enums.OverlayType
import com.neilturner.aerialviews.models.enums.SlotType
import com.neilturner.aerialviews.models.prefs.InterfacePrefs

object SlotHelper {

    // Update summary to show assigned overlay name
    fun updateSummary(list: ListPreference?, summaryList: Array<String>, slot: OverlayType) {
        // should show - Message Line 1
        // and not MESSAGE 1 or Message Line 1 (Slot name) etc
        val index = OverlayType.valueOf(slot.toString()).ordinal
        val summary = summaryList[index]
        list?.summary = summary
    }

    // Build list of overlays adding slot name if already assigned
    fun buildOverlayList(list: ListPreference?, slotEntries: Array<String>, slotValues: Array<String>, slotPrefs: List<Triple<OverlayType, SlotType, String>>) {
        val entries = slotEntries.toMutableList()
        slotValues.forEachIndexed { index, value ->
            if (value == OverlayType.EMPTY.toString()) {
                return@forEachIndexed
            }

            val found = slotPrefs.find { it.first.toString() == value }
            if (found != null) {
                entries[index] += " (${found.third})"
            }
        }

        list?.entries = entries.toTypedArray()
        list?.entryValues = slotValues
    }

    // If overlay is already assigned, remove it from previous slot
    fun removeDuplicateOverlays(prefScreen: PreferenceScreen, slotName: String) {
        val currentPrefs = slotPrefs()
        val slotPref = currentPrefs.find { it.second.toString().lowercase() == slotName }?.first

        currentPrefs.forEach {
            if (it.second.toString().lowercase() == slotName) {
                return@forEach
            }

            if (it.first == slotPref) {
                val pref = prefScreen.findPreference<ListPreference>(it.second.toString().lowercase())
                pref?.value = OverlayType.EMPTY.toString()
            }
        }
    }

    fun entriesAndValues(context: Context): Pair<Array<String>, Array<String>> {
        val res = context.resources!!
        val entries = res.getStringArray(R.array.slot_entries) // Empty, Clock, etc
        val values = res.getStringArray(R.array.slot_values) // EMPTY, CLOCK, etc
        return Pair(entries, values)
    }

    fun slotPrefs(): List<Triple<OverlayType, SlotType, String>> {
        val slotPrefs = mutableListOf<Triple<OverlayType, SlotType, String>>()
        slotPrefs.add(Triple(InterfacePrefs.slotBottomLeft1, SlotType.SLOT_BOTTOM_LEFT1, "Bottom Left, Slot 1"))
        slotPrefs.add(Triple(InterfacePrefs.slotBottomLeft2, SlotType.SLOT_BOTTOM_LEFT2, "Bottom Left, Slot 2"))
        slotPrefs.add(Triple(InterfacePrefs.slotBottomRight1, SlotType.SLOT_BOTTOM_RIGHT1, "Bottom Right, Slot 1"))
        slotPrefs.add(Triple(InterfacePrefs.slotBottomRight2, SlotType.SLOT_BOTTOM_RIGHT2, "Bottom Right, Slot 2"))
        return slotPrefs
    }
}
