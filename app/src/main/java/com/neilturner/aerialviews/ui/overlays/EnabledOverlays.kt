package com.neilturner.aerialviews.ui.overlays

import android.content.Context
import com.neilturner.aerialviews.models.enums.OverlayType
import com.neilturner.aerialviews.ui.helpers.SlotHelper

/**
 * Which overlays the user has assigned to a slot.
 *
 * Shared by both engines. The XML path used to answer this with OverlayHelper.findOverlay<T>(),
 * which reflects instantiated Views - so it is always empty when the Compose layer draws the
 * overlays, which silently stopped the weather service and the message API from starting.
 */
object EnabledOverlays {
    fun types(context: Context): Set<OverlayType> =
        SlotHelper
            .slotPrefs(context)
            .map { it.pref }
            .filter { it != OverlayType.EMPTY }
            .toSet()
}
