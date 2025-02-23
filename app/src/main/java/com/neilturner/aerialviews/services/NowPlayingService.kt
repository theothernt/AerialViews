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
class NowPlayingServiceAlt(
    private val context: Context,
) : MediaController.Callback(),
    MediaSessionManager.OnActiveSessionsChangedListener {
    private val notificationListener = ComponentName(context, NotificationService::class.java)
    private val hasPermission = PermissionHelper.hasNotificationListenerPermission(context)
    private var sessionManager: MediaSessionManager? = null
    private var activeController: MediaController? = null
    private var metadata: MediaMetadata? = null
    private var active = false

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
            updateMetadata()
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

    override fun onActiveSessionsChanged(controllers: MutableList<MediaController>?) {
        Timber.i("onActiveSessionsChanged")
        activeController = pickController(controllers)
        activeController?.let {
            it.registerCallback(this)
            metadata = it.metadata
            updateMetadata()
        }
    }

    override fun onSessionEvent(
        event: String,
        extras: Bundle?,
    ) {
        Timber.i("onSessionEvent")
        super.onSessionEvent(event, extras)
        updateMetadata()
    }

    override fun onMetadataChanged(metadata: MediaMetadata?) {
        Timber.i("onMetadataChanged")
        super.onMetadataChanged(metadata)
        this.metadata = metadata
        updateMetadata()
    }

    override fun onPlaybackStateChanged(state: PlaybackState?) {
        Timber.i("onPlaybackStateChanged")
        super.onPlaybackStateChanged(state)
        active = isActive()
        updateMetadata()
    }

    override fun onSessionDestroyed() {
        Timber.i("onSessionDestroyed")
        activeController = null
        metadata = null
        super.onSessionDestroyed()
    }

    private fun updateMetadata() {
        if (metadata == null) {
            Timber.i("updateMetadata - null")
            return
        }

        val musicEvent =
            metadata
                ?.let {
                    val song = it.getString(MediaMetadata.METADATA_KEY_TITLE) ?: ""
                    val artist = it.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: ""
                    MusicEvent(artist, song)
                }.takeIf { active == true } ?: MusicEvent()

        Timber.i("updateMetadata - trying $musicEvent")
        GlobalBus.post(musicEvent)
    }

    fun isActive(controller: MediaController? = activeController): Boolean {
        val state = controller?.playbackState?.state ?: PlaybackState.STATE_NONE
        return state == PlaybackState.STATE_PLAYING ||
                state == PlaybackState.STATE_BUFFERING ||
                state == PlaybackState.STATE_FAST_FORWARDING ||
                state == PlaybackState.STATE_REWINDING
    }

    fun stop() {
        activeController = null
        metadata = null
        sessionManager?.removeOnActiveSessionsChangedListener(this)
    }
}

data class MusicEvent(
    val artist: String = "",
    val song: String = "",
)