package com.neilturner.aerialviews.utils

import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import android.view.View
import androidx.constraintlayout.helper.widget.Flow
import androidx.core.view.ViewCompat
import com.neilturner.aerialviews.databinding.VideoViewBinding
import com.neilturner.aerialviews.models.OverlayIds
import com.neilturner.aerialviews.models.enums.OverlayType
import com.neilturner.aerialviews.models.enums.SlotType
import com.neilturner.aerialviews.models.prefs.InterfacePrefs
import com.neilturner.aerialviews.ui.overlays.AltTextClock
import com.neilturner.aerialviews.ui.overlays.TextDate
import com.neilturner.aerialviews.ui.overlays.TextLocation
import kotlin.reflect.KClass

class OverlayHelper(private val context: Context, private val font: Typeface?, private val prefs: InterfacePrefs) {

    private var overlays = mutableListOf<View?>()
    private var alternateOverlays = false

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> findOverlay(clazz: KClass<T>): T? {
        return overlays
            .filterNotNull()
            .find { it::class == clazz } as T?
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
    fun buildOverlaysAndIds(root: VideoViewBinding): OverlayIds {
        val slots = SlotHelper.slotPrefs()
        for (type in SlotType.entries) {
            val slot = slots.find { it.second == type }
            val view = getOverlay(slot!!.first)
            view?.id = ViewCompat.generateViewId()
            overlays.add(view)
            if (view != null) {
                root.layout.addView(view)
            }
        }

        findOverlay(AltTextClock::class)?.apply {
            updateFormat(prefs.clockFormat)
        }

        findOverlay(TextDate::class)?.apply {
            updateFormat(prefs.dateFormat, prefs.dateCustom)
        }

        val bottomRow = buildReferenceIds(
            overlays[0],
            overlays[1],
            overlays[2],
            overlays[3],
            root.emptyView1,
            root.emptyView2
        )

        val topRow = buildReferenceIds(
            overlays[5],
            overlays[4],
            overlays[7],
            overlays[6],
            root.emptyView3,
            root.emptyView4
        )

        return OverlayIds(bottomRow.first, bottomRow.second, topRow.first, topRow.second)
    }

    // Figure out which IDs go where, add an empty view if needed
    private fun buildReferenceIds(left1: View?, left2: View?, right1: View?, right2: View?, empty1: View, empty2: View): Pair<List<Int>, List<Int>> {
        // Reverse order of views due to how the Flow control display order
        val leftIds = listOfNotNull(empty2.id, left2?.id, left1?.id)
        val rightIds = listOfNotNull(empty1.id, right2?.id, right1?.id)
        return Pair(leftIds, rightIds)
    }

    private fun getOverlay(type: OverlayType): View? {
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
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, prefs.dateSize.toFloat())
                    typeface = font
                }
            }
            else -> {
                return null
            }
        }
    }
}
