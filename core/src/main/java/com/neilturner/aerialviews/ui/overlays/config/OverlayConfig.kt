package com.neilturner.aerialviews.ui.overlays.config

import com.neilturner.aerialviews.models.enums.OverlayType
import com.neilturner.aerialviews.models.enums.SlotType
import com.neilturner.aerialviews.ui.helpers.OverlayOpacityHelper
import com.neilturner.aerialviews.ui.helpers.OverlayPaddingHelper

enum class OverlayCorner {
    BOTTOM_LEFT,
    BOTTOM_RIGHT,
    TOP_LEFT,
    TOP_RIGHT,
    ;

    /**
     * The position this corner moves to when text alternation is enabled.
     *
     * Alternation swaps the left and right columns of both rows; it never moves text between the
     * top and bottom of the screen.
     */
    fun mirrored(): OverlayCorner =
        when (this) {
            BOTTOM_LEFT -> BOTTOM_RIGHT
            BOTTOM_RIGHT -> BOTTOM_LEFT
            TOP_LEFT -> TOP_RIGHT
            TOP_RIGHT -> TOP_LEFT
        }
}

/** The vertical position of a slot within its corner. */
enum class OverlayRow {
    /** The upper slot (the slot numbered 2 in the legacy layout). */
    UPPER,

    /** The lower slot (the slot numbered 1 in the legacy layout). */
    LOWER,
}

/**
 * Typography for one overlay.
 *
 * Sizes are sp values and weights are the variable-font weights used by the legacy views.
 */
data class OverlayTypography(
    val sizeSp: Float,
    val weight: Int,
)

/**
 * One populated overlay slot.
 *
 * [verticalOrder] is 0 for the upper slot and 1 for the lower slot, matching the order in which
 * the legacy Flow columns stack their children.
 */
data class OverlaySlotConfig(
    val slotType: SlotType,
    val overlayType: OverlayType,
    val corner: OverlayCorner,
    val row: OverlayRow,
    val verticalOrder: Int,
    val typography: OverlayTypography,
)

/**
 * The resolved layout shared by the legacy views and the future Compose renderer.
 *
 * This model deliberately contains no Android types. A renderer supplies its own text measurement,
 * padding conversion and view construction; it only consumes these decisions.
 */
data class OverlayLayout(
    val slots: List<OverlaySlotConfig>,
    val paddingHorizontalPx: Int,
    val paddingVerticalPx: Int,
    val maxTextWidthPx: Int,
    val visibleAlpha: Float,
    val fadeCorners: Set<OverlayCorner>,
    val showTopGradient: Boolean,
    val showBottomGradient: Boolean,
    val alternateTextPosition: Boolean,
) {
    /**
     * Returns the slots for the current item.
     *
     * [alternate] is the renderer's per-item alternation state. When the user has disabled
     * alternation the layout is returned unchanged.
     */
    fun slotsFor(alternate: Boolean): List<OverlaySlotConfig> {
        if (!alternateTextPosition || !alternate) return slots
        return slots.map { it.copy(corner = it.corner.mirrored()) }
    }
}

data class OverlayConfig(
    val layout: OverlayLayout,
    val fontTypeface: String,
    val colour: String,
) {
    fun slotsFor(alternate: Boolean): List<OverlaySlotConfig> = layout.slotsFor(alternate)
}

/** Raw preference values for the overlay configuration. */
data class OverlayConfigInput(
    val slots: Map<SlotType, OverlayType> = emptyMap(),
    val typography: Map<OverlayType, OverlayTypographyInput> = emptyMap(),
    val fontTypeface: String = "google-sans",
    val colour: String = "#FFFFFF",
    val opacityPercent: String? = "100",
    val paddingHorizontal: String? = "32",
    val paddingVertical: String? = "26",
    val fadeCorners: Set<OverlayCorner> = OverlayCorner.entries.toSet(),
    val alternateTextPosition: Boolean = true,
    val showTopGradient: Boolean = false,
    val showBottomGradient: Boolean = true,
)

data class OverlayTypographyInput(
    val size: String? = "18",
    val weight: String? = "300",
)

object OverlayConfigResolver {
    private const val DEFAULT_TEXT_SIZE_SP = 18f
    private const val DEFAULT_WEIGHT = 300
    private const val MIN_WEIGHT = 1
    private const val MAX_WEIGHT = 1000

