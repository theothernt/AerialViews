package com.neilturner.aerialviews.ui.core

import com.neilturner.aerialviews.services.MusicPlayer

/** Adapts the legacy ExoPlayer-backed music player to the shared controller contract. */
class LegacyMusicPlayback(
    private val player: MusicPlayer,
) : MusicPlayback {
    override fun load(startTrackIndex: Int) {
        player.createPlayer()
        player.load(startTrackIndex)
    }

    override fun play() {
        player.play()
    }

    override fun pause() {
        player.pause()
    }

    override fun resume() {
        player.resume()
    }

    override fun nextTrack() {
        player.nextTrack()
    }

    override fun previousTrack() {
        player.previousTrack()
    }

    override fun release() {
        player.pause()
        player.release()
    }

    override fun hasMusic(): Boolean = player.hasMusic()

    override fun currentTrackIndex(): Int = player.getCurrentTrackIndex()
}
