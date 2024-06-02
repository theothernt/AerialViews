package com.neilturner.aerialviews.services

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.util.Log
import androidx.core.content.getSystemService
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.utils.PermissionHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus


class NowPlayingService(private val context: Context, private val prefs: GeneralPrefs) :
    MediaSessionManager.OnActiveSessionsChangedListener
{
    private val _nowPlaying = MutableSharedFlow<String>(replay = 0)
    val nowPlaying
        get() = _nowPlaying.asSharedFlow()

    private val coroutineScope = CoroutineScope(Dispatchers.Main) + Job()
    private val notificationListener = ComponentName(context, NotificationService::class.java)
    private val hasPermission = PermissionHelper.hasNotificationListenerPermission(context)
    private var sessionManager: MediaSessionManager? = null
    private var controllers = listOf<MediaController>()

    private val metadataListener = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) {
            super.onMetadataChanged(metadata)
            updateNowPlaying(metadata, null)
        }

        override fun onPlaybackStateChanged(state: PlaybackState?) {
            super.onPlaybackStateChanged(state)

            if (state == null) {
                return
            }

            val newState = state.state
            val active = (newState != PlaybackState.STATE_STOPPED
                    && newState != PlaybackState.STATE_PAUSED
                    && newState != PlaybackState.STATE_ERROR
                    && newState != PlaybackState.STATE_BUFFERING
                    && newState != PlaybackState.STATE_NONE)

            updateNowPlaying(null, active)
        }
    }

    init {
        coroutineScope.launch {
            if (hasPermission) {
                setupSession()
            } else {
                Log.i(TAG, "No permission given to access media sessions")
            }
        }
    }

    private fun setupSession() {
        sessionManager = context.getSystemService<MediaSessionManager>()
        onActiveSessionsChanged(sessionManager?.getActiveSessions(notificationListener))
        sessionManager?.addOnActiveSessionsChangedListener(this, notificationListener)
    }

    private fun updateNowPlaying(metadata: MediaMetadata?, active: Boolean?) {
        if (active != null) {
            Log.i(TAG, "Active: $active")
        }

        if (metadata != null) {
            val song = metadata.getString(MediaMetadata.METADATA_KEY_TITLE)?.take(40)
            val artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)?.take(40)
            Log.i(TAG, "Metadata: $song - $artist")
        }

//        if (isPlaying) {
//            _nowPlaying.emit(nowPlaying)
//        } else {
//            _nowPlaying.emit("")
//        }
    }

    override fun onActiveSessionsChanged(controllers: MutableList<MediaController>?) {
        Log.i(TAG, "onActiveSessionsChanged")
        unregisterAll()
        initControllers(controllers)
    }

    private fun initControllers(newControllers: List<MediaController>?) {
        controllers = if (!newControllers.isNullOrEmpty()) newControllers else emptyList()

        Log.i(TAG, "Registering controllers: ${controllers.size}")
        controllers.forEach { controller ->
            controller.registerCallback(metadataListener)
        }
    }

    private fun unregisterAll() {
        Log.i(TAG, "Unregistering controllers: ${controllers.size}")
        controllers.forEach { controller ->
            controller.unregisterCallback(metadataListener)
        }
    }

    fun stop() {
        unregisterAll()
        sessionManager = null
        coroutineScope.cancel()
    }

    companion object {
        private const val TAG = "NowPlayingService"
    }
}
