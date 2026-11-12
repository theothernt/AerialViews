package com.neilturner.aerialviews.ui.overlays.compose

import com.neilturner.aerialviews.models.SlotPref
import com.neilturner.aerialviews.models.enums.OverlayType
import com.neilturner.aerialviews.models.enums.SlotType

/** One corner of the screen: its overlays in display order, plus which fade setting applies. */
data class OverlayCorner(
    val types: List<OverlayType>,
    val fadeKey: String,
)

/** Where each column of overlays goes once alternation has been applied. */
data class OverlayCorners(
    val topStart: OverlayCorner,
    val topEnd: OverlayCorner,
    val bottomStart: OverlayCorner,
    val bottomEnd: OverlayCorner,
)

const val FADE_TOP_LEFT = "TOP_LEFT"
const val FADE_TOP_RIGHT = "TOP_RIGHT"
const val FADE_BOTTOM_LEFT = "BOTTOM_LEFT"
const val FADE_BOTTOM_RIGHT = "BOTTOM_RIGHT"

/**
 * Works out what goes in each column, the way OverlayHelper.buildOverlaysAndIds() and
 * assignOverlaysAndIds() do: the upper slot renders above the lower one, empty slots are dropped,
 * and alternating swaps the left and right columns of both rows.
 *
 * Kept out of the composable so the mapping - the part that is easy to get subtly wrong and hard
 * to see on screen - can be tested without a display.
 */
fun overlayCorners(
    slots: List<SlotPref>,
    alternate: Boolean,
): OverlayCorners {
    val topLeft = corner(slots, FADE_TOP_LEFT, SlotType.SLOT_TOP_LEFT1, SlotType.SLOT_TOP_LEFT2)
    val topRight = corner(slots, FADE_TOP_RIGHT, SlotType.SLOT_TOP_RIGHT1, SlotType.SLOT_TOP_RIGHT2)
    val bottomLeft = corner(slots, FADE_BOTTOM_LEFT, SlotType.SLOT_BOTTOM_LEFT1, SlotType.SLOT_BOTTOM_LEFT2)
    val bottomRight = corner(slots, FADE_BOTTOM_RIGHT, SlotType.SLOT_BOTTOM_RIGHT1, SlotType.SLOT_BOTTOM_RIGHT2)

    return OverlayCorners(
        topStart = if (alternate) topRight else topLeft,
        topEnd = if (alternate) topLeft else topRight,
        bottomStart = if (alternate) bottomRight else bottomLeft,
        bottomEnd = if (alternate) bottomLeft else bottomRight,
    )
}

private fun corner(
    slots: List<SlotPref>,
    fadeKey: String,
    lower: SlotType,
    upper: SlotType,
): OverlayCorner =
    OverlayCorner(
        types =
            listOfNotNull(
                slots.find { it.type == upper }?.pref,
                slots.find { it.type == lower }?.pref,
            ).filter { it != OverlayType.EMPTY },
        fadeKey = fadeKey,
    )
