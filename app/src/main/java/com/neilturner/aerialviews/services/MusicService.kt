package com.neilturner.aerialviews.services

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.neilturner.aerialviews.models.music.MusicPlaylist
import com.neilturner.aerialviews.models.music.MusicTrack
import com.neilturner.aerialviews.models.prefs.LocalMediaPrefs
import com.neilturner.aerialviews.models.prefs.MusicPrefs
import com.neilturner.aerialviews.models.prefs.SambaMediaPrefs
import com.neilturner.aerialviews.models.prefs.SambaMediaPrefs2
import com.neilturner.aerialviews.providers.music.LocalMusicProvider
import com.neilturner.aerialviews.providers.music.MusicProvider
import com.neilturner.aerialviews.providers.music.SambaMusicProvider
import com.neilturner.aerialviews.ui.core.VideoPlayerHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

class MusicService(
    private val context: Context,
) {
    private var player: ExoPlayer? = null
    private var playlist: MusicPlaylist? = null
    private var fetchJob: Job? = null
    private var isPrepared = false

    private val musicProviders = mutableListOf<MusicProvider>()

    init {
        // Build list of enabled music providers
        if (LocalMediaPrefs.musicEnabled) {
            musicProviders.add(
                LocalMusicProvider(context) { LocalMediaPrefs.musicEnabled },
            )
        }

        if (SambaMediaPrefs.musicEnabled) {
            musicProviders.add(SambaMusicProvider(context, SambaMediaPrefs))
        }

        if (SambaMediaPrefs2.musicEnabled) {
            musicProviders.add(SambaMusicProvider(context, SambaMediaPrefs2))
        }

        Timber.i("MusicService: ${musicProviders.size} music provider(s) enabled")
    }

    /**
     * Fetches music from all enabled providers and builds the playlist.
     * Should be called during screensaver initialization.
     */
    fun preparePlaylist(onComplete: (trackCount: Int) -> Unit) {
        fetchJob = CoroutineScope(Dispatchers.Main).launch {
            val allTracks = mutableListOf<MusicTrack>()

            withContext(Dispatchers.IO) {
                for (provider in musicProviders) {
                    try {
                        val tracks = provider.fetchMusic()
                        Timber.i("MusicService: ${provider::class.simpleName} returned ${tracks.size} tracks")
                        allTracks.addAll(tracks)
                    } catch (ex: Exception) {
                        Timber.e(ex, "MusicService: failed to fetch from ${provider::class.simpleName}")
                    }
                }
            }

            if (allTracks.isNotEmpty()) {
                playlist = MusicPlaylist(
                    _tracks = allTracks,
                    shuffle = MusicPrefs.shuffle,
                    repeat = MusicPrefs.repeat,
                )
                isPrepared = true
                Timber.i("MusicService: playlist built with ${allTracks.size} tracks")
            } else {
                isPrepared = false
                Timber.i("MusicService: no music tracks found")
            }

            onComplete(allTracks.size)
        }
    }

    /**
     * Creates and configures the audio ExoPlayer instance.
     * Should be called before starting playback.
     */
    fun createPlayer(): ExoPlayer {
        player = VideoPlayerHelper.buildAudioPlayer(context)
        return player!!
    }

    /**
     * Returns the current ExoPlayer instance, or null if not created.
     */
    fun getPlayer(): ExoPlayer? = player

    /**
     * Starts music playback. Loads the first track if not already playing.
     */
    fun start() {
        val player = player ?: run {
            Timber.w("MusicService: start() called but player not created")
            return
        }

        if (!isPrepared || playlist == null) {
            Timber.w("MusicService: start() called but playlist not prepared")
            return
        }

        if (player.playbackState == Player.STATE_IDLE || player.currentMediaItem == null) {
            loadNextTrack()
        }

        player.play()
        Timber.i("MusicService: playback started")
    }

    /**
     * Pauses music playback.
     */
    fun pause() {
        player?.pause()
        Timber.i("MusicService: playback paused")
    }

    /**
     * Skips to the next track.
     */
    fun nextTrack() {
        if (playlist == null) return
        loadNextTrack()
        Timber.i("MusicService: skipped to next track")
    }

    /**
     * Skips to the previous track.
     */
    fun previousTrack() {
        if (playlist == null) return
        loadPreviousTrack()
        Timber.i("MusicService: skipped to previous track")
    }

    /**
     * Releases the player and cancels any ongoing fetch jobs.
     * Should be called when the screensaver stops.
     */
    fun release() {
        fetchJob?.cancel()
        player?.release()
        player = null
        playlist = null
        isPrepared = false
        Timber.i("MusicService: released")
    }

    /**
     * Returns true if there is a prepared playlist with tracks.
     */
    fun hasMusic(): Boolean = isPrepared && playlist != null && playlist!!.size > 0

    private fun loadNextTrack() {
        val playlist = playlist ?: return
        val player = player ?: return

        val track = playlist.nextTrack()
        loadTrack(player, track)
    }

    private fun loadPreviousTrack() {
        val playlist = playlist ?: return
        val player = player ?: return

        val track = playlist.previousTrack()
        loadTrack(player, track)
    }

    private fun loadTrack(player: ExoPlayer, track: MusicTrack) {
        val mediaItem = MediaItem.fromUri(track.uri)
        player.setMediaItem(mediaItem)
        player.prepare()
        player.playWhenReady = true
        Timber.i("MusicService: loading track - ${track.title} by ${track.artist}")
    }
}
