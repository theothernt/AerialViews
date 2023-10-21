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

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> findOverlay(clazz: KClass<T>): T? {
        return overlays
            .filterNotNull()
            .find { it::class == clazz } as T?
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
    fun buildOverlaysAndIds(root: VideoViewBinding): OverlayIds {
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

        findOverlay(AltTextClock::class)?.apply {
            updateFormat(prefs.clockFormat)
        }

        findOverlay(TextDate::class)?.apply {
            updateFormat(prefs.dateFormat, prefs.dateCustom)
        }

        val bottomRow = buildReferenceIds(
            root.emptyView1,
            overlays[0],
            overlays[1],
            root.emptyView2,
            overlays[2],
            overlays[3]
        )

        val topRow = buildReferenceIds(
            overlays[4],
            overlays[5],
            root.emptyView3,
            overlays[6],
            overlays[7],
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
