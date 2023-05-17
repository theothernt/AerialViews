package com.neilturner.aerialviews.utils

import com.google.android.exoplayer2.DefaultLoadControl
import com.neilturner.aerialviews.models.BufferingStrategy

object PlayerHelper {

    fun bufferingStrategy(strategy: BufferingStrategy): DefaultLoadControl.Builder {
        val loadControlBuilder = DefaultLoadControl.Builder()

        // Defaults
        // val minBuffer = 50_000
        // val maxBuffer = 50_000
        // val bufferForPlayback = 2500
        // val bufferForPlaybackAfterRebuffer = 5000

        if (strategy == BufferingStrategy.FAST_START) {
            // Buffer sizes while playing
            val minBuffer = 5000
            val maxBuffer = 40_000

            // Initial buffer size to start playback
            val bufferForPlayback = 1024
            val bufferForPlaybackAfterRebuffer = 1024

            loadControlBuilder
                .setBufferDurationsMs(
                    minBuffer,
                    maxBuffer,
                    bufferForPlayback,
                    bufferForPlaybackAfterRebuffer
                )
        }

        if (strategy == BufferingStrategy.LARGER) {
            // Buffer sizes while playing
            val minBuffer = 75_000
            val maxBuffer = 75_000

            // Initial buffer size to start playback
            val bufferForPlayback = 4000
            val bufferForPlaybackAfterRebuffer = 8000

            loadControlBuilder
                .setBufferDurationsMs(
                    minBuffer,
                    maxBuffer,
                    bufferForPlayback,
                    bufferForPlaybackAfterRebuffer
                )
        }

        if (strategy == BufferingStrategy.SMALLER) {
            // Buffer sizes while playing
            val minBuffer = 5000
            val maxBuffer = 5000

            // Initial buffer size to start playback
            val bufferForPlayback = 1024
            val bufferForPlaybackAfterRebuffer = 1024

            loadControlBuilder
                .setBufferDurationsMs(
                    minBuffer,
                    maxBuffer,
                    bufferForPlayback,
                    bufferForPlaybackAfterRebuffer
                )
        }

        return loadControlBuilder
    }
}
