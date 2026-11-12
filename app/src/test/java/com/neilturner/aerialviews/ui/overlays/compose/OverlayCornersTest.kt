package com.neilturner.aerialviews.ui.overlays.compose

import com.neilturner.aerialviews.models.SlotPref
import com.neilturner.aerialviews.models.enums.OverlayType
import com.neilturner.aerialviews.models.enums.SlotType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class OverlayCornersTest {
    private fun slot(
        type: SlotType,
        pref: OverlayType,
    ) = SlotPref(pref = pref, type = type, label = type.name)

    private val slots =
        listOf(
            slot(SlotType.SLOT_TOP_LEFT1, OverlayType.CLOCK),
            slot(SlotType.SLOT_TOP_LEFT2, OverlayType.DATE),
            slot(SlotType.SLOT_TOP_RIGHT1, OverlayType.WEATHER1),
            slot(SlotType.SLOT_TOP_RIGHT2, OverlayType.EMPTY),
            slot(SlotType.SLOT_BOTTOM_LEFT1, OverlayType.METADATA1),
            slot(SlotType.SLOT_BOTTOM_LEFT2, OverlayType.EMPTY),
            slot(SlotType.SLOT_BOTTOM_RIGHT1, OverlayType.MESSAGE1),
            slot(SlotType.SLOT_BOTTOM_RIGHT2, OverlayType.MUSIC1),
        )

    @Test
    fun `the upper slot renders above the lower slot`() {
        val corners = overlayCorners(slots, alternate = false)

        assertEquals(listOf(OverlayType.DATE, OverlayType.CLOCK), corners.topStart.types)
    }

    @Test
    fun `empty slots are dropped`() {
        val corners = overlayCorners(slots, alternate = false)

        assertEquals(listOf(OverlayType.WEATHER1), corners.topEnd.types)
        assertEquals(listOf(OverlayType.METADATA1), corners.bottomStart.types)
    }

    @Test
    fun `alternating swaps the left and right columns of both rows`() {
        val corners = overlayCorners(slots, alternate = true)

        assertEquals(listOf(OverlayType.WEATHER1), corners.topStart.types)
        assertEquals(listOf(OverlayType.DATE, OverlayType.CLOCK), corners.topEnd.types)
        // The upper slot (MUSIC1) renders above the lower one (MESSAGE1)
        assertEquals(listOf(OverlayType.MUSIC1, OverlayType.MESSAGE1), corners.bottomStart.types)
        assertEquals(listOf(OverlayType.METADATA1), corners.bottomEnd.types)
    }

    @Test
    fun `the fade setting follows the configured slot, not the screen position`() {
        val corners = overlayCorners(slots, alternate = true)

        // The clock was configured top left, so it keeps that fade setting even while it is being
        // drawn in the top right column
        assertEquals(FADE_TOP_LEFT, corners.topEnd.fadeKey)
        assertEquals(FADE_TOP_RIGHT, corners.topStart.fadeKey)
    }

    @Test
    fun `a corner with nothing configured is empty`() {
        val corners = overlayCorners(emptyList(), alternate = false)

        assertEquals(emptyList<OverlayType>(), corners.topStart.types)
        assertEquals(emptyList<OverlayType>(), corners.bottomEnd.types)
    }
}
