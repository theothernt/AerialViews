package com.neilturner.aerialviews.utils

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
     * Fades volume from 0 → 1 over [durationMs].
     * If duration is ≤ 0, volume is set to 1 instantly.
     */
    fun fadeIn(durationMs: Long = 500) {
        cancel()
        if (durationMs <= 0L) {
            setVolume(1f)
            return
        }
        val startVolume = 0f
        val targetVolume = 1f
        animator = ValueAnimator.ofFloat(startVolume, targetVolume).apply {
            duration = durationMs
            addUpdateListener { setVolume(it.animatedValue as Float) }
            start()
        }
    }

    /**
     * Fades volume from current → 0 over [durationMs], then calls [onComplete].
     * If duration is ≤ 0, volume is set to 0 instantly and [onComplete] fires immediately.
     */
    fun fadeOut(durationMs: Long = 500, onComplete: (() -> Unit)? = null) {
        cancel()
        val startVolume = getVolume()
        if (startVolume <= 0f || durationMs <= 0L) {
            setVolume(0f)
            onComplete?.invoke()
            return
        }
        animator = ValueAnimator.ofFloat(startVolume, 0f).apply {
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
