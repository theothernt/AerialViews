package com.neilturner.aerialviews.utils

import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import android.view.View
import androidx.constraintlayout.helper.widget.Flow
import androidx.core.view.ViewCompat
import com.neilturner.aerialviews.databinding.VideoViewBinding
import com.neilturner.aerialviews.models.enums.OverlayType
import com.neilturner.aerialviews.models.enums.SlotType
import com.neilturner.aerialviews.models.prefs.InterfacePrefs
import com.neilturner.aerialviews.ui.overlays.AltTextClock
import com.neilturner.aerialviews.ui.overlays.TextDate
import com.neilturner.aerialviews.ui.overlays.TextLocation

object OverlayHelper {

    private var overlays = mutableListOf<Pair<OverlayType, View?>>()
    private var alternateOverlays = false

    fun findOverlay(type: OverlayType): View? {
        return overlays.find { it.first == type }?.second
    }

    // Assign IDs/Overlays to correct Flow - or alternate
    fun assignOverlaysAndIds(leftFlow: Flow, rightFlow: Flow, leftIds: List<Int>, rightIds: List<Int>, shouldAlternateOverlays: Boolean) {
        if (!alternateOverlays) {
            leftFlow.referencedIds = leftIds.toIntArray()
            rightFlow.referencedIds = rightIds.toIntArray()
        } else {
            leftFlow.referencedIds = rightIds.toIntArray()
            rightFlow.referencedIds = leftIds.toIntArray()
        }
        leftFlow.requestLayout()
        rightFlow.requestLayout()

        if (shouldAlternateOverlays) {
            alternateOverlays = !alternateOverlays
        }
    }

    // Initialise chosen overlays, add them to the layout then return IDs for later use
    fun buildOverlaysAndIds(context: Context, root: VideoViewBinding, typeface: Typeface?, prefs: InterfacePrefs): Pair<List<Int>, List<Int>> {
        val slots = SlotHelper.slotPrefs()
        for (type in SlotType.entries) {
            val slot = slots.find { it.second == type }
            val view = getOverlay(context, typeface, prefs, slot!!.first)
            view?.id = ViewCompat.generateViewId()
            overlays.add(Pair(slot.first, view))
            if (view != null) {
                root.layout.addView(view)
            }
        }

        val bottomEmptyView = root.emptyViewBottom
        // val topEmptyView = TextView(context)
        return buildReferenceIds(
            overlays[0].second,
            overlays[1].second,
            overlays[2].second,
            overlays[3].second,
            bottomEmptyView
        )
    }

    // Figure out which IDs go where, add an empty view if needed
    private fun buildReferenceIds(left1: View?, left2: View?, right1: View?, right2: View?, empty: View): Pair<List<Int>, List<Int>> {
        var leftIds = listOfNotNull(left1?.id, left2?.id)
        var rightIds = listOfNotNull(right1?.id, right2?.id)

        // If one side (Flow) is empty, add an invisible view
        // Fixes a bug that causes layout issues
        if (leftIds.isEmpty() && rightIds.isNotEmpty()) {
            empty.visibility = View.INVISIBLE
            leftIds = listOf(empty.id)
        }

        if (leftIds.isNotEmpty() && rightIds.isEmpty()) {
            empty.visibility = View.INVISIBLE
            rightIds = listOf(empty.id)
        }
        return Pair(leftIds, rightIds)
    }

    private fun getOverlay(context: Context, font: Typeface?, prefs: InterfacePrefs, type: OverlayType): View? {
        when (type) {
            OverlayType.CLOCK -> {
                return AltTextClock(context).apply {
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, prefs.clockSize.toFloat())
                    typeface = font
                }
            }
            OverlayType.LOCATION -> {
                return TextLocation(context).apply {
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, prefs.locationSize.toFloat())
                    typeface = font
                }
            }
            OverlayType.DATE -> {
                return TextDate(context).apply {
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, prefs.locationSize.toFloat())
                    typeface = font
                }
            }
            else -> {
                return null
            }
        }
    }
}
