package com.neilturner.aerialviews.utils

import android.content.Context
import androidx.constraintlayout.helper.widget.Flow
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.models.enums.OverlayType

object OverlayHelper {

    fun entriesAndValues(context: Context): Pair<Array<String>, Array<String>> {
        val res = context.resources!!
        val entries = res.getStringArray(R.array.slot_entries) // Empty, Clock, etc
        val values = res.getStringArray(R.array.slot_values) // EMPTY, CLOCK, etc
        return Pair(entries, values)
    }

    fun loadOverlays(flow: Flow, slot1: String, slot2: String) {
        val prefs = SlotHelper.slotPrefs()
        val overlays = mutableListOf<Triple<OverlayType, String, String>?>()
        overlays.add(prefs.find { it.second == slot1 })
        overlays.add(prefs.find { it.second == slot2 })
        overlays.forEach { it ->
            if (it != null && it.first != OverlayType.EMPTY) {
                // val overlay = getOverlay(it.first)
                // flow.addView(overlay)
            }
        }
    }

//    private fun getOverlay(type: OverlayType): View {
//        return when (type) {
//            OverlayType.CLOCK -> TextView()
//            OverlayType.LOCATION -> TextView()
//            else -> TextView()
//        }
//    }
}