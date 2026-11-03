package com.neilturner.aerialviews.utils

/**
 * Playback arithmetic kept in one place because media time and wall-clock time differ whenever the
 * playback speed is not 1x.
 */
object PlaybackTiming {
    const val MIN_FINISH_DELAY_MS = 1_000L

    /**
     * Number of complete plays of [durationMs] that fit in [limitMs].
     *
     * Rounding up would play past the limit the user configured, so only whole loops count.
     */
    fun wholeLoops(
        durationMs: Long,
        limitMs: Long,
    ): Int {
        if (durationMs <= 0 || limitMs < durationMs) return 0
        return (limitMs / durationMs).toInt()
    }

    /** Media duration of [wholeLoops] plays, falling back to a single play if none fit. */
    fun loopingTargetDuration(
        durationMs: Long,
        limitMs: Long,
    ): Long {
        val loops = wholeLoops(durationMs, limitMs)
        return durationMs * loops.coerceAtLeast(1)
    }

    /** Wall-clock time needed to play [mediaMs] of media at [speed]. */
    fun mediaToWallClock(
        mediaMs: Long,
        speed: Double,
    ): Long {
        val safeSpeed = if (speed.isFinite() && speed > 0.0) speed else 1.0
        return (mediaMs.coerceAtLeast(0L) / safeSpeed).toLong()
    }

    /** Wall-clock delay before the next item, after the fade out and with a one second floor. */
    fun finishDelayMs(
        mediaRemainingMs: Long,
        speed: Double,
        fadeOutMs: Long,
    ): Long {
        val remaining = mediaToWallClock(mediaRemainingMs, speed) - fadeOutMs.coerceAtLeast(0L)
        return remaining.coerceAtLeast(MIN_FINISH_DELAY_MS)
    }
}
