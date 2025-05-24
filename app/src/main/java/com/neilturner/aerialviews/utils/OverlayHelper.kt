package com.neilturner.aerialviews.utils

import android.content.Context
import android.util.TypedValue
import android.view.View
import androidx.constraintlayout.helper.widget.Flow
import com.neilturner.aerialviews.databinding.OverlayViewBinding
import com.neilturner.aerialviews.models.OverlayIds
import com.neilturner.aerialviews.models.enums.OverlayType
import com.neilturner.aerialviews.models.enums.SlotType
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.ui.overlays.ClockOverlay
import com.neilturner.aerialviews.ui.overlays.DateOverlay
import com.neilturner.aerialviews.ui.overlays.LocationOverlay
import com.neilturner.aerialviews.ui.overlays.MessageOverlay
import com.neilturner.aerialviews.ui.overlays.NowPlayingOverlay
import com.neilturner.aerialviews.ui.overlays.WeatherOverlay

class OverlayHelper(
    private val context: Context,
    private val prefs: GeneralPrefs,
) {
    var overlays = mutableListOf<View?>()

    inline fun <reified T : View> findOverlay(): List<T> = overlays.filterIsInstance<T>()

    // Assign IDs/Overlays to correct Flow - or alternate
    fun assignOverlaysAndIds(
        leftFlow: Flow,
        rightFlow: Flow,
        leftIds: List<Int>,
        rightIds: List<Int>,
        alternateOverlays: Boolean,
    ) {
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
        // Get a list of slots + selected overlay (or empty)
        val slots = SlotHelper.slotPrefs(context)

        // For each slot type (top left 1, etc) - order matters
        for (type in SlotType.entries) {
            // For each slot, find a matching overlay, init then return it
            val slot = slots.find { it.type == type }
            val view = getOverlay(slot!!.pref)

            // Add ID to overlay for later use
            view?.id = View.generateViewId()

            // Add overlay to overlays view for later positioning
            overlays.add(view)
            if (view != null) {
                root.layout.addView(view)
            }
        }

        // For each overlay loaded, update its prefs
        findOverlay<ClockOverlay>().forEach {
            it.updateFormat(prefs.clockFormat)
        }

        findOverlay<DateOverlay>().forEach {
            it.updateFormat(prefs.dateFormat, prefs.dateCustom)
        }

        findOverlay<MessageOverlay>().forEach {
            if (it.type == OverlayType.MESSAGE1) {
                it.updateMessage(prefs.messageLine1)
            } else {
                it.updateMessage(prefs.messageLine2)
            }
        }

        findOverlay<NowPlayingOverlay>().forEach {
            if (it.type == OverlayType.MUSIC1) {
                it.updateFormat(prefs.nowPlayingLine1)
            } else {
                it.updateFormat(prefs.nowPlayingLine2)
            }
        }

//        findOverlay<TextWeather>().forEach {
//            if (it.type == OverlayType.WEATHER1) {
//                it.updateFormat("")
//            } else {
//                it.updateFormat("")
//            }
//        }

        // Create each row of overlays - the order of views matter
        val bottomRow =
            buildReferenceIds(
                root.emptyView1,
                overlays[1],
                overlays[0],
                root.emptyView2,
                overlays[3],
                overlays[2],
            )

        val topRow =
            buildReferenceIds(
                overlays[5],
                overlays[4],
                root.emptyView3,
                overlays[7],
                overlays[6],
                root.emptyView4,
            )

        return OverlayIds(bottomRow.first, bottomRow.second, topRow.first, topRow.second)
    }

    // Figure out which IDs go where, add an empty view if needed
    private fun buildReferenceIds(
        view1: View?,
        view2: View?,
        view3: View?,
        view4: View?,
        view5: View?,
        view6: View?,
    ): Pair<List<Int>, List<Int>> {
        // Reverse order of views due to how the Flow control display order
        // Use empty view if there are no overlays so that Flow layout is correct
        val leftIds = listOfNotNull(view1?.id, view2?.id, view3?.id)
        val rightIds = listOfNotNull(view4?.id, view5?.id, view6?.id)
        return Pair(leftIds, rightIds)
    }

    private fun getOverlay(overlay: OverlayType): View? {
        return when (overlay) {
            OverlayType.CLOCK ->
                ClockOverlay(context).apply {
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, prefs.clockSize.toFloat())
                    typeface = FontHelper.getTypeface(context, prefs.fontTypeface, prefs.clockWeight)
                }
            OverlayType.LOCATION ->
                LocationOverlay(context).apply {
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, prefs.descriptionSize.toFloat())
                    typeface = FontHelper.getTypeface(context, prefs.fontTypeface, prefs.descriptionWeight)
                }
            OverlayType.WEATHER1 ->
                WeatherOverlay(context).apply {
                    type = overlay
                    style(prefs.fontTypeface, prefs.weatherLine1Size.toFloat(), prefs.weatherLine1Weight)
                    // layout(prefs.weatherLine1)
                    // layout("CITY, TEMPERATURE, ICON, SUMMARY")
                    layout("TEMPERATURE, ICON, SUMMARY")
                }
//            OverlayType.WEATHER2 ->
//                WeatherOverlay(context).apply {
//                    style(prefs.fontTypeface, prefs.weatherLine2Size.toFloat(), prefs.weatherLine2Weight)
//                    // layout(prefs.weatherLine2)
//                    layout("SUMMARY")
//                }
            OverlayType.MUSIC1 ->
                NowPlayingOverlay(context).apply {
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, prefs.nowPlayingSize1.toFloat())
                    typeface = FontHelper.getTypeface(context, prefs.fontTypeface, prefs.nowPlayingWeight1)
                    type = overlay
                }
            OverlayType.MUSIC2 ->
                NowPlayingOverlay(context).apply {
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, prefs.nowPlayingSize2.toFloat())
                    typeface = FontHelper.getTypeface(context, prefs.fontTypeface, prefs.nowPlayingWeight2)
                    type = overlay
                }
            OverlayType.DATE ->
                DateOverlay(context).apply {
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, prefs.dateSize.toFloat())
                    typeface = FontHelper.getTypeface(context, prefs.fontTypeface, prefs.dateWeight)
                }
            OverlayType.MESSAGE1,
            OverlayType.MESSAGE2,
            ->
                MessageOverlay(context).apply {
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, prefs.messageSize.toFloat())
                    typeface = FontHelper.getTypeface(context, prefs.fontTypeface, prefs.messageWeight)
                    type = overlay
                }
            else -> return null
        }
    }
}
