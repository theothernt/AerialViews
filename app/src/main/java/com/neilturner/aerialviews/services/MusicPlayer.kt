package com.neilturner.aerialviews.services

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.neilturner.aerialviews.models.music.MusicPlaylist
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.ui.core.VideoPlayerHelper
import com.neilturner.aerialviews.ui.helpers.VolumeHelper
import com.neilturner.aerialviews.utils.FirebaseHelper
import timber.log.Timber

private const val FADE_DURATION_MS = 500L

class MusicPlayer(
    private val context: Context,
    private val playlist: MusicPlaylist,
) {
    private var player: ExoPlayer? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val volumeHelper =
        VolumeHelper(
            getVolume = { player?.volume ?: 0f },
            setVolume = { v -> player?.volume = v },
        )

    private var shouldPlay = false
    private var startTrackIndex = 0
    private var lastKnownTrackIndex = 0
    private var consecutiveErrors = 0
    private val failedTrackIndices = mutableSetOf<Int>()

    var onMediaItemChanged: (() -> Unit)? = null

    /** Invoked when a playback error could not be recovered and music has been given up on. */
    var onPlaybackFailed: (() -> Unit)? = null

    fun createPlayer(): ExoPlayer {
        val exoPlayer = VideoPlayerHelper.buildAudioPlayer(context.applicationContext)
        exoPlayer.addListener(
            object : Player.Listener {
                override fun onMediaItemTransition(
                    mediaItem: MediaItem?,
                    reason: Int,
                ) {
                    player?.currentMediaItemIndex?.let { lastKnownTrackIndex = it }
                    onMediaItemChanged?.invoke()
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    // A track is playing again: forget failures so transient errors can heal
                    if (isPlaying) {
                        consecutiveErrors = 0
                        failedTrackIndices.clear()
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    super.onPlayerError(error)
                    handleError(error)
                }
            },
        )
        player = exoPlayer
        return exoPlayer
    }

    fun getCurrentTrackIndex(): Int = player?.currentMediaItemIndex ?: lastKnownTrackIndex

    /**
     * Builds the playback queue (once) and positions it at [startTrackIndex] without starting
     * playback, so that [resume] can start it later.
     */
    @OptIn(UnstableApi::class)
    fun load(startTrackIndex: Int = 0) {
        val player =
            player ?: run {
                Timber.w("MusicPlayer: load() called but player not created")
                return
            }

        this.startTrackIndex = startTrackIndex

        if (player.mediaItemCount > 0) {
            Timber.d("MusicPlayer: queue already loaded, skipping")
            return
        }

        // Load all tracks into ExoPlayer's queue with correct data source per track
        playlist.tracks.forEach { track ->
            val mediaSource = VideoPlayerHelper.createAudioMediaSource(context.applicationContext, track)
            player.addMediaSource(mediaSource)
        }

        player.repeatMode =
            if (playlist.repeat) {
                Player.REPEAT_MODE_ALL
            } else {
                Player.REPEAT_MODE_OFF
            }

        val startIndex = startTrackIndex.takeIf { it in playlist.tracks.indices } ?: 0
        player.seekTo(startIndex, 0L)
        lastKnownTrackIndex = startIndex
        player.volume = 0f
        Timber.i("MusicPlayer: loaded ${playlist.size} tracks at index $startIndex, repeat=${playlist.repeat}")
    }

    fun play(startTrackIndex: Int = 0) {
        load(startTrackIndex)
        startPlayback()
        Timber.i("MusicPlayer: playing ${playlist.size} tracks, repeat=${playlist.repeat}")
    }

    fun pause() {
        shouldPlay = false
        // Won't do anything unless we delay shutting down of screensaver
        // onWakeUp or onStop - delay less than 1 second or be killed by OS
        volumeHelper.fadeOut(durationMs = FADE_DURATION_MS) {
            player?.pause()
        }
        Timber.i("MusicPlayer: pausing")
    }

    fun resume() {
        // The queue is only empty when playback never started (eg. screensaver began in blackout)
        if (player?.mediaItemCount == 0) {
            load(startTrackIndex)
        }
        startPlayback()
        Timber.i("MusicPlayer: resuming")
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
        shouldPlay = false
        volumeHelper.cancel()
        mainHandler.removeCallbacksAndMessages(null)
        player?.release()
        player = null
        consecutiveErrors = 0
        failedTrackIndices.clear()
        Timber.i("MusicPlayer: released")
    }

    fun hasMusic(): Boolean = playlist.size > 0

    private fun startPlayback() {
        val player =
            player ?: run {
                Timber.w("MusicPlayer: startPlayback() called but player not created")
                return
            }
        shouldPlay = true
        consecutiveErrors = 0
        volumeHelper.cancel()
        player.prepare()
        player.play()
        volumeHelper.fadeIn(
            durationMs = FADE_DURATION_MS,
            targetVolume = targetVolume(),
        )
    }

    private fun targetVolume(): Float = GeneralPrefs.videoVolume.toFloat() / 100

    private fun handleError(error: PlaybackException) {
        val trackIndex = player?.currentMediaItemIndex ?: -1
        if (trackIndex >= 0) {
            failedTrackIndices += trackIndex
            lastKnownTrackIndex = trackIndex
        }
        consecutiveErrors++

        val trackUri =
            playlist.tracks
                .getOrNull(trackIndex)
                ?.uri
                ?.toString() ?: "unknown"
        Timber.e(error, "MusicPlayer: playback error on track $trackIndex ($trackUri), $consecutiveErrors in a row")

        // ExoPlayer is IDLE after an error, so recovery must not run inside the listener
        mainHandler.post { recoverFromError(error) }
    }

    private fun recoverFromError(error: PlaybackException) {
        val player = player ?: return

        // Paused (eg. blackout): the error is recovered the next time playback is started
        if (!shouldPlay) {
            Timber.i("MusicPlayer: error while paused, recovery deferred")
            return
        }

        val nextIndex =
            MusicErrorRecovery.nextPlayableIndex(
                currentIndex = player.currentMediaItemIndex,
                totalTracks = playlist.size,
                failedIndices = failedTrackIndices,
                repeat = playlist.repeat,
            )

        if (nextIndex == null ||
            MusicErrorRecovery.shouldGiveUp(
                consecutiveErrors = consecutiveErrors,
                failedTrackCount = failedTrackIndices.size,
                totalTracks = playlist.size,
            )
        ) {
            giveUpOnMusic(error)
            return
        }

        player.seekTo(nextIndex, 0L)
        player.prepare()
        player.play()
        Timber.i("MusicPlayer: skipping to track $nextIndex after error")
    }

    private fun giveUpOnMusic(error: PlaybackException) {
        Timber.e(error, "MusicPlayer: giving up on background music after $consecutiveErrors consecutive errors")
        FirebaseHelper.crashlyticsLogMessage(
            "MusicPlayer: background music abandoned after $consecutiveErrors consecutive errors, last track $lastKnownTrackIndex",
        )
        FirebaseHelper.crashlyticsException(error.cause)

        shouldPlay = false
        volumeHelper.cancel()
        player?.release()
        player = null
        onPlaybackFailed?.invoke()
    }
}
