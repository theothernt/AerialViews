package com.neilturner.aerialviews.utils

import android.content.Context
import android.view.View
import androidx.constraintlayout.helper.widget.Flow
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.models.enums.OverlayType
import com.neilturner.aerialviews.ui.overlays.AltTextClock
import com.neilturner.aerialviews.ui.overlays.TextLocation

class OverlayHelper(private val context: Context) {

    //private var locationData: Pair<String, Map<Int, String>>

    fun entriesAndValues(): Pair<Array<String>, Array<String>> {
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
        overlays.forEach {
            if (it != null && it.first != OverlayType.EMPTY) {
                val overlay = getOverlay(it.first)
                if (overlay != null) {
                    flow.addView(overlay)
                }
            }
        }
    }

    private fun getOverlay(type: OverlayType): View? {
        return when (type) {
            OverlayType.CLOCK -> AltTextClock(context)
            OverlayType.LOCATION -> TextLocation(context)
            else -> null
        }
    }
}