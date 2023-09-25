package com.neilturner.aerialviews.ui.settings

import android.content.SharedPreferences
import android.os.Bundle
import androidx.preference.ListPreference
import androidx.preference.PreferenceFragmentCompat
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.models.prefs.InterfacePrefs
import com.neilturner.aerialviews.models.SlotType

class AppearanceRowBottomFragment :
    PreferenceFragmentCompat(),
    SharedPreferences.OnSharedPreferenceChangeListener {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings_appearance_row_bottom, rootKey)
        preferenceManager.sharedPreferences?.registerOnSharedPreferenceChangeListener(this)

        updateSlots()
    }

    override fun onDestroy() {
        preferenceManager.sharedPreferences?.unregisterOnSharedPreferenceChangeListener(this)
        super.onDestroy()
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String) {
        if (key.contains("slot_", false)) {
            updateSlot(key)
            updateSlots()
        }
    }

    private fun updateSlot(slotName: String) {
        val slotPrefs = buildSlotPrefs()
        val slotPref = slotPrefs.find { it.second == slotName }?.first

        slotPrefs.forEach {
            if (it.second == slotName) {
                return@forEach
            }

            if (it.first == slotPref) {
                val pref = preferenceScreen.findPreference<ListPreference>(it.second)
                pref?.value = SlotType.EMPTY.toString()
            }

        }
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

        val slotPrefs = buildSlotPrefs()

        updateSlotList(bottomLeft1, slotEntries, slotValues, slotPrefs)
        updateSlotList(bottomLeft2, slotEntries, slotValues, slotPrefs)
        updateSlotList(bottomRight1, slotEntries, slotValues, slotPrefs)
        updateSlotList(bottomRight2, slotEntries, slotValues, slotPrefs)
    }

    private fun buildSlotPrefs(): List<Triple<SlotType, String, String>> {
        val slotPrefs = mutableListOf<Triple<SlotType, String, String>>()
        slotPrefs.add(Triple(InterfacePrefs.slotBottomLeft1, "slot_bottom_left1", "Bottom Left, Slot 1"))
        slotPrefs.add(Triple(InterfacePrefs.slotBottomLeft2, "slot_bottom_left2","Bottom Left, Slot 2"))
        slotPrefs.add(Triple(InterfacePrefs.slotBottomRight1, "slot_bottom_right1", "Bottom Right, Slot 1"))
        slotPrefs.add(Triple(InterfacePrefs.slotBottomRight2, "slot_bottom_right2", "Bottom Right, Slot 2"))
        return slotPrefs
    }
    private fun updateSlotSummary(list: ListPreference?, summaryList: Array<String>, slot: SlotType) {
        val index = SlotType.valueOf(slot.toString()).ordinal
        val summary = summaryList[index]
        list?.summary = summary
    }

    private fun updateSlotList(list: ListPreference?, slotEntries: Array<String>, slotValues: Array<String>, slotPrefs: List<Triple<SlotType, String, String>>) {
        val entries = slotEntries.toMutableList()
        slotValues.forEachIndexed { index, value ->
            if (value == SlotType.EMPTY.toString()) {
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
}
