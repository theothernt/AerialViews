package com.neilturner.aerialviews.ui.settings

import android.os.Bundle
import androidx.preference.ListPreference
import androidx.preference.PreferenceFragmentCompat
import com.chibatching.kotpref.pref.AbstractPref
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.models.prefs.InterfacePrefs
import com.neilturner.aerialviews.models.videos.SlotType

class AppearanceRowBottomFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings_appearance_row_bottom, rootKey)

        updateSlot()
    }

    private fun updateSlot() {
        val bottomLeft1 = preferenceScreen.findPreference<ListPreference>("slot_bottom_left1")
        val bottomLeft2 = preferenceScreen.findPreference<ListPreference>("slot_bottom_left2")
        val bottomRight1 = preferenceScreen.findPreference<ListPreference>("slot_bottom_right1")
        val bottomRight2 = preferenceScreen.findPreference<ListPreference>("slot_bottom_right2")

        val res = context?.resources!!
        val summaryList = res.getStringArray(R.array.slot_summary_entries) // Empty, Clock, etc

        updateSlotSummary(bottomLeft1, summaryList, InterfacePrefs.slotBottomLeft1)
        updateSlotSummary(bottomLeft2, summaryList, InterfacePrefs.slotBottomLeft2)
        updateSlotSummary(bottomRight1, summaryList, InterfacePrefs.slotBottomRight1)
        updateSlotSummary(bottomRight2, summaryList, InterfacePrefs.slotBottomRight2)
    }

    private fun updateSlotSummary(list: ListPreference?, summaryList: Array<String>, slotPref: AbstractPref<SlotType>) {
        val index = SlotType.valueOf(slotPref.toString()).ordinal
        val summary = summaryList[index]
        list?.summary = summary
    }
    private fun updateSlotList() {


    }
}
