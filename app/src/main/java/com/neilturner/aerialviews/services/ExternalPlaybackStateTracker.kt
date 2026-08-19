package com.neilturner.aerialviews.services

import android.media.session.PlaybackState

internal data class MediaSessionPlayback(
    val packageName: String,
    val state: Int,
)

internal class ExternalPlaybackStateTracker(
    private val ownPackageName: String,
    private val onStateChanged: (Boolean) -> Unit,
) {
    private var lastState: Boolean? = null

    fun update(sessions: Iterable<MediaSessionPlayback>) {
        val isExternalPlaybackActive =
            sessions.any { session ->
                session.packageName != ownPackageName && session.state == PlaybackState.STATE_PLAYING
            }

        if (lastState == isExternalPlaybackActive) return

        lastState = isExternalPlaybackActive
        onStateChanged(isExternalPlaybackActive)
    }
}
