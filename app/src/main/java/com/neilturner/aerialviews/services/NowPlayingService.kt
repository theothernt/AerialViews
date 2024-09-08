package com.neilturner.aerialviews.services

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import androidx.core.content.getSystemService
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.utils.PermissionHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import me.kosert.flowbus.GlobalBus
import timber.log.Timber

// Thanks to @Spocky for his help with this feature!
class NowPlayingService(private val context: Context, private val prefs: GeneralPrefs) :
    MediaSessionManager.OnActiveSessionsChangedListener,
    MediaController.Callback() {
    private val coroutineScope = CoroutineScope(Dispatchers.Main)
    private val notificationListener = ComponentName(context, NotificationService::class.java)
    private val hasPermission = PermissionHelper.hasNotificationListenerPermission(context)
    private var sessionManager: MediaSessionManager? = null
    private var controllers = listOf<MediaController>()
    private var music = MusicEvent()

    init {
        coroutineScope.launch {
            if (hasPermission) {
                setupSession()
            } else {
                Timber.i("No permission given to access media sessions")
            }
        }
    }

    override fun onMetadataChanged(metadata: MediaMetadata?) {
        super.onMetadataChanged(metadata)
        updateNowPlaying(metadata, null)
    }

    override fun onPlaybackStateChanged(state: PlaybackState?) {
        super.onPlaybackStateChanged(state)

        if (state == null) {
            return
        }

        val active = isActive(state.state)
        updateNowPlaying(null, active)
    }

    private fun setupSession() {
        sessionManager = context.getSystemService<MediaSessionManager>()

        // Set metadata for active sessions
        onActiveSessionsChanged(sessionManager?.getActiveSessions(notificationListener))
        if (controllers.isNotEmpty()) {
            val activeController = controllers.first()
            val active = isActive(activeController.playbackState?.state)
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
        metadata?.let {
            val song = metadata.getString(MediaMetadata.METADATA_KEY_TITLE)
            val artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)
            music = MusicEvent(artist, song)
        }

        if (active == true) {
            GlobalBus.post(music)
        } else {
            GlobalBus.post(MusicEvent())
        }
    }

    override fun onActiveSessionsChanged(controllers: MutableList<MediaController>?) {
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

    private fun isActive(state: Int?): Boolean {
        return (
            state != PlaybackState.STATE_STOPPED &&
                state != PlaybackState.STATE_PAUSED &&
                state != PlaybackState.STATE_ERROR &&
                state != PlaybackState.STATE_BUFFERING &&
                state != PlaybackState.STATE_NONE
        )
    }
}

data class MusicEvent(
    val artist: String = "",
    val song: String = "",
)
