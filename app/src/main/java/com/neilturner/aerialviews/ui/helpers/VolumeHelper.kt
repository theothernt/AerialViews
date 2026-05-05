package com.neilturner.aerialviews.ui.helpers

import android.animation.ValueAnimator

/**
 * Utility for smoothly fading the volume of a Float-based property (e.g. ExoPlayer.volume).
 *
 * Usage:
 *   val helper = VolumeHelper({ exoPlayer.volume }, { exoPlayer.volume = it })
 *   helper.fadeIn(durationMs = 500)
 *   helper.fadeOut(durationMs = 500)
 */
class VolumeHelper(
    private val getVolume: () -> Float,
    private val setVolume: (Float) -> Unit,
) {
    private var animator: ValueAnimator? = null

    /**
     * Fades volume from 0 → [targetVolume] over [durationMs].
     * If duration is ≤ 0, volume is set to [targetVolume] instantly.
     */
    fun fadeIn(
        durationMs: Long = 500,
        targetVolume: Float = 1f,
    ) {
        cancel()
        if (durationMs <= 0L) {
            setVolume(targetVolume)
            return
        }
        val startVolume = 0f
        animator =
            ValueAnimator.ofFloat(startVolume, targetVolume).apply {
                duration = durationMs
                addUpdateListener { setVolume(it.animatedValue as Float) }
                start()
            }
    }

    /**
     * Fades volume from current → 0 over [durationMs], then calls [onComplete].
     * If duration is ≤ 0, volume is set to 0 instantly and [onComplete] fires immediately.
     */
    fun fadeOut(
        durationMs: Long = 500,
        onComplete: (() -> Unit)? = null,
    ) {
        cancel()
        val startVolume = getVolume()
        if (startVolume <= 0f || durationMs <= 0L) {
            setVolume(0f)
            onComplete?.invoke()
            return
        }
        animator =
            ValueAnimator.ofFloat(startVolume, 0f).apply {
                duration = durationMs
                addUpdateListener { setVolume(it.animatedValue as Float) }
                addListener(
                    object : android.animation.AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: android.animation.Animator) {
                            animator = null
                            onComplete?.invoke()
                        }
                    },
                )
                start()
            }
    }

    fun cancel() {
        animator?.cancel()
        animator = null
    }
}
