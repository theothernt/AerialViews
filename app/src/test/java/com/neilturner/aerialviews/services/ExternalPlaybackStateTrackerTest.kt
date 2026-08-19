package com.neilturner.aerialviews.services

import android.media.session.PlaybackState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class ExternalPlaybackStateTrackerTest {
    private val ownPackageName = "com.neilturner.aerialviews"

    @Test
    fun `reports active when an external session is playing`() {
        val reportedStates = mutableListOf<Boolean>()
        val tracker = ExternalPlaybackStateTracker(ownPackageName, reportedStates::add)

        tracker.update(listOf(session("com.spotify.music", PlaybackState.STATE_PLAYING)))

        assertEquals(listOf(true), reportedStates)
    }

    @Test
    fun `reports inactive for paused stopped and absent sessions`() {
        val reportedStates = mutableListOf<Boolean>()
        val tracker = ExternalPlaybackStateTracker(ownPackageName, reportedStates::add)

        tracker.update(listOf(session("com.spotify.music", PlaybackState.STATE_PAUSED)))
        tracker.update(listOf(session("com.spotify.music", PlaybackState.STATE_STOPPED)))
        tracker.update(emptyList())

        assertEquals(listOf(false), reportedStates)
    }

    @Test
    fun `ignores Aerial Views sessions`() {
        val reportedStates = mutableListOf<Boolean>()
        val tracker = ExternalPlaybackStateTracker(ownPackageName, reportedStates::add)

        tracker.update(listOf(session(ownPackageName, PlaybackState.STATE_PLAYING)))

        assertEquals(listOf(false), reportedStates)
    }

    @Test
    fun `emits transitions without duplicate states`() {
        val reportedStates = mutableListOf<Boolean>()
        val tracker = ExternalPlaybackStateTracker(ownPackageName, reportedStates::add)

        tracker.update(emptyList())
        tracker.update(emptyList())
        tracker.update(listOf(session("com.spotify.music", PlaybackState.STATE_PLAYING)))
        tracker.update(listOf(session("com.spotify.music", PlaybackState.STATE_PLAYING)))
        tracker.update(emptyList())

        assertEquals(listOf(false, true, false), reportedStates)
    }

    private fun session(
        packageName: String,
        state: Int,
    ): MediaSessionPlayback =
        MediaSessionPlayback(packageName = packageName, state = state)
}
