package com.neilturner.aerialviews.ui.core

import com.neilturner.aerialviews.models.enums.AerialMediaType
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlaybackCallbacksTest {
    @Test
    fun `video events apply while a video is on screen`() {
        assertTrue(PlaybackCallbacks.videoEventAppliesTo(AerialMediaType.VIDEO))
    }

    @Test
    fun `a late video event does not advance a photo`() {
        assertFalse(PlaybackCallbacks.videoEventAppliesTo(AerialMediaType.IMAGE))
    }

    @Test
    fun `video events are ignored before playback starts`() {
        assertFalse(PlaybackCallbacks.videoEventAppliesTo(null))
    }
}
