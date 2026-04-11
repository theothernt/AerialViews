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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
    private var lastMusicEvent: MusicEvent? = null
    private val serviceJob = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Main + serviceJob)
    private var updateActiveSessionJob: Job? = null

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
        logControllers("setupSession", controllers)
        updateActiveSession(controllers)
    }

    private fun updateActiveSession(controllers: MutableList<MediaController>?) {
        val selectedController = pickController(controllers)
        Timber.i(
            "updateActiveSession current=%s selected=%s",
            activeController.describeController(),
            selectedController.describeController(),
        )
        if (selectedController?.sessionToken == activeController?.sessionToken) {
            if (selectedController == null) {
                clearNowPlaying("no active controller available")
            }
            return
        }
        unregisterCurrentController()
        activeController = selectedController
        if (activeController == null) {
            metadata = null
            clearNowPlaying("active controller changed to null")
            return
        }

        activeController?.let {
            it.registerCallback(this)
            metadata = it.metadata
            Timber.i(
                "Registered callback for %s with playback=%s metadata=%s",
                it.describeController(),
                stateToString(it.playbackState?.state ?: -1),
                metadataSummary(it.metadata),
            )
            updateMetadata()
        }
    }

    private fun pickController(controllers: MutableList<MediaController>?): MediaController? {
        logControllers("pickController", controllers)
        controllers?.forEach {
            if (isActive(it)) {
                Timber.i("Using controller: ${it.packageName}")
                return it
            }
        }

        Timber.i("No controllers found.")
        return null
    }

    private fun unregisterCurrentController() {
        activeController?.unregisterCallback(this)
    }

    override fun onActiveSessionsChanged(controllers: MutableList<MediaController>?) {
        Timber.i("onActiveSessionsChanged")
        logControllers("onActiveSessionsChanged", controllers)
        updateActiveSession(controllers)

        updateActiveSessionJob?.cancel()
        updateActiveSessionJob =
            scope.launch {
                // Check every 500ms for 3 seconds (6 times)
                repeat(6) {
                    delay(500)
                    Timber.i("Delayed check for active sessions")
                    val freshControllers = sessionManager?.getActiveSessions(notificationListener)
                    logControllers("delayedCheck[$it]", freshControllers)
                    updateActiveSession(freshControllers)
                }
            }
    }

    override fun onSessionEvent(
        event: String,
        extras: Bundle?,
    ) {
        Timber.i("onSessionEvent: $event")
        super.onSessionEvent(event, extras)
        updateMetadata()
    }

    override fun onMetadataChanged(metadata: MediaMetadata?) {
        Timber.i("onMetadataChanged: ${metadataSummary(metadata)}")
        super.onMetadataChanged(metadata)
        this.metadata = metadata
        updateMetadata()
    }

    override fun onPlaybackStateChanged(state: PlaybackState?) {
        Timber.i(
            "onPlaybackStateChanged: %s actions=%s activeController=%s",
            stateToString(state?.state ?: -1),
            state?.actions ?: 0,
            activeController.describeController(),
        )
        super.onPlaybackStateChanged(state)
        active = isActive()
        updateMetadata()
    }

    override fun onSessionDestroyed() {
        Timber.i("onSessionDestroyed for ${activeController.describeController()}")
        activeController = null
        metadata = null
        clearNowPlaying("session destroyed")
        super.onSessionDestroyed()
    }

    private fun updateMetadata() {
        if (metadata == null) {
            Timber.i("updateMetadata - null metadata for ${activeController.describeController()}")
            clearNowPlaying("metadata became null")
            return
        }
        active = isActive()
        val musicEvent =
            metadata
                ?.let {
                    val song = it.getString(MediaMetadata.METADATA_KEY_TITLE) ?: ""
                    val artist = it.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: ""
                    MusicEvent(artist, song)
                }.takeIf { active } ?: MusicEvent()

        if (musicEvent == lastMusicEvent) {
            Timber.i("updateMetadata - unchanged event: $musicEvent")
            return
        }
        postMusicEvent(musicEvent, "updateMetadata active=$active metadata=${metadataSummary(metadata)}")
    }

    private fun isActive(controller: MediaController? = activeController): Boolean {
        val state = controller?.playbackState?.state ?: PlaybackState.STATE_NONE
        Timber.i("Playback state for ${controller?.packageName}: ${stateToString(state)}")
        return state == PlaybackState.STATE_PLAYING
    }

    private fun clearNowPlaying(reason: String) {
        postMusicEvent(MusicEvent(), "clearNowPlaying reason=$reason")
    }

    private fun postMusicEvent(
        event: MusicEvent,
        reason: String,
    ) {
        if (event == lastMusicEvent) {
            Timber.i("Skipping duplicate music event ($reason): $event")
            return
        }
        lastMusicEvent = event
        Timber.i("Posting music event ($reason): $event")
        GlobalBus.post(event)
    }

    private fun logControllers(
        source: String,
        controllers: List<MediaController>?,
    ) {
        if (controllers.isNullOrEmpty()) {
            Timber.i("$source controllers: []")
            return
        }
        val summary = controllers.joinToString(" | ") { it.describeController() }
        Timber.i("$source controllers(${controllers.size}): $summary")
    }

    private fun MediaController?.describeController(): String {
        if (this == null) return "null"
        val state = playbackState?.state ?: PlaybackState.STATE_NONE
        return "$packageName[${stateToString(state)}]"
    }

    private fun metadataSummary(metadata: MediaMetadata?): String {
        if (metadata == null) return "null"
        val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty()
        val artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST).orEmpty()
        val album = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM).orEmpty()
        return "title='$title', artist='$artist', album='$album'"
    }

    private fun stateToString(state: Int): String =
        when (state) {
            PlaybackState.STATE_NONE -> "STATE_NONE"
            PlaybackState.STATE_STOPPED -> "STATE_STOPPED"
            PlaybackState.STATE_PAUSED -> "STATE_PAUSED"
            PlaybackState.STATE_PLAYING -> "STATE_PLAYING"
            PlaybackState.STATE_FAST_FORWARDING -> "STATE_FAST_FORWARDING"
            PlaybackState.STATE_REWINDING -> "STATE_REWINDING"
            PlaybackState.STATE_BUFFERING -> "STATE_BUFFERING"
            PlaybackState.STATE_ERROR -> "STATE_ERROR"
            PlaybackState.STATE_CONNECTING -> "STATE_CONNECTING"
            PlaybackState.STATE_SKIPPING_TO_PREVIOUS -> "STATE_SKIPPING_TO_PREVIOUS"
            PlaybackState.STATE_SKIPPING_TO_NEXT -> "STATE_SKIPPING_TO_NEXT"
            PlaybackState.STATE_SKIPPING_TO_QUEUE_ITEM -> "STATE_SKIPPING_TO_QUEUE_ITEM"
            else -> "UNKNOWN ($state)"
        }

    fun nextTrack() = activeController?.transportControls?.skipToNext()

    fun previousTrack() = activeController?.transportControls?.skipToPrevious()

    fun stop() {
        serviceJob.cancel()
        unregisterCurrentController()
        activeController = null
        metadata = null
        sessionManager?.removeOnActiveSessionsChangedListener(this)
    }
}

data class MusicEvent(
    val artist: String = "",
    val song: String = "",
)
