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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus

class NowPlayingService(private val context: Context, private val prefs: GeneralPrefs) :
    MediaSessionManager.OnActiveSessionsChangedListener {

    private val _nowPlaying = MutableSharedFlow<String>(replay = 0)
    val nowPlaying
        get() = _nowPlaying.asSharedFlow()

    private val coroutineScope = CoroutineScope(Dispatchers.Main) + Job()
    private val notificationListener = ComponentName(context, NotificationService::class.java)
    private val hasPermission = PermissionHelper.hasNotificationListenerPermission(context)
    private var sessionManager: MediaSessionManager? = null
    private var mediaController: MediaController? = null

    private val metadataListener = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) {
            metadata?.let { showSong(it) }
            super.onMetadataChanged(metadata)
        }

        override fun onPlaybackStateChanged(state: PlaybackState?) {
            state?.let { showState(it.state) }
            super.onPlaybackStateChanged(state)
        }
    }

    init {
        coroutineScope.launch {
            if (hasPermission) {
                setupSession()
                while (isActive) {
                    updateNowPlaying()
                }
            } else {
                Log.i(TAG, "No permission given to access media sessions")
            }
        }
    }

    private suspend fun updateNowPlaying() {
        val sessions = sessionManager?.getActiveSessions(notificationListener)
//        sessions?.forEach { controller ->
//            val playing = if (controller.playbackState?.state == PlaybackState.STATE_PLAYING) "playing" else "not playing"
//            val packageName = controller.packageName
//            Log.i(TAG, "$packageName:$playing")
//        }

        if (!sessions.isNullOrEmpty()) {
            val controller = sessions.first()
            val packageName = controller.packageName
            val isPlaying = controller.playbackState?.state == PlaybackState.STATE_PLAYING

            val song = controller.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
            val artist = controller.metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            val nowPlaying = "$song - $artist"

            if (isPlaying) {
                _nowPlaying.emit(nowPlaying)
            } else {
                _nowPlaying.distinctUntilChanged()
                _nowPlaying.emit("")
            }
        }
        delay(1500)
    }

    private fun setupSession() {
        sessionManager = context.getSystemService<MediaSessionManager>()
//        Log.i(TAG, "Listening for media sessions...")
//        onActiveSessionsChanged(sessionManager?.getActiveSessions(notificationListener))
//        sessionManager?.addOnActiveSessionsChangedListener(this, notificationListener)
    }

    override fun onActiveSessionsChanged(controllers: MutableList<MediaController>?) {
        if (!controllers.isNullOrEmpty()) {
            Log.i(TAG, "Controllers: ${controllers.count()}")
            controllers.forEach { controller ->
                val playing = (controller.playbackState?.state == PlaybackState.STATE_PLAYING)
                val name = controller.packageName
                Log.i(TAG, "$name:$playing")
            }

            // updateActiveMediaController(controllers[0])
        } else {
            // mediaController?.unregisterCallback(metadataListener)
        }

        val sessions = sessionManager?.getActiveSessions(notificationListener)
        sessions?.forEach { controller ->
            val playing = if (controller.playbackState?.state == PlaybackState.STATE_PLAYING) "playing" else "not playing"
            val packageName = controller.packageName
            Log.i(TAG, "$packageName:$playing")
        }
    }

    private fun updateActiveMediaController(activeController: MediaController) {
        if (mediaController != activeController) {
            mediaController?.unregisterCallback(metadataListener)
            activeController.registerCallback(metadataListener)
            mediaController = activeController
        }

        // Log.i(TAG, "Initial metadata from controller...")
        activeController.metadata?.let { showSong(it) }
        activeController.playbackState?.let { showState(it.state) }
    }

    var message = ""
    var playing = ""

    private fun showSong(data: MediaMetadata) {
        val song = data.getString(MediaMetadata.METADATA_KEY_TITLE)
        val artist = data.getString(MediaMetadata.METADATA_KEY_ARTIST)
        val message = "$song - $artist"
        Log.i(TAG, "Song: $message")
        if (message != this.message) {
            this.message = message
        }
    }

    private fun showState(state: Int) {
        val playing = if (state == PlaybackState.STATE_PLAYING) "true" else "false"
        Log.i(TAG, "Playing: $playing")
        if (playing != this.playing) {
            this.playing = playing
        }
    }

    fun stop() {
        coroutineScope.cancel()
        sessionManager?.removeOnActiveSessionsChangedListener(this)
        sessionManager = null
        mediaController?.unregisterCallback(metadataListener)
        mediaController = null
    }

    companion object {
        private const val TAG = "NowPlayingService"
    }
}
