package com.neilturner.aerialviews.utils

import android.content.Context
import androidx.core.content.edit
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.models.SlotPref
import com.neilturner.aerialviews.models.enums.OverlayType
import com.neilturner.aerialviews.models.enums.SlotType
import com.neilturner.aerialviews.models.prefs.GeneralPrefs

object SlotHelper {
    // Update summary to show assigned overlay name
    fun updateSummary(
        list: ListPreference?,
        summaryList: Array<String>,
        slot: OverlayType,
    ) {
        // should show - Location
        // and not LOCATION or Location (Slot name) etc
        val index = OverlayType.valueOf(slot.toString()).ordinal
        val summary = summaryList.getOrNull(index) ?: summaryList.first()
        list?.summary = summary
    }

    // Build list of overlays adding slot name if already assigned
    fun buildOverlayList(
        list: ListPreference?,
        slotEntries: Array<String>,
        slotValues: Array<String>,
        slotPrefs: List<SlotPref>,
    ) {
        val entries = slotEntries.toMutableList()
        slotValues.forEachIndexed { index, value ->
            if (value == OverlayType.EMPTY.toString()) {
                return@forEachIndexed
            }

            val found = slotPrefs.find { it.pref.toString() == value }
            if (found != null) {
                entries[index] += " (${found.label})"
            }
        }

        list?.entries = entries.toTypedArray()
        list?.entryValues = slotValues
    }

    // If overlay is already assigned to another slot, remove it
    fun removeDuplicateOverlays(
        prefScreen: PreferenceScreen,
        slotName: String,
    ) {
        val allSlots = slotPrefs(prefScreen.context)
        val currentSlot = allSlots.find { it.type.toString().lowercase() == slotName }

        allSlots.forEach {
            // Ignore current slot
            if (it.type.toString().lowercase() == slotName) {
                return@forEach
            }

            // If overlay found in another slot
            if (it.pref == currentSlot?.pref) {
                // If dropdown for slot is on-screen, update it
                val pref = prefScreen.findPreference<ListPreference>(it.type.toString().lowercase())

                if (pref == null) {
                    // not on-screen, update pref only
                    GeneralPrefs.preferences
                        .edit {
                            putString(it.type.toString().lowercase(), OverlayType.EMPTY.toString())
                        }
                } else {
                    // on-screen, control + pref are updated
                    pref.value = OverlayType.EMPTY.toString()
                }
            }
        }
    }

    fun entriesAndValues(context: Context): Pair<Array<String>, Array<String>> {
        // Should be Map<T>
        // Create generic func to return res?
        val res = context.resources!!
        val entries = res.getStringArray(R.array.slot_entries) // Empty, Clock, etc
        val values = res.getStringArray(R.array.slot_values) // EMPTY, CLOCK, etc
        return Pair(entries, values)
    }

    fun slotPrefs(context: Context): List<SlotPref> {
        val slotPrefs = mutableListOf<SlotPref>()
        val res = context.resources!!
        slotPrefs.add(
            SlotPref(
                GeneralPrefs.slotBottomLeft1 ?: OverlayType.entries.first(),
                SlotType.SLOT_BOTTOM_LEFT1,
                res.getString(R.string.appearance_bottom_left_lower_slot),
            ),
        )
        slotPrefs.add(
            SlotPref(
                GeneralPrefs.slotBottomLeft2 ?: OverlayType.entries.first(),
                SlotType.SLOT_BOTTOM_LEFT2,
                res.getString(R.string.appearance_bottom_left_upper_slot),
            ),
        )
        slotPrefs.add(
            SlotPref(
                GeneralPrefs.slotBottomRight1 ?: OverlayType.entries.first(),
                SlotType.SLOT_BOTTOM_RIGHT1,
                res.getString(R.string.appearance_bottom_right_lower_slot),
            ),
        )
        slotPrefs.add(
            SlotPref(
                GeneralPrefs.slotBottomRight2 ?: OverlayType.entries.first(),
                SlotType.SLOT_BOTTOM_RIGHT2,
                res.getString(R.string.appearance_bottom_right_upper_slot),
            ),
        )
        slotPrefs.add(
            SlotPref(
                GeneralPrefs.slotTopLeft1 ?: OverlayType.entries.first(),
                SlotType.SLOT_TOP_LEFT1,
                res.getString(R.string.appearance_top_left_lower_slot),
            ),
        )
        slotPrefs.add(
            SlotPref(
                GeneralPrefs.slotTopLeft2 ?: OverlayType.entries.first(),
                SlotType.SLOT_TOP_LEFT2,
                res.getString(R.string.appearance_top_left_upper_slot),
            ),
        )
        slotPrefs.add(
            SlotPref(
                GeneralPrefs.slotTopRight1 ?: OverlayType.entries.first(),
                SlotType.SLOT_TOP_RIGHT1,
                res.getString(R.string.appearance_top_right_lower_slot),
            ),
        )
        slotPrefs.add(
            SlotPref(
                GeneralPrefs.slotTopRight2 ?: OverlayType.entries.first(),
                SlotType.SLOT_TOP_RIGHT2,
                res.getString(R.string.appearance_top_right_upper_slot),
            ),
        )
        return slotPrefs
    }
}
