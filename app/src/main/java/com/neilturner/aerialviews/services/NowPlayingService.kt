package com.neilturner.aerialviews.services

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Bundle
import androidx.core.content.getSystemService
import com.neilturner.aerialviews.utils.PermissionHelper
import me.kosert.flowbus.GlobalBus
import timber.log.Timber

// Thanks to @Spocky for his help with this feature!
// Based on code from https://github.com/jathak/musicwidget/blob/master/app/src/main/java/xyz/jathak/musicwidget/NotificationListener.java
class NowPlayingService(
    private val context: Context,
) : MediaController.Callback(),
    MediaSessionManager.OnActiveSessionsChangedListener {
    private val notificationListener = ComponentName(context, NotificationService::class.java)
    private val hasPermission = PermissionHelper.hasNotificationListenerPermission(context)
    private var sessionManager: MediaSessionManager? = null
    private var activeController: MediaController? = null
    private var metadata: MediaMetadata? = null
    private var active = false
    private var lastSentEvent: MusicEvent? = null

    init {
        if (hasPermission) {
            try {
                setupSession()
            } catch (e: Exception) {
                Timber.e(e, "Error setting up session")
            }
        } else {
            Timber.i("No permission given to access media sessions")
        }
    }

    private fun setupSession() {
        Timber.i("Setting up Now Playing session")
        sessionManager = context.getSystemService<MediaSessionManager>()
        sessionManager?.addOnActiveSessionsChangedListener(this, notificationListener)
        val controllers = sessionManager?.getActiveSessions(notificationListener)
        activeController = pickController(controllers)
        activeController?.let {
            it.registerCallback(this)
            metadata = it.metadata
            updateMetadata("setupSession", "initial setup")
        }
    }

    private fun pickController(controllers: MutableList<MediaController>?): MediaController? {
        controllers?.forEach {
            if (isActive(it)) {
                Timber.i("Using controller: ${it.packageName}")
                return it
            }
        }
        if (controllers?.isNotEmpty() == true) {
            Timber.i("No controller playing music, trying first one")
            return controllers.first()
        }

        Timber.i("No controllers found.")
        return null
    }

    private fun unregisterCurrentController() {
        activeController?.unregisterCallback(this)
    }

    override fun onActiveSessionsChanged(controllers: MutableList<MediaController>?) {
        Timber.i("onActiveSessionsChanged")
        unregisterCurrentController()
        activeController = pickController(controllers)
        if (activeController != null) {
            activeController?.let {
                it.registerCallback(this)
                metadata = it.metadata
                updateMetadata("onActiveSessionsChanged", "new controller: ${it.packageName}")
            }
        } else {
            // No active sessions left - clear the display
            Timber.i("No active sessions remaining")
            metadata = null
            postStoppedEvent("onActiveSessionsChanged", "no sessions left")
        }
    }

    override fun onSessionEvent(
        event: String,
        extras: Bundle?,
    ) {
        Timber.i("onSessionEvent: $event")
        super.onSessionEvent(event, extras)
        updateMetadata("onSessionEvent", "event: $event")
    }

    override fun onMetadataChanged(metadata: MediaMetadata?) {
        Timber.i("onMetadataChanged")
        super.onMetadataChanged(metadata)
        this.metadata = metadata
        updateMetadata("onMetadataChanged", "metadata updated")
    }

    override fun onPlaybackStateChanged(state: PlaybackState?) {
        val stateName = state?.state?.let { playbackStateToString(it) } ?: "null"
        Timber.i("onPlaybackStateChanged: $stateName")
        super.onPlaybackStateChanged(state)
        active = isActive()
        updateMetadata("onPlaybackStateChanged", "state: $stateName, active: $active")
    }

    override fun onSessionDestroyed() {
        Timber.i("onSessionDestroyed")
        activeController = null
        metadata = null
        postStoppedEvent("onSessionDestroyed", "session ended")
        super.onSessionDestroyed()
    }

    private fun postStoppedEvent(caller: String, reason: String) {
        val stoppedEvent = MusicEvent(state = MusicEvent.PlaybackState.STOPPED)
        if (lastSentEvent?.state != MusicEvent.PlaybackState.STOPPED) {
            Timber.i("[$caller] Posting STOPPED event ($reason)")
            lastSentEvent = stoppedEvent
            GlobalBus.post(stoppedEvent)
        } else {
            Timber.i("[$caller] Skipping STOPPED event - already stopped ($reason)")
        }
    }

    private fun updateMetadata(caller: String, reason: String) {
        if (metadata == null) {
            Timber.i("[$caller] updateMetadata - null metadata ($reason)")
            return
        }
        active = isActive() // Don't remove!

        val musicEvent = if (active) {
            metadata?.let {
                val song = it.getString(MediaMetadata.METADATA_KEY_TITLE) ?: ""
                val artist = it.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: ""
                // Only send PLAYING event if we have actual content
                if (artist.isNotBlank() || song.isNotBlank()) {
                    MusicEvent(artist, song, MusicEvent.PlaybackState.PLAYING)
                } else {
                    null
                }
            }
        } else {
            // Not active - send STOPPED to clear display
            MusicEvent(state = MusicEvent.PlaybackState.STOPPED)
        }

        // Only post if event is different from last sent (deduplication)
        if (musicEvent != null && musicEvent != lastSentEvent) {
            Timber.i("[$caller] Posting $musicEvent ($reason)")
            lastSentEvent = musicEvent
            GlobalBus.post(musicEvent)
        } else if (musicEvent == null) {
            Timber.i("[$caller] Skipping - no content ($reason)")
        } else {
            Timber.i("[$caller] Skipping - duplicate ($reason)")
        }
    }

    private fun playbackStateToString(state: Int): String = when (state) {
        PlaybackState.STATE_NONE -> "NONE"
        PlaybackState.STATE_STOPPED -> "STOPPED"
        PlaybackState.STATE_PAUSED -> "PAUSED"
        PlaybackState.STATE_PLAYING -> "PLAYING"
        PlaybackState.STATE_FAST_FORWARDING -> "FAST_FORWARDING"
        PlaybackState.STATE_REWINDING -> "REWINDING"
        PlaybackState.STATE_BUFFERING -> "BUFFERING"
        PlaybackState.STATE_ERROR -> "ERROR"
        PlaybackState.STATE_CONNECTING -> "CONNECTING"
        PlaybackState.STATE_SKIPPING_TO_PREVIOUS -> "SKIPPING_TO_PREVIOUS"
        PlaybackState.STATE_SKIPPING_TO_NEXT -> "SKIPPING_TO_NEXT"
        PlaybackState.STATE_SKIPPING_TO_QUEUE_ITEM -> "SKIPPING_TO_QUEUE_ITEM"
        else -> "UNKNOWN($state)"
    }

    private fun isActive(controller: MediaController? = activeController): Boolean {
        val state = controller?.playbackState?.state ?: PlaybackState.STATE_NONE
        return state == PlaybackState.STATE_PLAYING ||
            state == PlaybackState.STATE_BUFFERING ||
            state == PlaybackState.STATE_FAST_FORWARDING ||
            state == PlaybackState.STATE_REWINDING
    }

    fun nextTrack() = activeController?.transportControls?.skipToNext()

    fun previousTrack() = activeController?.transportControls?.skipToPrevious()

    fun stop() {
        unregisterCurrentController()
        activeController = null
        metadata = null
        postStoppedEvent("stop", "service stopping")
        sessionManager?.removeOnActiveSessionsChangedListener(this)
    }
}

data class MusicEvent(
    val artist: String = "",
    val song: String = "",
    val state: PlaybackState = PlaybackState.STOPPED,
) {
    enum class PlaybackState { PLAYING, STOPPED }

    fun hasContent() = artist.isNotBlank() || song.isNotBlank()
}
