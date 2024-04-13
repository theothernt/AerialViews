package com.neilturner.aerialviews.services

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus

class NowPlayingService(private val context: Context, private val prefs: GeneralPrefs) {

    private val _nowPlaying = MutableSharedFlow<String>(replay = 0)
    val nowPlaying
        get() = _nowPlaying.asSharedFlow()

    private val coroutineScope = CoroutineScope(Dispatchers.Main) + Job()
    private val notificationListener = ComponentName(context, NotificationService::class.java)
    private val hasPermission = PermissionHelper.hasNotificationListenerPermission(context)
    private var sessionManager: MediaSessionManager? = null

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
        if (!sessions.isNullOrEmpty()) {
            val controller = sessions.first()
            val isPlaying = controller.playbackState?.state == PlaybackState.STATE_PLAYING ||
                    controller.playbackState?.state == PlaybackState.STATE_BUFFERING
            var song = controller.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)?.take(40)
            var artist = controller.metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)?.take(40)

            if (song?.length == 40) {
                song += "..."
            }
            if (artist?.length == 40) {
                artist += "..."
            }

            val nowPlaying = "$song · $artist"
            if (isPlaying) {
                _nowPlaying.emit(nowPlaying)
            } else {
                _nowPlaying.emit("")
            }
        }
        delay(1000)
    }

    private fun setupSession() {
        sessionManager = context.getSystemService<MediaSessionManager>()
    }

    fun stop() {
        sessionManager = null
        coroutineScope.cancel()
    }

    companion object {
        private const val TAG = "NowPlayingService"
    }
}
