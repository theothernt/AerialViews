package com.neilturner.aerialviews.ui.overlays.compose

import com.neilturner.aerialviews.ui.controls.ProgressState

/**
 * What the Compose progress bar should do for one progress event.
 *
 * The XML `ProgressBar` drives a `ValueAnimator` over a View's width; the Compose bar drives an
 * `Animatable` fraction, so the same four states map onto these motions.
 */
sealed interface ProgressBarMotion {
    /** Grow the filled part from [from] to [to] over [durationMillis], linearly. */
    data class Animate(
        val from: Float,
        val to: Float,
        val durationMillis: Long,
    ) : ProgressBarMotion

    /** Hold the current fraction: the running animation stops where it is. */
    data object Pause : ProgressBarMotion

    /** Continue from the current fraction to the full width. */
    data object Resume : ProgressBarMotion

    /** Empty the bar. */
    data object Reset : ProgressBarMotion
}

/**
 * The filled fraction for a playback position, or null when there is nothing to draw.
 *
 * `ProgressBar.animateWidth()` bails out unless the parent has been measured and the position sits
 * within a positive duration, so the same inputs return null here instead of dividing by zero or
 * letting the bar run past its own bounds.
 */
fun progressFraction(
    position: Long,
    duration: Long,
): Float? {
    if (duration <= 0L || position < 0L || position > duration) return null
    return (position.toDouble() / duration.toDouble()).toFloat().coerceIn(0f, 1f)
}

/**
 * Maps a progress event onto the bar's motion.
 *
 * A START heads for the full width over the item's whole duration, exactly as the View's animator
 * does; a START carrying no usable position resets instead.
 */
fun progressBarMotion(
    state: ProgressState,
    position: Long,
    duration: Long,
): ProgressBarMotion =
    when (state) {
        ProgressState.START -> {
            progressFraction(position, duration)?.let { from ->
                ProgressBarMotion.Animate(from = from, to = 1f, durationMillis = duration)
            } ?: ProgressBarMotion.Reset
        }

        ProgressState.PAUSE -> {
            ProgressBarMotion.Pause
        }

        ProgressState.RESUME -> {
            ProgressBarMotion.Resume
        }

        ProgressState.RESET -> {
            ProgressBarMotion.Reset
        }
    }
