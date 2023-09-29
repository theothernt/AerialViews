package com.neilturner.aerialviews.utils

import android.content.Context
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.models.OverlayType
import com.neilturner.aerialviews.models.prefs.InterfacePrefs

object SlotHelper {

    fun updateSummary(list: ListPreference?, summaryList: Array<String>, slot: OverlayType) {
        val index = OverlayType.valueOf(slot.toString()).ordinal
        val summary = summaryList[index]
        list?.summary = summary
    }

    fun updateDropDown(list: ListPreference?, slotEntries: Array<String>, slotValues: Array<String>, slotPrefs: List<Triple<OverlayType, String, String>>) {
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

    fun updateSlot(prefScreen: PreferenceScreen, slotName: String) {
        val currentPrefs = currentPrefs()
        val slotPref = currentPrefs.find { it.second == slotName }?.first

        currentPrefs.forEach {
            if (it.second == slotName) {
                return@forEach
            }

            if (it.first == slotPref) {
                val pref = prefScreen.findPreference<ListPreference>(it.second)
                pref?.value = OverlayType.EMPTY.toString()
            }
        }
    }

    fun currentPrefs(): List<Triple<OverlayType, String, String>> {
        val slotPrefs = mutableListOf<Triple<OverlayType, String, String>>()
        slotPrefs.add(Triple(InterfacePrefs.slotBottomLeft1, "slot_bottom_left1", "Bottom Left, Slot 1"))
        slotPrefs.add(Triple(InterfacePrefs.slotBottomLeft2, "slot_bottom_left2", "Bottom Left, Slot 2"))
        slotPrefs.add(Triple(InterfacePrefs.slotBottomRight1, "slot_bottom_right1", "Bottom Right, Slot 1"))
        slotPrefs.add(Triple(InterfacePrefs.slotBottomRight2, "slot_bottom_right2", "Bottom Right, Slot 2"))
        return slotPrefs
    }

    fun entriesAndValues(context: Context): Pair<Array<String>, Array<String>> {
        val res = context.resources!!
        val entries = res.getStringArray(R.array.slot_entries) // Empty, Clock, etc
        val values = res.getStringArray(R.array.slot_values) // EMPTY, CLOCK, etc
        return Pair(entries, values)
    }
}