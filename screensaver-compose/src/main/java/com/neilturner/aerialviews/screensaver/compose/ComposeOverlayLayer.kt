@file:Suppress("ktlint:standard:function-naming")

package com.neilturner.aerialviews.screensaver.compose

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.ui.core.ScreensaverController
import com.neilturner.aerialviews.ui.core.ScreensaverState
import com.neilturner.aerialviews.ui.overlays.config.OverlayConfig
import com.neilturner.aerialviews.ui.overlays.config.OverlayCorner
import com.neilturner.aerialviews.ui.overlays.config.OverlaySlotConfig
import com.neilturner.aerialviews.ui.overlays.state.OverlayUiState
import kotlinx.coroutines.delay

private const val TEXT_BLOCK_SPACING_DP = 3
private const val GRADIENT_HEIGHT_DP = 200
private const val GRADIENT_TOP_ALPHA = 0.7f

/**
 * Renders the shared overlay content in the four legacy corner flows.
 *
 * The fade only affects the fade-enabled corner groups and their gradients. Persistent corners stay
 * at the configured opacity, matching the per-corner fade selection.
 */
@Suppress("ktlint:standard:function-naming")
@Composable
internal fun ComposeOverlayLayer(
    controller: ScreensaverController,
    state: ScreensaverState,
    overlayState: OverlayUiState,
    revealKey: Int = 0,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.roundToPx() }
        val config = remember(widthPx, density.density) { resolveComposeOverlayConfig(widthPx, density.density) }
        val layout = remember(config, state.alternateOverlays) { config.slotsFor(state.alternateOverlays) }
        val slotsByCorner = remember(layout) { layout.groupBy { it.corner } }
        val fadeCorners = config.layout.fadeCorners
        val fadeSlots = remember(layout, fadeCorners) { layout.filter { it.corner in fadeCorners } }
        val fadeAlpha = remember { Animatable(1f) }
        val horizontalPadding = with(density) { config.layout.paddingHorizontalPx.toDp() }
        val verticalPadding = with(density) { config.layout.paddingVerticalPx.toDp() }
        val maxTextWidth = with(density) { config.layout.maxTextWidthPx.toDp() }
        val visibilityMode = remember { GeneralPrefs.overlayVisibility }
        // The controller reaches canSkip only after the media fade-in, so the legacy
        // visibility delay is measured from that point rather than adding the media fade again.
        val overlayDelayMs =
            remember(visibilityMode) {
                (GeneralPrefs.overlayVisibilityDelay.toLongOrNull() ?: 0L) * 1_000L
            }
        val fadeInMs = remember { GeneralPrefs.overlayFadeInDuration.toLongOrNull()?.coerceAtLeast(0) ?: 0L }
        val fadeOutMs = remember { GeneralPrefs.overlayFadeOutDuration.toLongOrNull()?.coerceAtLeast(0) ?: 0L }
        val overlayRevealTimeoutMs =
            remember { (GeneralPrefs.overlayRevealTimeout.toLongOrNull() ?: 0L) * 1_000L }
        val hasTopFade = layout.any { it.corner.isTop && it.corner in fadeCorners }
        val hasTopPersistent = layout.any { it.corner.isTop && it.corner !in fadeCorners }
        val hasBottomFade = layout.any { !it.corner.isTop && it.corner in fadeCorners }
        val hasBottomPersistent = layout.any { !it.corner.isTop && it.corner !in fadeCorners }

        LaunchedEffect(state.itemGeneration, state.canSkip, revealKey, config, layout) {
            if (state.blackout.active) {
                fadeAlpha.snapTo(0f)
                return@LaunchedEffect
            }
            if (!state.canSkip) return@LaunchedEffect

            if (fadeSlots.isEmpty()) {
                fadeAlpha.snapTo(1f)
                controller.onOverlaysReady()
                return@LaunchedEffect
            }

            val revealed = revealKey > 0
            when (visibilityMode) {
                "ALWAYS_VISIBLE" -> {
                    fadeAlpha.snapTo(1f)
                    controller.onOverlaysReady()
                }

                "ALWAYS_HIDDEN" -> {
                    if (!revealed) {
                        fadeAlpha.snapTo(0f)
                        controller.onOverlaysReady()
                    } else {
                        revealAndAutoHide(
                            controller = controller,
                            fadeAlpha = fadeAlpha,
                            revealTimeoutMs = overlayRevealTimeoutMs,
                            fadeOutMs = fadeOutMs,
                        )
                    }
                }

                "HIDE_AFTER_DELAY" -> {
                    if (revealed) {
                        revealAndAutoHide(
                            controller = controller,
                            fadeAlpha = fadeAlpha,
                            revealTimeoutMs = overlayRevealTimeoutMs,
                            fadeOutMs = fadeOutMs,
                        )
                    } else {
                        fadeAlpha.snapTo(1f)
                        delay(overlayDelayMs)
                        controller.onOverlaysBusy()
                        fadeAlpha.animateTo(
                            targetValue = 0f,
                            animationSpec = tween(durationMillis = fadeOutMs.toInt(), easing = LinearEasing),
                        )
                        controller.onOverlaysReady()
                    }
                }

                "SHOW_AFTER_DELAY" -> {
                    if (revealed) {
                        revealAndAutoHide(
                            controller = controller,
                            fadeAlpha = fadeAlpha,
                            revealTimeoutMs = overlayRevealTimeoutMs,
                            fadeOutMs = fadeOutMs,
                        )
                    } else {
                        fadeAlpha.snapTo(0f)
                        controller.onOverlaysBusy()
                        delay(overlayDelayMs)
                        fadeAlpha.animateTo(
                            targetValue = 1f,
                            animationSpec = tween(durationMillis = fadeInMs.toInt(), easing = LinearEasing),
                        )
                        controller.onOverlaysReady()
                    }
                }

                else -> {
                    fadeAlpha.snapTo(1f)
                    controller.onOverlaysReady()
                }
            }
        }

        if (config.layout.showTopGradient) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(GRADIENT_HEIGHT_DP.dp)
                        .align(Alignment.TopCenter)
                        .graphicsLayer {
                            alpha = if (hasTopFade && !hasTopPersistent) fadeAlpha.value else 1f
                        }.background(
                            Brush.verticalGradient(
                                colors =
                                    listOf(
                                        Color.Black.copy(alpha = GRADIENT_TOP_ALPHA),
                                        Color.Transparent,
                                    ),
                            ),
                        ),
            )
        }

        if (config.layout.showBottomGradient) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(GRADIENT_HEIGHT_DP.dp)
                        .align(Alignment.BottomCenter)
                        .graphicsLayer {
                            alpha = if (hasBottomFade && !hasBottomPersistent) fadeAlpha.value else 1f
                        }.background(
                            Brush.verticalGradient(
                                colors =
                                    listOf(
                                        Color.Transparent,
                                        Color.Black.copy(alpha = GRADIENT_TOP_ALPHA),
                                    ),
                            ),
                        ),
            )
        }

        OverlayCorner.entries.forEach { corner ->
            val slots = slotsByCorner[corner].orEmpty()
            if (slots.isNotEmpty()) {
                OverlayCornerColumn(
                    corner = corner,
                    slots = slots,
                    config = config,
                    state = state,
                    overlayState = overlayState,
                    fadeCorners = fadeCorners,
                    fadeAlpha = fadeAlpha,
                    horizontalPadding = horizontalPadding,
                    verticalPadding = verticalPadding,
                    maxTextWidth = maxTextWidth,
                )
            }
        }
    }
}

