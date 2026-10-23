package com.neilturner.aerialviews.services

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class MusicErrorRecoveryTest {
    @Test
    fun `skips to the following track when one fails`() {
        val next =
            MusicErrorRecovery.nextPlayableIndex(
                currentIndex = 1,
                totalTracks = 5,
                failedIndices = setOf(1),
                repeat = false,
            )

        assertEquals(2, next)
    }

    @Test
    fun `skips tracks that already failed`() {
        val next =
            MusicErrorRecovery.nextPlayableIndex(
                currentIndex = 0,
                totalTracks = 4,
                failedIndices = setOf(0, 1, 2),
                repeat = false,
            )

        assertEquals(3, next)
    }

    @Test
    fun `wraps around to the start when repeating`() {
        val next =
            MusicErrorRecovery.nextPlayableIndex(
                currentIndex = 3,
                totalTracks = 4,
                failedIndices = setOf(3),
                repeat = true,
            )

        assertEquals(0, next)
    }

    @Test
    fun `returns null at the end of the playlist when not repeating`() {
        val next =
            MusicErrorRecovery.nextPlayableIndex(
                currentIndex = 2,
                totalTracks = 3,
                failedIndices = setOf(2),
                repeat = false,
            )

        assertNull(next)
    }

    @Test
    fun `returns null when every remaining track has failed`() {
        val next =
            MusicErrorRecovery.nextPlayableIndex(
                currentIndex = 0,
                totalTracks = 3,
                failedIndices = setOf(0, 1, 2),
                repeat = true,
            )

        assertNull(next)
    }

    @Test
    fun `returns null for an empty playlist`() {
        val next =
            MusicErrorRecovery.nextPlayableIndex(
                currentIndex = 0,
                totalTracks = 0,
                failedIndices = emptySet(),
                repeat = true,
            )

        assertNull(next)
    }

    @Test
    fun `keeps recovering below the consecutive error limit`() {
        assertFalse(
            MusicErrorRecovery.shouldGiveUp(
                consecutiveErrors = MusicErrorRecovery.MAX_CONSECUTIVE_ERRORS - 1,
                failedTrackCount = 2,
                totalTracks = 10,
            ),
        )
    }

    @Test
    fun `gives up once the consecutive error limit is reached`() {
        assertTrue(
            MusicErrorRecovery.shouldGiveUp(
                consecutiveErrors = MusicErrorRecovery.MAX_CONSECUTIVE_ERRORS,
                failedTrackCount = 3,
                totalTracks = 10,
            ),
        )
    }

    @Test
    fun `gives up once every track has failed`() {
        assertTrue(
            MusicErrorRecovery.shouldGiveUp(
                consecutiveErrors = 1,
                failedTrackCount = 2,
                totalTracks = 2,
            ),
        )
    }
}
