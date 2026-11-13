package com.neilturner.aerialviews.ui.overlays.compose

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween

// Both lines are animated by ViewPropertyAnimator in the View engine, whose default interpolator is
// AccelerateDecelerateInterpolator; Compose's default (fast-out-slow-in) drops away from full
// brightness too quickly at the start.
private val OVERLAY_FADE_EASING = CubicBezierEasing(0.4f, 0f, 0.6f, 1f)

/**
 * The alpha one overlay line is drawn at, for the "fade out, swap the text, fade back in" sequence
 * both engines use when their content changes.
 *
 * The class exists so a test can watch the value: a node's own `alpha` modifier is not part of its
 * bitmap under Robolectric, so the curve cannot be read from a screenshot the way the device
 * recordings do it.
 */
class OverlayFade(
    private val fadeMillis: Int,
) {
    val alpha = Animatable(1f)

    suspend fun fadeOut() = alpha.animateTo(0f, tween(fadeMillis, easing = OVERLAY_FADE_EASING))

    suspend fun fadeIn() = alpha.animateTo(1f, tween(fadeMillis, easing = OVERLAY_FADE_EASING))

    /**
     * Nothing is on screen, so the next item has to fade up from zero: the View leaves a hidden
     * overlay at alpha 0 before fading it in.
     */
    suspend fun snapHidden() = alpha.snapTo(0f)

    /** A new item starts fully visible, as the View's overlays are left by their render calls. */
    suspend fun reset() = alpha.snapTo(1f)
}