@Composable
private fun BoxScope.OverlayCornerColumn(
    corner: OverlayCorner,
    slots: List<OverlaySlotConfig>,
    config: OverlayConfig,
    state: ScreensaverState,
    overlayState: OverlayUiState,
    fadeCorners: Set<OverlayCorner>,
    fadeAlpha: Animatable<Float, *>,
    horizontalPadding: androidx.compose.ui.unit.Dp,
    verticalPadding: androidx.compose.ui.unit.Dp,
    maxTextWidth: androidx.compose.ui.unit.Dp,
) {
    val padding =
        when (corner) {
            OverlayCorner.TOP_LEFT -> PaddingValues(start = horizontalPadding, top = verticalPadding)
            OverlayCorner.TOP_RIGHT -> PaddingValues(end = horizontalPadding, top = verticalPadding)
            OverlayCorner.BOTTOM_LEFT -> PaddingValues(start = horizontalPadding, bottom = verticalPadding)
            OverlayCorner.BOTTOM_RIGHT -> PaddingValues(end = horizontalPadding, bottom = verticalPadding)
        }
    val horizontalAlignment =
        if (corner.isRight) Alignment.End else Alignment.Start

    Column(
        modifier =
            Modifier
                .align(corner.alignment())
                .padding(padding)
                .widthIn(max = maxTextWidth)
                .graphicsLayer {
                    alpha = if (corner in fadeCorners) fadeAlpha.value else 1f
                },
        horizontalAlignment = horizontalAlignment,
        verticalArrangement = Arrangement.spacedBy(TEXT_BLOCK_SPACING_DP.dp),
    ) {
        slots
            .sortedBy { it.verticalOrder }
            .forEach { slot ->
                OverlaySlot(
                    slot = slot,
                    config = config,
                    state = state,
                    overlayState = overlayState,
                )
            }
    }
}

private suspend fun revealAndAutoHide(
    controller: ScreensaverController,
    fadeAlpha: Animatable<Float, *>,
    revealTimeoutMs: Long,
    fadeOutMs: Long,
) {
    fadeAlpha.snapTo(1f)
    controller.onOverlaysBusy()
    delay(revealTimeoutMs)
    fadeAlpha.animateTo(
        targetValue = 0f,
        animationSpec = tween(durationMillis = fadeOutMs.toInt(), easing = LinearEasing),
    )
    controller.onOverlaysReady()
}

private fun OverlayCorner.alignment(): Alignment =
    when (this) {
        OverlayCorner.TOP_LEFT -> Alignment.TopStart
        OverlayCorner.TOP_RIGHT -> Alignment.TopEnd
        OverlayCorner.BOTTOM_LEFT -> Alignment.BottomStart
        OverlayCorner.BOTTOM_RIGHT -> Alignment.BottomEnd
    }

private val OverlayCorner.isTop: Boolean
    get() = this == OverlayCorner.TOP_LEFT || this == OverlayCorner.TOP_RIGHT

private val OverlayCorner.isRight: Boolean
    get() = this == OverlayCorner.TOP_RIGHT || this == OverlayCorner.BOTTOM_RIGHT
