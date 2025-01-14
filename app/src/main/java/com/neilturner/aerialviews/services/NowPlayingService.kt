package com.neilturner.aerialviews.services

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import androidx.core.content.getSystemService
import com.neilturner.aerialviews.utils.PermissionHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import me.kosert.flowbus.GlobalBus
import timber.log.Timber

// Thanks to @Spocky for his help with this feature!
class NowPlayingService(
    private val context: Context,
) : MediaController.Callback(),
    MediaSessionManager.OnActiveSessionsChangedListener {
    private val coroutineScope = CoroutineScope(Dispatchers.Main)
    private val notificationListener = ComponentName(context, NotificationService::class.java)
    private val hasPermission = PermissionHelper.hasNotificationListenerPermission(context)
    private var sessionManager: MediaSessionManager? = null
    private var controllers = listOf<MediaController>()

    init {
        coroutineScope.launch {
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
    }

    override fun onMetadataChanged(metadata: MediaMetadata?) {
        Timber.i("onMetadataChanged")
        super.onMetadataChanged(metadata)
        try {
            updateNowPlaying(metadata, null)
        } catch (ex: Exception) {
            Timber.e(ex, "Error setting Now Playing info")
        }
    }

    override fun onPlaybackStateChanged(state: PlaybackState?) {
        Timber.i("onPlaybackStateChanged")
        super.onPlaybackStateChanged(state)

        if (state == null) {
            return
        }

        try {
            val active = isActive(state.state)
            updateNowPlaying(null, active)
        } catch (ex: Exception) {
            Timber.e(ex, "Error setting Now Playing info")
        }
    }

    private fun setupSession() {
        sessionManager = context.getSystemService<MediaSessionManager>()

        // Set metadata for active sessions
        onActiveSessionsChanged(sessionManager?.getActiveSessions(notificationListener))
        if (controllers.isNotEmpty()) {
            val activeController = controllers.first()
            val active = isActive(activeController.playbackState?.state)
            Timber.i("Initial state - active: $active")
            try {
                updateNowPlaying(activeController.metadata, active)
            } catch (ex: Exception) {
                Timber.e(ex, "Error setting initial Now Playing info")
            }
        }
        // Listen for future changes to active sessions
        sessionManager?.addOnActiveSessionsChangedListener(this, notificationListener)
    }

    private fun updateNowPlaying(
        metadata: MediaMetadata?,
        active: Boolean?,
    ) {
        Timber.i("updateNowPlaying - metadata: $metadata., active: $active")
        val musicEvent =
            metadata
                ?.let {
                    val song = it.getString(MediaMetadata.METADATA_KEY_TITLE) ?: ""
                    val artist = it.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: ""
                    MusicEvent(artist, song)
                }.takeIf { active == true } ?: MusicEvent()

        Timber.i("updateNowPlaying - trying $musicEvent, active: $active")
        GlobalBus.post(musicEvent)
    }

    override fun onActiveSessionsChanged(controllers: MutableList<MediaController>?) {
        Timber.i("onActiveSessionsChanged")
        unregisterAll()
        if (!controllers.isNullOrEmpty()) {
            initControllers(controllers)
        } else {
            updateNowPlaying(null, false)
        }
    }

    private fun initControllers(newControllers: List<MediaController>?) {
        controllers = if (!newControllers.isNullOrEmpty()) newControllers else emptyList()
        controllers.forEach { controller ->
            controller.registerCallback(this)
        }
    }

    private fun unregisterAll() {
        controllers.forEach { controller ->
            controller.unregisterCallback(this)
        }
    }

    fun stop() {
        unregisterAll()
        sessionManager?.removeOnActiveSessionsChangedListener(this)
        coroutineScope.cancel()
    }

    private fun isActive(state: Int?): Boolean =
        (
            state != PlaybackState.STATE_STOPPED &&
                state != PlaybackState.STATE_PAUSED &&
                state != PlaybackState.STATE_ERROR &&
                //state != PlaybackState.STATE_BUFFERING &&
                state != PlaybackState.STATE_NONE
        )
}

data class MusicEvent(
    val artist: String = "",
    val song: String = "",
)
