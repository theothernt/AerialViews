package com.neilturner.aerialviews.screensaver.compose

import com.neilturner.aerialviews.models.enums.OverlayType
import com.neilturner.aerialviews.models.enums.SlotType
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.ui.overlays.config.OverlayConfig
import com.neilturner.aerialviews.ui.overlays.config.OverlayConfigInput
import com.neilturner.aerialviews.ui.overlays.config.OverlayConfigResolver
import com.neilturner.aerialviews.ui.overlays.config.OverlayCorner
import com.neilturner.aerialviews.ui.overlays.config.OverlayTypographyInput

/**
 * Maps the shared preferences into the same resolved overlay layout the legacy renderer uses.
 *
 * The resolver itself is pure; only this adapter knows about [GeneralPrefs]. Keeping it here means
 * a future settings change only has to refresh the composition, not duplicate slot rules.
 */
internal fun resolveComposeOverlayConfig(
    screenWidthPx: Int,
    density: Float,
): OverlayConfig =
    OverlayConfigResolver.resolve(
        input =
            OverlayConfigInput(
                slots =
                    mapOf(
                        SlotType.SLOT_BOTTOM_LEFT1 to (GeneralPrefs.slotBottomLeft1 ?: OverlayType.EMPTY),
                        SlotType.SLOT_BOTTOM_LEFT2 to (GeneralPrefs.slotBottomLeft2 ?: OverlayType.EMPTY),
                        SlotType.SLOT_BOTTOM_RIGHT1 to (GeneralPrefs.slotBottomRight1 ?: OverlayType.EMPTY),
                        SlotType.SLOT_BOTTOM_RIGHT2 to (GeneralPrefs.slotBottomRight2 ?: OverlayType.EMPTY),
                        SlotType.SLOT_TOP_LEFT1 to (GeneralPrefs.slotTopLeft1 ?: OverlayType.EMPTY),
                        SlotType.SLOT_TOP_LEFT2 to (GeneralPrefs.slotTopLeft2 ?: OverlayType.EMPTY),
                        SlotType.SLOT_TOP_RIGHT1 to (GeneralPrefs.slotTopRight1 ?: OverlayType.EMPTY),
                        SlotType.SLOT_TOP_RIGHT2 to (GeneralPrefs.slotTopRight2 ?: OverlayType.EMPTY),
                    ),
                typography =
                    mapOf(
                        OverlayType.CLOCK to typography(GeneralPrefs.clockSize, GeneralPrefs.clockWeight),
                        OverlayType.DATE to typography(GeneralPrefs.dateSize, GeneralPrefs.dateWeight),
                        OverlayType.METADATA1 to
                            typography(GeneralPrefs.overlayMetadata1Size, GeneralPrefs.overlayMetadata1Weight),
                        OverlayType.METADATA2 to
                            typography(GeneralPrefs.overlayMetadata2Size, GeneralPrefs.overlayMetadata2Weight),
                        OverlayType.METADATA3 to
                            typography(GeneralPrefs.overlayMetadata3Size, GeneralPrefs.overlayMetadata3Weight),
                        OverlayType.METADATA4 to
                            typography(GeneralPrefs.overlayMetadata4Size, GeneralPrefs.overlayMetadata4Weight),
                        OverlayType.WEATHER1 to typography(GeneralPrefs.weatherLine1Size, GeneralPrefs.weatherLine1Weight),
                        OverlayType.WEATHER2 to typography(GeneralPrefs.weatherLine2Size, GeneralPrefs.weatherLine2Weight),
                        OverlayType.MUSIC1 to typography(GeneralPrefs.nowPlayingSize1, GeneralPrefs.nowPlayingWeight1),
                        OverlayType.MUSIC2 to typography(GeneralPrefs.nowPlayingSize2, GeneralPrefs.nowPlayingWeight2),
                        OverlayType.MESSAGE1 to typography(GeneralPrefs.messageSize, GeneralPrefs.messageWeight),
                        OverlayType.MESSAGE2 to typography(GeneralPrefs.messageSize, GeneralPrefs.messageWeight),
                        OverlayType.MESSAGE3 to typography(GeneralPrefs.messageSize, GeneralPrefs.messageWeight),
                        OverlayType.MESSAGE4 to typography(GeneralPrefs.messageSize, GeneralPrefs.messageWeight),
                        OverlayType.COUNTDOWN to typography(GeneralPrefs.countdownSize, GeneralPrefs.countdownWeight),
                    ),
                fontTypeface = GeneralPrefs.fontTypeface,
                colour = GeneralPrefs.overlayColour,
                opacityPercent = GeneralPrefs.overlayOpacity,
                paddingHorizontal = GeneralPrefs.overlayPaddingHorizontal,
                paddingVertical = GeneralPrefs.overlayPaddingVertical,
                fadeCorners = selectedFadeCorners(),
                alternateTextPosition = GeneralPrefs.alternateTextPosition,
                showTopGradient = GeneralPrefs.showTopGradient,
                showBottomGradient = GeneralPrefs.showBottomGradient,
            ),
        screenWidthPx = screenWidthPx,
        density = density,
    )

private fun typography(
    size: String,
    weight: String,
): OverlayTypographyInput = OverlayTypographyInput(size = size, weight = weight)

private fun selectedFadeCorners(): Set<OverlayCorner> =
    GeneralPrefs.overlayFadeCornersSelection
        .mapNotNull { value ->
            runCatching { OverlayCorner.valueOf(value) }.getOrNull()
        }.toSet()
