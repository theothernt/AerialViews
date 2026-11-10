package com.neilturner.aerialviews.ui.overlays.config

import com.neilturner.aerialviews.models.enums.OverlayType
import com.neilturner.aerialviews.models.enums.SlotType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OverlayConfigResolverTest {
    @Test
    fun `resolves populated slots with padding and typography`() {
        val config =
            OverlayConfigResolver.resolve(
                input =
                    OverlayConfigInput(
                        slots =
                            mapOf(
                                SlotType.SLOT_BOTTOM_LEFT1 to OverlayType.CLOCK,
                                SlotType.SLOT_TOP_RIGHT2 to OverlayType.METADATA1,
                            ),
                        typography =
                            mapOf(
                                OverlayType.CLOCK to OverlayTypographyInput("24", "400"),
                                OverlayType.METADATA1 to OverlayTypographyInput("32", "600"),
                            ),
                        opacityPercent = "40",
                        paddingHorizontal = "32",
                        paddingVertical = "26",
                        fadeCorners = setOf(OverlayCorner.TOP_LEFT),
                        showTopGradient = true,
                        showBottomGradient = false,
                    ),
                screenWidthPx = 1_920,
                density = 2f,
            )

        assertEquals(2, config.layout.slots.size)
        assertEquals(64, config.layout.paddingHorizontalPx)
        assertEquals(52, config.layout.paddingVerticalPx)
        assertEquals(1_792, config.layout.maxTextWidthPx)
        assertEquals(0.4f, config.layout.visibleAlpha, 0.001f)
        assertEquals(setOf(OverlayCorner.TOP_LEFT), config.layout.fadeCorners)
        assertTrue(config.layout.showTopGradient)
        assertEquals(false, config.layout.showBottomGradient)

        val clock = config.layout.slots.first()
        assertEquals(SlotType.SLOT_BOTTOM_LEFT1, clock.slotType)
        assertEquals(OverlayType.CLOCK, clock.overlayType)
        assertEquals(OverlayCorner.BOTTOM_LEFT, clock.corner)
        assertEquals(OverlayRow.LOWER, clock.row)
        assertEquals(1, clock.verticalOrder)
        assertEquals(24f, clock.typography.sizeSp)
        assertEquals(400, clock.typography.weight)

        val metadata = config.layout.slots.last()
        assertEquals(SlotType.SLOT_TOP_RIGHT2, metadata.slotType)
        assertEquals(OverlayCorner.TOP_RIGHT, metadata.corner)
        assertEquals(OverlayRow.UPPER, metadata.row)
        assertEquals(0, metadata.verticalOrder)
        assertEquals(600, metadata.typography.weight)
    }

    @Test
    fun `alternation mirrors left and right corners but not rows`() {
        val config =
            OverlayConfigResolver.resolve(
                input =
                    OverlayConfigInput(
                        slots =
                            mapOf(
                                SlotType.SLOT_BOTTOM_LEFT1 to OverlayType.CLOCK,
                                SlotType.SLOT_BOTTOM_RIGHT2 to OverlayType.DATE,
                                SlotType.SLOT_TOP_LEFT2 to OverlayType.WEATHER1,
                                SlotType.SLOT_TOP_RIGHT1 to OverlayType.METADATA2,
                            ),
                        alternateTextPosition = true,
                    ),
                screenWidthPx = 1_920,
                density = 1f,
            )

        val original = config.layout.slots.associateBy { it.slotType }
        val mirrored = config.slotsFor(alternate = true).associateBy { it.slotType }

        assertEquals(OverlayCorner.BOTTOM_LEFT, original.getValue(SlotType.SLOT_BOTTOM_LEFT1).corner)
        assertEquals(OverlayCorner.BOTTOM_RIGHT, mirrored.getValue(SlotType.SLOT_BOTTOM_LEFT1).corner)
        assertEquals(OverlayRow.LOWER, mirrored.getValue(SlotType.SLOT_BOTTOM_LEFT1).row)

        assertEquals(OverlayCorner.BOTTOM_RIGHT, original.getValue(SlotType.SLOT_BOTTOM_RIGHT2).corner)
        assertEquals(OverlayCorner.BOTTOM_LEFT, mirrored.getValue(SlotType.SLOT_BOTTOM_RIGHT2).corner)
        assertEquals(OverlayRow.UPPER, mirrored.getValue(SlotType.SLOT_BOTTOM_RIGHT2).row)

        assertEquals(OverlayCorner.TOP_LEFT, original.getValue(SlotType.SLOT_TOP_LEFT2).corner)
        assertEquals(OverlayCorner.TOP_RIGHT, mirrored.getValue(SlotType.SLOT_TOP_LEFT2).corner)
        assertEquals(OverlayCorner.TOP_RIGHT, original.getValue(SlotType.SLOT_TOP_RIGHT1).corner)
        assertEquals(OverlayCorner.TOP_LEFT, mirrored.getValue(SlotType.SLOT_TOP_RIGHT1).corner)

        assertEquals(original.values.toList(), config.slotsFor(alternate = false))
    }

    @Test
    fun `disabled alternation leaves slots in place`() {
        val config =
            OverlayConfigResolver.resolve(
                input =
                    OverlayConfigInput(
                        slots = mapOf(SlotType.SLOT_BOTTOM_LEFT1 to OverlayType.CLOCK),
                        alternateTextPosition = false,
                    ),
                screenWidthPx = 1_920,
                density = 1f,
            )

        assertEquals(OverlayCorner.BOTTOM_LEFT, config.slotsFor(alternate = true).single().corner)
    }

    @Test
    fun `empty slots are omitted`() {
        val config =
            OverlayConfigResolver.resolve(
                input =
                    OverlayConfigInput(
                        slots =
                            mapOf(
                                SlotType.SLOT_BOTTOM_LEFT1 to OverlayType.EMPTY,
                                SlotType.SLOT_BOTTOM_RIGHT1 to OverlayType.CLOCK,
                            ),
                    ),
                screenWidthPx = 1_920,
                density = 1f,
            )

        assertEquals(1, config.layout.slots.size)
        assertEquals(
            OverlayType.CLOCK,
            config.layout.slots
                .single()
                .overlayType,
        )
    }

    @Test
    fun `invalid values fall back to defaults`() {
        val config =
            OverlayConfigResolver.resolve(
                input =
                    OverlayConfigInput(
                        slots = mapOf(SlotType.SLOT_BOTTOM_LEFT1 to OverlayType.CLOCK),
                        typography =
                            mapOf(
                                OverlayType.CLOCK to OverlayTypographyInput("not-a-size", "1001"),
                            ),
                        opacityPercent = "nope",
                        paddingHorizontal = "not-a-padding",
                        paddingVertical = "Infinity",
                    ),
                screenWidthPx = 1_920,
                density = 1f,
            )

        val style =
            config.layout.slots
                .single()
                .typography
        assertEquals(18f, style.sizeSp)
        assertEquals(300, style.weight)
        assertEquals(1f, config.layout.visibleAlpha)
        assertEquals(32, config.layout.paddingHorizontalPx)
        assertEquals(26, config.layout.paddingVerticalPx)
    }
}