    /**
     * Resolves a layout without touching Android resources or `GeneralPrefs`.
     *
     * [density] is passed in so the resolver can turn the user's dp padding into pixels. The legacy
     * view uses the same conversion through [OverlayPaddingHelper].
     */
    fun resolve(
        input: OverlayConfigInput,
        screenWidthPx: Int,
        density: Float,
    ): OverlayConfig {
        val horizontalPx =
            OverlayPaddingHelper.toPx(
                OverlayPaddingHelper.horizontalDp(input.paddingHorizontal),
                density,
            )
        val verticalPx =
            OverlayPaddingHelper.toPx(
                OverlayPaddingHelper.verticalDp(input.paddingVertical),
                density,
            )

        val slots =
            SlotType.entries.mapNotNull { slotType ->
                val overlayType = input.slots[slotType] ?: OverlayType.EMPTY
                if (overlayType == OverlayType.EMPTY) return@mapNotNull null

                val row = rowFor(slotType)
                OverlaySlotConfig(
                    slotType = slotType,
                    overlayType = overlayType,
                    corner = cornerFor(slotType),
                    row = row,
                    verticalOrder = row.ordinal,
                    typography =
                        resolveTypography(
                            input.typography[overlayType],
                        ),
                )
            }

        return OverlayConfig(
            layout =
                OverlayLayout(
                    slots = slots,
                    paddingHorizontalPx = horizontalPx,
                    paddingVerticalPx = verticalPx,
                    maxTextWidthPx = (screenWidthPx - horizontalPx * 2).coerceAtLeast(0),
                    visibleAlpha = OverlayOpacityHelper.alphaFromPercent(input.opacityPercent),
                    fadeCorners = input.fadeCorners,
                    showTopGradient = input.showTopGradient,
                    showBottomGradient = input.showBottomGradient,
                    alternateTextPosition = input.alternateTextPosition,
                ),
            fontTypeface = input.fontTypeface,
            colour = input.colour,
        )
    }

    private fun resolveTypography(input: OverlayTypographyInput?): OverlayTypography =
        OverlayTypography(
            sizeSp = parseTextSize(input?.size),
            weight = parseWeight(input?.weight),
        )

    private fun parseTextSize(value: String?): Float =
        value
            ?.trim()
            ?.toFloatOrNull()
            ?.takeIf { it.isFinite() && it > 0f }
            ?: DEFAULT_TEXT_SIZE_SP

    private fun parseWeight(value: String?): Int =
        value
            ?.trim()
            ?.toIntOrNull()
            ?.takeIf { it in MIN_WEIGHT..MAX_WEIGHT }
            ?: DEFAULT_WEIGHT

    private fun cornerFor(slot: SlotType): OverlayCorner =
        when (slot) {
            SlotType.SLOT_BOTTOM_LEFT1,
            SlotType.SLOT_BOTTOM_LEFT2,
            -> OverlayCorner.BOTTOM_LEFT

            SlotType.SLOT_BOTTOM_RIGHT1,
            SlotType.SLOT_BOTTOM_RIGHT2,
            -> OverlayCorner.BOTTOM_RIGHT

            SlotType.SLOT_TOP_LEFT1,
            SlotType.SLOT_TOP_LEFT2,
            -> OverlayCorner.TOP_LEFT

            SlotType.SLOT_TOP_RIGHT1,
            SlotType.SLOT_TOP_RIGHT2,
            -> OverlayCorner.TOP_RIGHT
        }

    private fun rowFor(slot: SlotType): OverlayRow =
        when (slot) {
            SlotType.SLOT_BOTTOM_LEFT1,
            SlotType.SLOT_BOTTOM_RIGHT1,
            SlotType.SLOT_TOP_LEFT1,
            SlotType.SLOT_TOP_RIGHT1,
            -> OverlayRow.LOWER

            SlotType.SLOT_BOTTOM_LEFT2,
            SlotType.SLOT_BOTTOM_RIGHT2,
            SlotType.SLOT_TOP_LEFT2,
            SlotType.SLOT_TOP_RIGHT2,
            -> OverlayRow.UPPER
        }
}
