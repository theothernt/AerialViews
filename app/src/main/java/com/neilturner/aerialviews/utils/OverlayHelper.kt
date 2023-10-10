package com.neilturner.aerialviews.utils

import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import android.view.View
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.widget.TextViewCompat
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.databinding.VideoViewBinding
import com.neilturner.aerialviews.models.enums.OverlayType
import com.neilturner.aerialviews.models.enums.SlotType
import com.neilturner.aerialviews.models.prefs.InterfacePrefs
import com.neilturner.aerialviews.ui.overlays.AltTextClock
import com.neilturner.aerialviews.ui.overlays.TextLocation

object OverlayHelper {

    fun buildOverlayIds(context: Context, root: VideoViewBinding, typeface: Typeface?, prefs: InterfacePrefs): Pair<List<Int>, List<Int>> {
        val views = mutableListOf<View?>()
        val slots = SlotHelper.slotPrefs()

        for (type in SlotType.entries) {
            val overlay = slots.find { it.second == type }
            val view = getOverlay(context, typeface, prefs, overlay?.first)
            view?.id = ViewCompat.generateViewId()
            views.add(view)
            if (view != null) {
                root.layout.addView(view)
            }
        }

        val bottomEmptyView = root.emptyViewBottom
        // val topEmptyView = TextView(context)
        return buildReferenceIds(views[0], views[1], views[2], views[3], bottomEmptyView)
    }

    private fun buildReferenceIds(left1: View?, left2: View?, right1: View?, right2: View?, empty: View): Pair<List<Int>, List<Int>> {
        var leftIds = listOfNotNull(left1?.id, left2?.id)
        var rightIds = listOfNotNull(right1?.id, right2?.id)

        if (leftIds.isEmpty() && rightIds.isNotEmpty()) {
            leftIds = listOf(empty.id)
        }

        if (leftIds.isNotEmpty() && rightIds.isEmpty()) {
            rightIds = listOf(empty.id)
        }
        return Pair(leftIds, rightIds)
    }

    private fun getOverlay(context: Context, typeface: Typeface?, prefs: InterfacePrefs, type: OverlayType?): View? {
        when (type) {
            OverlayType.CLOCK -> {
                val clock = AltTextClock(context)
                TextViewCompat.setTextAppearance(clock, R.style.ClockText)
                clock.typeface = typeface
                clock.setTextSize(TypedValue.COMPLEX_UNIT_SP, InterfacePrefs.clockSize.toFloat())
                return clock
            }
            OverlayType.LOCATION -> {
                val location = TextLocation(context)
                TextViewCompat.setTextAppearance(location, R.style.LocationText)
                location.setTextSize(TypedValue.COMPLEX_UNIT_SP, InterfacePrefs.locationSize.toFloat())
                location.typeface = typeface
                location.text = "Somewhere lovely!"
                return location
            }
            OverlayType.DATE -> {
                val date = TextView(context)
                TextViewCompat.setTextAppearance(date, R.style.LocationText)
                date.setTextSize(TypedValue.COMPLEX_UNIT_SP, InterfacePrefs.locationSize.toFloat())
                date.typeface = typeface
                date.text = "Today's date!"
                return date
            }

            OverlayType.MUSIC -> {
                val location = TextLocation(context)
                TextViewCompat.setTextAppearance(location, R.style.LocationText)
                location.setTextSize(TypedValue.COMPLEX_UNIT_SP, InterfacePrefs.locationSize.toFloat())
                location.typeface = typeface
                location.text = "Music 🎶"
                return location
            }
            else -> {
                return null
            }
        }
    }
}
