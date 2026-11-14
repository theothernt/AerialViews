package com.neilturner.aerialviews.ui.overlays.compose

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import com.neilturner.aerialviews.models.SlotPref
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
    fadeInDurationMillis: Int,
    fadeOutDurationMillis: Int,
    fadeCorners: Set<String>,
    animateFade: Boolean = true,
    modifier: Modifier = Modifier,
) {
    // User adjustable edge padding. OverlayHelper overrides the dimens values at runtime, so the
    // preferences are the authority here too. Parsed once per preference value: this recomposes on
    // every fade frame and parsing the strings each time is wasted work.
    val horizontalPref = GeneralPrefs.overlayPaddingHorizontal
    val verticalPref = GeneralPrefs.overlayPaddingVertical
    val horizontal = remember(horizontalPref) { OverlayPaddingHelper.horizontalDp(horizontalPref).dp }
    val vertical = remember(verticalPref) { OverlayPaddingHelper.verticalDp(verticalPref).dp }

    // Revealing and hiding are separate animations in the View engine too: the preferences carry
    // both durations, and the mode decides which one the layer is heading towards. A target of 0 is
    // the hidden target, anything above it is a reveal towards the user's overlay opacity.
    val fadeDurationMillis = if (fadeAlpha > 0f) fadeInDurationMillis else fadeOutDurationMillis

    // A snap must land in one frame, like the View's direct alpha set; a fade takes the configured
    // duration. animateFloatAsState always animates, so the layer drives an Animatable itself.
    val animatedFade = remember { Animatable(fadeAlpha) }
    LaunchedEffect(fadeAlpha, animateFade, fadeDurationMillis) {
        if (animateFade) {
            animatedFade.animateTo(
                fadeAlpha,
                tween(durationMillis = fadeDurationMillis, easing = OVERLAY_FADE_EASING),
            )
        } else {
            animatedFade.snapTo(fadeAlpha)
        }
    }

    val corners = remember(slots, alternate) { overlayCorners(slots, alternate) }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .padding(horizontal = horizontal, vertical = vertical),
    ) {
        OverlayCornerColumn(
            corner = corners.topStart,
            state = state,
            videoPosition = videoPosition,
            alpha = corners.topStart.alpha(animatedFade.value, visibleAlpha, fadeCorners),
            horizontalAlignment = Alignment.Start,
            modifier = Modifier.align(Alignment.TopStart),
        )
        OverlayCornerColumn(
            corner = corners.topEnd,
            state = state,
            videoPosition = videoPosition,
            alpha = corners.topEnd.alpha(animatedFade.value, visibleAlpha, fadeCorners),
            horizontalAlignment = Alignment.End,
            modifier = Modifier.align(Alignment.TopEnd),
        )
        OverlayCornerColumn(
            corner = corners.bottomStart,
            state = state,
            videoPosition = videoPosition,
            alpha = corners.bottomStart.alpha(animatedFade.value, visibleAlpha, fadeCorners),
            horizontalAlignment = Alignment.Start,
            modifier = Modifier.align(Alignment.BottomStart),
        )
        OverlayCornerColumn(
            corner = corners.bottomEnd,
            state = state,
            videoPosition = videoPosition,
            alpha = corners.bottomEnd.alpha(animatedFade.value, visibleAlpha, fadeCorners),
            horizontalAlignment = Alignment.End,
            modifier = Modifier.align(Alignment.BottomEnd),
        )
    }
}

@Composable
private fun OverlayCornerColumn(
    corner: OverlayCorner,
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
