package com.neilturner.aerialviews.ui.overlays.compose

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import com.neilturner.aerialviews.models.SlotPref
import com.neilturner.aerialviews.models.enums.OverlayType
import com.neilturner.aerialviews.models.enums.SlotType
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.ui.helpers.OverlayPaddingHelper
import com.neilturner.aerialviews.ui.overlays.state.OverlayUiState

/** Vertical gap between overlays in a corner (res/values/dimens.xml text_block_padding). */
private val cornerGap = 3.dp

/**
 * The four-corner overlay layer: the Compose counterpart of overlay_view.xml's four Flow
 * widgets, the empty spacer views and OverlayHelper's slot assignment.
 *
 * State is hoisted - this takes rendered state and alpha values, and reads only the two
 * padding preferences it needs to position itself.
 */
@Composable
fun ScreensaverOverlays(
    state: OverlayUiState,
    slots: List<SlotPref>,
    videoPosition: () -> Long,
    alternate: Boolean,
    visibleAlpha: Float,
    fadeAlpha: Float,
    fadeDurationMillis: Int,
    fadeCorners: Set<String>,
    modifier: Modifier = Modifier,
) {
    // User adjustable edge padding. OverlayHelper overrides the dimens values at runtime, so the
    // preferences are the authority here too.
    val horizontal = OverlayPaddingHelper.horizontalDp(GeneralPrefs.overlayPaddingHorizontal).dp
    val vertical = OverlayPaddingHelper.verticalDp(GeneralPrefs.overlayPaddingVertical).dp

    val animatedFade by animateFloatAsState(
        targetValue = fadeAlpha,
        animationSpec = tween(durationMillis = fadeDurationMillis),
        label = "overlayFadeAlpha",
    )

    val topLeft = corner("TOP_LEFT", slots, SlotType.SLOT_TOP_LEFT1, SlotType.SLOT_TOP_LEFT2)
    val topRight = corner("TOP_RIGHT", slots, SlotType.SLOT_TOP_RIGHT1, SlotType.SLOT_TOP_RIGHT2)
    val bottomLeft = corner("BOTTOM_LEFT", slots, SlotType.SLOT_BOTTOM_LEFT1, SlotType.SLOT_BOTTOM_LEFT2)
    val bottomRight = corner("BOTTOM_RIGHT", slots, SlotType.SLOT_BOTTOM_RIGHT1, SlotType.SLOT_BOTTOM_RIGHT2)

    // Alternating position swaps the left and right columns of both rows, which is what
    // OverlayHelper.assignOverlaysAndIds() does by swapping the Flow referenced ids.
    val topStart = if (alternate) topRight else topLeft
    val topEnd = if (alternate) topLeft else topRight
    val bottomStart = if (alternate) bottomRight else bottomLeft
    val bottomEnd = if (alternate) bottomLeft else bottomRight

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .padding(horizontal = horizontal, vertical = vertical),
    ) {
        OverlayCorner(
            corner = topStart,
            state = state,
            videoPosition = videoPosition,
            alpha = topStart.alpha(animatedFade, visibleAlpha, fadeCorners),
            horizontalAlignment = Alignment.Start,
            modifier = Modifier.align(Alignment.TopStart),
        )
        OverlayCorner(
            corner = topEnd,
            state = state,
            videoPosition = videoPosition,
            alpha = topEnd.alpha(animatedFade, visibleAlpha, fadeCorners),
            horizontalAlignment = Alignment.End,
            modifier = Modifier.align(Alignment.TopEnd),
        )
        OverlayCorner(
            corner = bottomStart,
            state = state,
            videoPosition = videoPosition,
            alpha = bottomStart.alpha(animatedFade, visibleAlpha, fadeCorners),
            horizontalAlignment = Alignment.Start,
            modifier = Modifier.align(Alignment.BottomStart),
        )
        OverlayCorner(
            corner = bottomEnd,
            state = state,
            videoPosition = videoPosition,
            alpha = bottomEnd.alpha(animatedFade, visibleAlpha, fadeCorners),
            horizontalAlignment = Alignment.End,
            modifier = Modifier.align(Alignment.BottomEnd),
        )
    }
}

@Composable
private fun OverlayCorner(
    corner: Corner,
    state: OverlayUiState,
    videoPosition: () -> Long,
    alpha: Float,
    horizontalAlignment: Alignment.Horizontal,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.alpha(alpha),
        verticalArrangement = Arrangement.spacedBy(cornerGap),
        horizontalAlignment = horizontalAlignment,
    ) {
        corner.types.forEach { type ->
            OverlaySlotContent(type = type, state = state, videoPosition = videoPosition)
        }
    }
}

/**
 * Corners in a row share one fade setting, but the setting is keyed by the slot the user
 * configured, not by the screen position: alternating position moves the overlay without moving
 * the corner's fade behaviour, matching getOverlaysToFade().
 */
private fun Corner.alpha(
    fadeAlpha: Float,
    visibleAlpha: Float,
    fadeCorners: Set<String>,
): Float = if (fadeKey in fadeCorners) fadeAlpha else visibleAlpha

private fun corner(
    fadeKey: String,
    slots: List<SlotPref>,
    lower: SlotType,
    upper: SlotType,
): Corner =
    Corner(
        // Display order matches OverlayHelper.buildReferenceIds: the upper slot renders above
        // the lower slot in every corner.
        types =
            listOfNotNull(
                slots.find { it.type == upper }?.pref,
                slots.find { it.type == lower }?.pref,
            ).filter { it != OverlayType.EMPTY },
        fadeKey = fadeKey,
    )

private data class Corner(
    val types: List<OverlayType>,
    val fadeKey: String,
)
