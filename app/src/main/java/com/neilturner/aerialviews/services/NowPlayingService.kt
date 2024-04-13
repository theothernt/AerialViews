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

class NowPlayingService(private val context: Context, private val prefs: GeneralPrefs) :
    MediaSessionManager.OnActiveSessionsChangedListener {

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
        if (hasPermission) {
            setupSession()
        } else {
            Log.i(TAG, "No permission given to access media sessions")
        }
    }

    private fun setupSession() {
        val sessionManager = context.getSystemService<MediaSessionManager>()
        val notificationListener = ComponentName(context, NotificationService::class.java)

        Log.i(TAG, "Listening for media sessions...")
        onActiveSessionsChanged(sessionManager?.getActiveSessions(notificationListener))
        sessionManager?.addOnActiveSessionsChangedListener(this, notificationListener)
    }

    override fun onActiveSessionsChanged(controllers: MutableList<MediaController>?) {
        if (!controllers.isNullOrEmpty()) {
            // If multiple sessions, no way to know which is active
            // Might have to use a loop over 1/2 seconds to check playstate
            Log.i(TAG, "Controllers: ${controllers.count()}")
            controllers.forEachIndexed { index, controller ->
                val playing = (controller.playbackState?.state == PlaybackState.STATE_PLAYING)
                Log.i(TAG, "Controller:$index:$playing")
            }
            updateActiveMediaController(controllers[0])
        } else {
            mediaController?.unregisterCallback(metadataListener)
        }
    }

    private fun updateActiveMediaController(activeController: MediaController) {
        if (mediaController != activeController) {
            mediaController?.unregisterCallback(metadataListener)
            activeController.registerCallback(metadataListener)
            mediaController = activeController
        }

        //Log.i(TAG, "Initial metadata from controller...")
        activeController.metadata?.let { showSong(it) }
        activeController.playbackState?.let { showState(it.state) }
    }

    var message = ""
    var playing = ""

    private fun showSong(data: MediaMetadata) {
        val song = data.getString(MediaMetadata.METADATA_KEY_TITLE)
        val artist = data.getString(MediaMetadata.METADATA_KEY_ARTIST)
        val message = "$song - $artist"
        if (message != this.message) {
            this.message = message
            Log.i(TAG, "Song: $message")
        }
    }

    private fun showState(state: Int) {
        val playing = if (state == PlaybackState.STATE_PLAYING) "true" else "false"
        if (playing != this.playing) {
            Log.i(TAG, "Playing: $playing")
            this.playing = playing
        }

    }

    fun stop() {
        sessionManager?.removeOnActiveSessionsChangedListener(this)
        sessionManager = null
        mediaController?.unregisterCallback(metadataListener)
        mediaController = null
    }

    companion object {
        private const val TAG = "NowPlayingService"
    }
}
