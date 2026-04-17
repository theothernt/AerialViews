package com.neilturner.aerialviews.services

import android.content.Context
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.neilturner.aerialviews.models.music.MusicPlaylist
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.ui.core.VideoPlayerHelper
import com.neilturner.aerialviews.utils.VolumeHelper
import timber.log.Timber

class MusicPlayer(
    private val context: Context,
    private val playlist: MusicPlaylist,
) {
    private var player: ExoPlayer? = null
    private val volumeHelper = VolumeHelper(
        getVolume = { player?.volume ?: 0f },
        setVolume = { v -> player?.volume = v },
    )

    fun createPlayer(): ExoPlayer {
        player = VideoPlayerHelper.buildAudioPlayer(context)
        return player!!
    }

    fun getPlayer(): ExoPlayer? = player
    
    fun getCurrentTrackIndex(): Int = player?.currentMediaItemIndex ?: 0
    
    // Support resume capability
    fun seekToTrack(index: Int) {
        if (index > 0 && index < playlist.size) {
            player?.seekTo(index, 0L)
            Timber.i("MusicPlayer: array size is ${playlist.size}, seeking to index $index")
        }
    }

    fun play() {
        val player = player ?: run {
            Timber.w("MusicPlayer: play() called but player not created")
            return
        }

        // Load all tracks into ExoPlayer's queue with correct data source per track
        playlist.tracks.forEach { track ->
            val mediaSource = VideoPlayerHelper.createAudioMediaSource(context, track)
            player.addMediaSource(mediaSource)
        }
        player.prepare()

        // Apply repeat mode
        player.repeatMode =
            if (playlist.repeat) {
                Player.REPEAT_MODE_ALL
            } else {
                Player.REPEAT_MODE_OFF
            }

        player.volume = 0f
        player.play()
        volumeHelper.fadeIn(
            durationMs = 500,
            targetVolume = GeneralPrefs.videoVolume.toFloat() / 100,
        )
        Timber.i("MusicPlayer: playing ${playlist.size} tracks, repeat=${playlist.repeat}")
    }

    fun pause() {
        // Won't do anything unless we delay shutting down of screensaver
        // onWakeUp or onStop - delay less than 1 second or be killed by OS
        volumeHelper.fadeOut(durationMs = 500) {
            player?.pause()
        }
        Timber.i("MusicPlayer: pausing")
    }

    fun nextTrack() {
        val player = player ?: return
        player.seekToNextMediaItem()
        Timber.i("MusicPlayer: skipped to next track")
    }

    fun previousTrack() {
        val player = player ?: return
        player.seekToPreviousMediaItem()
        Timber.i("MusicPlayer: skipped to previous track")
    }

    fun release() {
        volumeHelper.cancel()
        player?.release()
        player = null
        Timber.i("MusicPlayer: released")
    }

    fun hasMusic(): Boolean = playlist.size > 0
}
