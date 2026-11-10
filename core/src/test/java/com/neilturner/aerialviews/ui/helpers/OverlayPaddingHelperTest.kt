package com.neilturner.aerialviews.ui.helpers

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class OverlayPaddingHelperTest {
    @Test
    fun `defaults match the dimens the layouts already used`() {
        assertEquals(32f, OverlayPaddingHelper.horizontalDp(null))
        assertEquals(26f, OverlayPaddingHelper.verticalDp(null))
    }

    @Test
    fun `parses the values offered in settings`() {
        listOf("0", "8", "16", "24", "26", "32", "40", "48", "64").forEach { value ->
            assertEquals(value.toFloat(), OverlayPaddingHelper.horizontalDp(value))
            assertEquals(value.toFloat(), OverlayPaddingHelper.verticalDp(value))
        }
    }

    @Test
    fun `tolerates surrounding whitespace`() {
        assertEquals(48f, OverlayPaddingHelper.horizontalDp(" 48 "))
    }

    @Test
    fun `unparseable values fall back to the default`() {
        assertEquals(32f, OverlayPaddingHelper.horizontalDp(""))
        assertEquals(26f, OverlayPaddingHelper.verticalDp("not-a-number"))
        assertEquals(32f, OverlayPaddingHelper.horizontalDp("NaN"))
        assertEquals(26f, OverlayPaddingHelper.verticalDp("Infinity"))
    }

    @Test
    fun `clamps values outside the supported range`() {
        assertEquals(0f, OverlayPaddingHelper.horizontalDp("-8"))
        assertEquals(64f, OverlayPaddingHelper.verticalDp("250"))
    }

    @Test
    fun `converts dp to pixels`() {
        assertEquals(64, OverlayPaddingHelper.toPx(32f, 2f))
        assertEquals(32, OverlayPaddingHelper.toPx(32f, 1f))
        assertEquals(0, OverlayPaddingHelper.toPx(0f, 3f))
    }
}
