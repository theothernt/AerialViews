package com.neilturner.aerialviews.utils

import android.content.Context
import android.util.TypedValue
import android.view.View
import androidx.constraintlayout.helper.widget.Flow
import androidx.core.view.ViewCompat
import com.neilturner.aerialviews.databinding.OverlayViewBinding
import com.neilturner.aerialviews.models.OverlayIds
import com.neilturner.aerialviews.models.enums.OverlayType
import com.neilturner.aerialviews.models.enums.SlotType
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.ui.overlays.AltTextClock
import com.neilturner.aerialviews.ui.overlays.TextDate
import com.neilturner.aerialviews.ui.overlays.TextLocation
import com.neilturner.aerialviews.ui.overlays.TextMessage

class OverlayHelper(private val context: Context, private val prefs: GeneralPrefs) {

    var overlays = mutableListOf<View?>()

    inline fun <reified T : View> findOverlay(): List<T> {
        return overlays.filterIsInstance<T>()
    }

    // Assign IDs/Overlays to correct Flow - or alternate
    fun assignOverlaysAndIds(leftFlow: Flow, rightFlow: Flow, leftIds: List<Int>, rightIds: List<Int>, alternateOverlays: Boolean) {
        if (!alternateOverlays) {
            leftFlow.referencedIds = leftIds.toIntArray()
            rightFlow.referencedIds = rightIds.toIntArray()
        } else {
            leftFlow.referencedIds = rightIds.toIntArray()
            rightFlow.referencedIds = leftIds.toIntArray()
        }
        leftFlow.requestLayout()
        rightFlow.requestLayout()
    }

    // Initialise chosen overlays, add them to the layout then return IDs for later use
    fun buildOverlaysAndIds(root: OverlayViewBinding): OverlayIds {
        val slots = SlotHelper.slotPrefs(context)
        for (type in SlotType.entries) {
            val slot = slots.find { it.type == type }
            val view = getOverlay(slot!!.pref)
            view?.id = ViewCompat.generateViewId()
            overlays.add(view)
            if (view != null) {
                root.layout.addView(view)
            }
        }

        findOverlay<AltTextClock>().forEach {
            it.updateFormat(prefs.clockFormat)
        }

        findOverlay<TextDate>().forEach {
            it.updateFormat(prefs.dateFormat, prefs.dateCustom)
        }

        findOverlay<TextMessage>().forEach {
            if (it.type == OverlayType.MESSAGE1) {
                it.updateMessage(prefs.messageLine1)
            } else {
                it.updateMessage(prefs.messageLine2)
            }
        }

        val bottomRow = buildReferenceIds(
            root.emptyView1,
            overlays[1],
            overlays[0],
            root.emptyView2,
            overlays[3],
            overlays[2]
        )

        val topRow = buildReferenceIds(
            overlays[5],
            overlays[4],
            root.emptyView3,
            overlays[7],
            overlays[6],
            root.emptyView4
        )

        return OverlayIds(bottomRow.first, bottomRow.second, topRow.first, topRow.second)
    }

    // Figure out which IDs go where, add an empty view if needed
    private fun buildReferenceIds(view1: View?, view2: View?, view3: View?, view4: View?, view5: View?, view6: View?): Pair<List<Int>, List<Int>> {
        // Reverse order of views due to how the Flow control display order
        val leftIds = listOfNotNull(view1?.id, view2?.id, view3?.id)
        val rightIds = listOfNotNull(view4?.id, view5?.id, view6?.id)
        return Pair(leftIds, rightIds)
    }

    private fun getOverlay(type: OverlayType): View? {
        return when (type) {
            OverlayType.CLOCK -> AltTextClock(context).apply {
                setTextSize(TypedValue.COMPLEX_UNIT_SP, prefs.clockSize.toFloat())
                typeface = FontHelper.getTypeface(context, GeneralPrefs.fontTypeface, GeneralPrefs.clockWeight)
            }
            OverlayType.LOCATION -> TextLocation(context).apply {
                setTextSize(TypedValue.COMPLEX_UNIT_SP, prefs.locationSize.toFloat())
                typeface = FontHelper.getTypeface(context, GeneralPrefs.fontTypeface, GeneralPrefs.locationWeight)
            }
            OverlayType.DATE -> TextDate(context).apply {
                setTextSize(TypedValue.COMPLEX_UNIT_SP, prefs.dateSize.toFloat())
                typeface = FontHelper.getTypeface(context, GeneralPrefs.fontTypeface, GeneralPrefs.dateWeight)
            }
            OverlayType.MESSAGE1,
            OverlayType.MESSAGE2 -> TextMessage(context).apply {
                setTextSize(TypedValue.COMPLEX_UNIT_SP, prefs.messageSize.toFloat())
                typeface = FontHelper.getTypeface(context, GeneralPrefs.fontTypeface, GeneralPrefs.messageWeight)
                this.type = type
            }
            else -> return null
        }
    }
}
