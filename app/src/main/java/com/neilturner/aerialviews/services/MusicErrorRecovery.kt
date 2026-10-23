package com.neilturner.aerialviews.services

/**
 * Pure decision helpers for recovering from a background music playback error.
 *
 * A single unplayable track must not end background music for the whole session: the player skips
 * to the next track that has not failed yet. Recovery is bounded so that an unreachable source
 * (eg. an offline network share) cannot loop through the playlist forever.
 */
internal object MusicErrorRecovery {
    /** Consecutive failures tolerated before background music is abandoned. */
    const val MAX_CONSECUTIVE_ERRORS = 3

    /**
     * Returns the next track worth trying after [currentIndex] failed, or `null` when there is
     * nothing left to play. [failedIndices] are skipped so one bad file is not retried on every
     * pass through the playlist.
     */
    fun nextPlayableIndex(
        currentIndex: Int,
        totalTracks: Int,
        failedIndices: Set<Int>,
        repeat: Boolean,
    ): Int? {
        if (totalTracks <= 0) return null

        val start = currentIndex + 1
        val candidates =
            if (repeat) {
                (start until totalTracks) + (0 until minOf(start, totalTracks))
            } else {
                start until totalTracks
            }
        return candidates.firstOrNull { it !in failedIndices }
    }

    /**
     * Returns `true` when playback should not be recovered any further: either every track has
     * failed, or too many errors happened in a row (no track played successfully since).
     */
    fun shouldGiveUp(
        consecutiveErrors: Int,
        failedTrackCount: Int,
        totalTracks: Int,
        maxConsecutiveErrors: Int = MAX_CONSECUTIVE_ERRORS,
    ): Boolean = consecutiveErrors >= maxConsecutiveErrors || failedTrackCount >= totalTracks
}
