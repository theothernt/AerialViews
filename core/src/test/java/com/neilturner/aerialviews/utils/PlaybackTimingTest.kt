package com.neilturner.aerialviews.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PlaybackTimingTest {
    @Test
    fun `whole loops never exceed the length limit`() {
        assertEquals(3, PlaybackTiming.wholeLoops(durationMs = 30_000, limitMs = 100_000))
        assertEquals(4, PlaybackTiming.wholeLoops(durationMs = 30_000, limitMs = 120_000))
        assertEquals(8, PlaybackTiming.wholeLoops(durationMs = 12_000, limitMs = 100_000))
    }

    @Test
    fun `a clip longer than the limit does not loop`() {
        assertEquals(0, PlaybackTiming.wholeLoops(durationMs = 30_000, limitMs = 20_000))
        assertEquals(30_000, PlaybackTiming.loopingTargetDuration(durationMs = 30_000, limitMs = 20_000))
    }

    @Test
    fun `looping target is the whole-loop duration`() {
        assertEquals(90_000, PlaybackTiming.loopingTargetDuration(durationMs = 30_000, limitMs = 100_000))
        assertEquals(120_000, PlaybackTiming.loopingTargetDuration(durationMs = 30_000, limitMs = 120_000))
        assertEquals(96_000, PlaybackTiming.loopingTargetDuration(durationMs = 12_000, limitMs = 100_000))
    }

    @Test
    fun `media time converts to wall clock at the current speed`() {
        assertEquals(60_000, PlaybackTiming.mediaToWallClock(mediaMs = 120_000, speed = 2.0))
        assertEquals(240_000, PlaybackTiming.mediaToWallClock(mediaMs = 120_000, speed = 0.5))
        assertEquals(150_000, PlaybackTiming.mediaToWallClock(mediaMs = 120_000, speed = 0.8))
    }

    @Test
    fun `negative media time converts to no wall clock time`() {
        assertEquals(0, PlaybackTiming.mediaToWallClock(mediaMs = -5_000, speed = 2.0))
    }

    @Test
    fun `finish delay uses remaining media time and keeps the fade out`() {
        assertEquals(60_000, PlaybackTiming.finishDelayMs(mediaRemainingMs = 120_000, speed = 2.0, fadeOutMs = 0))
        assertEquals(14_200, PlaybackTiming.finishDelayMs(mediaRemainingMs = 30_000, speed = 2.0, fadeOutMs = 800))
        assertEquals(60_000, PlaybackTiming.finishDelayMs(mediaRemainingMs = 60_000, speed = 1.0, fadeOutMs = 0))
    }

    @Test
    fun `finish delay never drops below one second`() {
        assertEquals(1_000, PlaybackTiming.finishDelayMs(mediaRemainingMs = 0, speed = 1.0, fadeOutMs = 0))
        assertEquals(1_000, PlaybackTiming.finishDelayMs(mediaRemainingMs = -5_000, speed = 2.0, fadeOutMs = 800))
        assertEquals(1_000, PlaybackTiming.finishDelayMs(mediaRemainingMs = 1_500, speed = 1.0, fadeOutMs = 800))
    }
}
