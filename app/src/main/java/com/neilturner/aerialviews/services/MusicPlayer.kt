package com.neilturner.aerialviews.services

import android.content.Context
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.neilturner.aerialviews.models.music.MusicPlaylist
import com.neilturner.aerialviews.models.music.MusicTrack
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

    fun getPlayer(): ExoPlayer? = player

    fun start() {
        val player = player ?: run {
            Timber.w("MusicPlayer: start() called but player not created")
            return
        }

        if (player.playbackState == Player.STATE_IDLE || player.currentMediaItem == null) {
            loadNextTrack()
        }

        player.play()
        Timber.i("MusicPlayer: playback started")
    }

    fun pause() {
        player?.pause()
        Timber.i("MusicPlayer: playback paused")
    }

    fun nextTrack() {
        loadNextTrack()
        Timber.i("MusicPlayer: skipped to next track")
    }

    fun previousTrack() {
        loadPreviousTrack()
        Timber.i("MusicPlayer: skipped to previous track")
    }

    fun release() {
        player?.release()
        player = null
        Timber.i("MusicPlayer: released")
    }

    fun hasMusic(): Boolean = playlist.size > 0

    private fun loadNextTrack() {
        val player = player ?: return
        loadTrack(player, playlist.nextTrack())
    }

    private fun loadPreviousTrack() {
        val player = player ?: return
        loadTrack(player, playlist.previousTrack())
    }

    private fun loadTrack(
        player: ExoPlayer,
        track: MusicTrack,
    ) {
        VideoPlayerHelper.setupAudioSource(context, player, track)
        player.playWhenReady = true
        Timber.i("MusicPlayer: loading track - ${track.title} by ${track.artist}")
    }
}
