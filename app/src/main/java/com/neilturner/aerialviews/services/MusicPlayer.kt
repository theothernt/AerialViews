package com.neilturner.aerialviews.services

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.neilturner.aerialviews.models.music.MusicPlaylist
import com.neilturner.aerialviews.ui.core.VideoPlayerHelper
import timber.log.Timber

class MusicPlayer(
    private val context: Context,
    private val playlist: MusicPlaylist,
) {
    private var player: ExoPlayer? = null

    fun createPlayer(): ExoPlayer {
        player = VideoPlayerHelper.buildAudioPlayer(context)
        return player!!
    }

	@OptIn(UnstableApi::class)
	fun start() {
        val player = player ?: run {
            Timber.w("MusicPlayer: start() called but player not created")
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

        player.play()
        Timber.i("MusicPlayer: started with ${playlist.size} tracks, repeat=${playlist.repeat}")
    }

    fun pause() {
        player?.pause()
        Timber.i("MusicPlayer: playback paused")
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
        player?.release()
        player = null
        Timber.i("MusicPlayer: released")
    }

    fun hasMusic(): Boolean = playlist.size > 0
}
