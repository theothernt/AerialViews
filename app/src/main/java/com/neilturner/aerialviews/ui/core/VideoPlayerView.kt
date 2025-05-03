@file:Suppress("SameParameterValue")

package com.neilturner.aerialviews.ui.core

import android.content.Context
import android.util.AttributeSet
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.models.enums.ProgressBarLocation
import com.neilturner.aerialviews.models.enums.ProgressBarType
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.models.videos.AerialMedia
import com.neilturner.aerialviews.services.PhilipsMediaCodecAdapterFactory
import com.neilturner.aerialviews.ui.overlays.ProgressBarEvent
import com.neilturner.aerialviews.ui.overlays.ProgressState
import com.neilturner.aerialviews.utils.FirebaseHelper
import com.neilturner.aerialviews.utils.PermissionHelper
import com.neilturner.aerialviews.utils.RefreshRateHelper
import me.kosert.flowbus.GlobalBus
import timber.log.Timber
import kotlin.time.Duration.Companion.milliseconds

@Suppress("JoinDeclarationAndAssignment")
@OptIn(UnstableApi::class)
class VideoPlayerView
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0,
    ) : PlayerView(context, attrs, defStyleAttr),
        Player.Listener {
        private val exoPlayer: ExoPlayer
        private var state = VideoState()

        private var listener: OnVideoPlayerEventListener? = null
        private var almostFinishedRunnable = Runnable { listener?.onVideoAlmostFinished() }
        private var canChangePlaybackSpeedRunnable = Runnable { this.canChangePlaybackSpeed = true }
        private var onErrorRunnable = Runnable { listener?.onVideoError() }
        private val refreshRateHelper by lazy { RefreshRateHelper(context) }
        private var canChangePlaybackSpeed = true
        private var playbackSpeed = GeneralPrefs.playbackSpeed
        private val progressBar =
            GeneralPrefs.progressBarLocation != ProgressBarLocation.DISABLED && GeneralPrefs.progressBarType != ProgressBarType.PHOTOS

        init {
            exoPlayer = VideoPlayerHelper.buildPlayer(context, GeneralPrefs)

            player = exoPlayer
            player?.addListener(this)
            player?.repeatMode = Player.REPEAT_MODE_ALL // Used for looping short videos

            controllerAutoShow = false
            useController = false
            resizeMode = VideoPlayerHelper.getResizeMode(GeneralPrefs.videoScale)
        }

        fun release() {
            pause()
            player?.release()

            removeCallbacks(almostFinishedRunnable)
            removeCallbacks(canChangePlaybackSpeedRunnable)
            removeCallbacks(onErrorRunnable)

            listener = null
        }

        fun setVideo(media: AerialMedia) {
            state = VideoState() // Reset params for each video

            if (GeneralPrefs.philipsDolbyVisionFix) {
                PhilipsMediaCodecAdapterFactory.mediaUrl = media.uri.toString()
            }

            VideoPlayerHelper.setupMediaSource(exoPlayer, media)

            if (GeneralPrefs.muteVideos) {
                VideoPlayerHelper.disableAudioTrack(exoPlayer)
            }

            player?.prepare()
        }

        fun increaseSpeed() = changeSpeed(true)

        fun decreaseSpeed() = changeSpeed(false)

        fun seekForward() = seek()

        fun seekBackward() = seek(true)

        fun setOnPlayerListener(listener: OnVideoPlayerEventListener?) {
            this.listener = listener
        }

        fun start() {
            exoPlayer.playWhenReady = true
        }

        fun pause() {
            exoPlayer.playWhenReady = false
        }

        fun stop() {
            removeCallbacks(almostFinishedRunnable)
            exoPlayer.stop()
        }

        val currentPosition
            get() = exoPlayer.currentPosition.toInt()

        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_IDLE -> Timber.i("Idle...")
                Player.STATE_ENDED -> Timber.i("Playback ended...")
                Player.STATE_READY -> {}
                Player.STATE_BUFFERING -> {
                    Timber.i("Buffering...")
                    if (progressBar) GlobalBus.post(ProgressBarEvent(ProgressState.PAUSE))
                }
            }

            if (!state.prepared && playbackState == Player.STATE_READY) {
                Timber.i("Preparing...")

                // Waiting for... https://youtrack.jetbrains.com/issue/KT-19627/Object-name-based-destructuring
                val result = VideoPlayerHelper.calculatePlaybackParameters(exoPlayer, GeneralPrefs)
                state.startPosition = result.first
                state.endPosition = result.second

                if (state.startPosition > 0) {
                    Timber.i("Seeking to ${state.startPosition.milliseconds}")
                    player?.seekTo(state.startPosition)
                }

                state.prepared = true
            }

            // Video is buffered, ready to play
            if (exoPlayer.playWhenReady && playbackState == Player.STATE_READY) {
                if (exoPlayer.isPlaying) {
                    Timber.i("Ready, Playing...")

                    if (GeneralPrefs.refreshRateSwitching && PermissionHelper.hasSystemOverlayPermission(context)) {
                        // VideoPlayerHelper.setRefreshRate(context, exoPlayer.videoFormat?.frameRate)
                        refreshRateHelper.setRefreshRate(exoPlayer.videoFormat?.frameRate)
                    }

                    if (!state.ready) {
                        listener?.onVideoPrepared()
                        state.ready = true
                    }

                    setupAlmostFinishedRunnable()
                } else {
                    Timber.i("Preparing again...")
                }
            }
        }

        override fun onMediaItemTransition(
            mediaItem: MediaItem?,
            reason: Int,
        ) {
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT) {
                state.loopCount++
                Timber.i("Looping video, count: ${state.loopCount}")

                if (GeneralPrefs.loopUntilSkipped) {
                    setupAlmostFinishedRunnable()
                }
            }
            super.onMediaItemTransition(mediaItem, reason)
        }

        override fun onPlayerError(error: PlaybackException) {
            super.onPlayerError(error)
            removeCallbacks(almostFinishedRunnable)
            postDelayed(onErrorRunnable, ScreenController.ERROR_DELAY)
            FirebaseHelper.logExceptionIfRecent(error.cause)
        }

        override fun onPlayerErrorChanged(error: PlaybackException?) {
            super.onPlayerErrorChanged(error)
            error?.let { Timber.e(it) }
        }

        private fun seek(backward: Boolean = false) {
            val interval = GeneralPrefs.seekInterval.toLong() * 1000
            val position = exoPlayer.currentPosition

            Timber.i("Seeking to $position/$interval (backward: $backward)")

            if (backward) {
                exoPlayer.seekTo(position - interval)
            } else {
                exoPlayer.seekTo(position + interval)
            }
        }

        private fun changeSpeed(increase: Boolean) {
            if (!canChangePlaybackSpeed) {
                return
            }

            if (!exoPlayer.playWhenReady || !exoPlayer.isPlaying) {
                return // Must be playing a video
            }

            if (exoPlayer.currentPosition <= CHANGE_PLAYBACK_START_END_DELAY) {
                return // No speed change at the start of the video
            }

            if (exoPlayer.duration - exoPlayer.currentPosition <= CHANGE_PLAYBACK_START_END_DELAY) {
                return // No speed changes at the end of video
            }

            canChangePlaybackSpeed = false
            postDelayed(canChangePlaybackSpeedRunnable, CHANGE_PLAYBACK_SPEED_DELAY)

            val currentSpeed = playbackSpeed
            var speedValues: Array<String>
            var currentSpeedIdx: Int

            try {
                speedValues = resources.getStringArray(R.array.playback_speed_values)
                currentSpeedIdx = speedValues.indexOf(currentSpeed)
            } catch (ex: Exception) {
                Timber.e(ex, "Exception while getting playback speed values")
                return
            }

            if (currentSpeedIdx == -1) {
                // No matching speed, likely a resource error or pref mismatch
                GeneralPrefs.playbackSpeed = "1" // Reset pref
                return
            }

            if (!increase && currentSpeedIdx == 0) {
                return // we are at minimum speed already
            }

            if (increase && currentSpeedIdx == speedValues.size - 1) {
                return // we are at maximum speed already
            }

            val newSpeed =
                if (increase) {
                    speedValues[currentSpeedIdx + 1]
                } else {
                    speedValues[currentSpeedIdx - 1]
                }

            playbackSpeed = newSpeed
            exoPlayer.setPlaybackSpeed(newSpeed.toFloat())
            GeneralPrefs.playbackSpeed = playbackSpeed

            setupAlmostFinishedRunnable()
            listener?.onVideoPlaybackSpeedChanged()
        }

        private fun setupAlmostFinishedRunnable() {
            removeCallbacks(almostFinishedRunnable)

            if (state.startPosition <= 0 && state.endPosition <= 0) {
                postDelayed(almostFinishedRunnable, 2 * 1000)
                if (progressBar) GlobalBus.post(ProgressBarEvent(ProgressState.RESET))
                return
            }

            // Adjust the duration based on the playback speed
            // Take into account the current player position, speed changes, fade in/out

            // Basic duration and progress
            val duration = state.endPosition - state.startPosition
            val progress = exoPlayer.currentPosition - state.startPosition
            val fadeDuration = GeneralPrefs.mediaFadeOutDuration.toLong()

            // Duration taking into account...
            val durationMinusSpeed = (duration / GeneralPrefs.playbackSpeed.toDouble()).toLong()
            val durationMinusSpeedAndProgress = durationMinusSpeed - progress
            var durationMinusSpeedAndProgressAndFade = durationMinusSpeedAndProgress - fadeDuration

            if (durationMinusSpeedAndProgressAndFade < 1000) {
                durationMinusSpeedAndProgressAndFade = 1000
            }

            Timber.i(
                "Duration: ${duration.milliseconds} (at 1x), Delay: ${durationMinusSpeedAndProgressAndFade.milliseconds} (at ${GeneralPrefs.playbackSpeed}x), Curr. position: ${progress.milliseconds}",
            )

            if (progressBar) GlobalBus.post(ProgressBarEvent(ProgressState.START, progress, durationMinusSpeed))

            if (!GeneralPrefs.loopUntilSkipped) {
                Timber.i("Video will finish in: ${durationMinusSpeedAndProgressAndFade.milliseconds}")
                postDelayed(almostFinishedRunnable, durationMinusSpeedAndProgressAndFade)
            } else {
                Timber.i("The video will only finish when skipped manually")
            }
        }

        interface OnVideoPlayerEventListener {
            fun onVideoAlmostFinished()

            fun onVideoError()

            fun onVideoPrepared()

            fun onVideoPlaybackSpeedChanged()
        }

        companion object {
            const val CHANGE_PLAYBACK_SPEED_DELAY: Long = 2000
            const val CHANGE_PLAYBACK_START_END_DELAY: Long = 4000
        }
    }

data class VideoState(
    var ready: Boolean = false,
    var prepared: Boolean = false,
    var loopCount: Int = 1,
    var startPosition: Long = 0L,
    var endPosition: Long = 0L,
)
