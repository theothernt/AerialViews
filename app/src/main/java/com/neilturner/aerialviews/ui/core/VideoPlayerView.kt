package com.neilturner.aerialviews.ui.core

import android.content.Context
import android.util.AttributeSet
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.models.enums.LimitLongerVideos
import com.neilturner.aerialviews.models.enums.ProgressBarLocation
import com.neilturner.aerialviews.models.enums.ProgressBarType
import com.neilturner.aerialviews.models.enums.VideoScale
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.models.videos.AerialMedia
import com.neilturner.aerialviews.services.PhilipsMediaCodecAdapterFactory
import com.neilturner.aerialviews.ui.overlays.ProgressBarEvent
import com.neilturner.aerialviews.ui.overlays.ProgressState
import me.kosert.flowbus.GlobalBus
import timber.log.Timber
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds

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
        // TODO
        // VideoStatus (ref to ExoPlayer?)
        private var video = VideoInfo()

        private var listener: OnVideoPlayerEventListener? = null
        private var almostFinishedRunnable = Runnable { listener?.onVideoAlmostFinished() }
        private var canChangePlaybackSpeedRunnable = Runnable { this.canChangePlaybackSpeed = true }
        private var onErrorRunnable = Runnable { listener?.onVideoError() }

        private var canChangePlaybackSpeed = true
        private var playbackSpeed = GeneralPrefs.playbackSpeed
        private val segmentLongVideos =
            GeneralPrefs.limitLongerVideos == LimitLongerVideos.SEGMENT && GeneralPrefs.maxVideoLength.toLong() > 0
        private val maxVideoLength = GeneralPrefs.maxVideoLength.toLong() * 1000
        private val randomStartPosition = GeneralPrefs.randomStartPosition && GeneralPrefs.maxVideoLength.toLong() == 0L
        private val randomStartPositionRange = GeneralPrefs.randomStartPositionRange.toInt()
        private val progressBar =
            GeneralPrefs.progressBarLocation != ProgressBarLocation.DISABLED && GeneralPrefs.progressBarType != ProgressBarType.PHOTOS

        init {
            exoPlayer = VideoPlayerHelper.buildPlayer(context, GeneralPrefs)
            exoPlayer.addListener(this)

            player = exoPlayer
            controllerAutoShow = false
            useController = false
            resizeMode =
                if (GeneralPrefs.videoScale == VideoScale.SCALE_TO_FIT_WITH_CROPPING) {
                    AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                } else {
                    AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
        }

        fun release() {
            pause()
            exoPlayer.release()

            removeCallbacks(almostFinishedRunnable)
            removeCallbacks(canChangePlaybackSpeedRunnable)
            removeCallbacks(onErrorRunnable)

            listener = null
        }

        // region Public methods
        fun setVideo(media: AerialMedia) {
            video = VideoInfo() // Reset params for each video
            exoPlayer.repeatMode = Player.REPEAT_MODE_OFF

            if (GeneralPrefs.philipsDolbyVisionFix) {
                PhilipsMediaCodecAdapterFactory.mediaUrl = media.uri.toString()
            }

            VideoPlayerHelper.setupMediaSource(exoPlayer, media)

            if (GeneralPrefs.muteVideos) {
                VideoPlayerHelper.disableAudioTrack(exoPlayer)
            }

            exoPlayer.prepare()
        }

        fun increaseSpeed() = changeSpeed(true)

        fun decreaseSpeed() = changeSpeed(false)

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
        // endregion

        // region Player Listener
        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_IDLE -> Timber.i("Idle...")
                Player.STATE_ENDED -> Timber.i("Playback ended...")
            }

            if (playbackState == Player.STATE_BUFFERING) {
                Timber.i("Buffering...")
                if (progressBar) GlobalBus.post(ProgressBarEvent(ProgressState.PAUSE))
            }

            if (!video.prepared && playbackState == Player.STATE_READY) {
                Timber.i("Preparing...")
                if (segmentLongVideos) {
                    handleSegmentedVideo()
                }
                if (randomStartPosition) {
                    // TODO
                    // should only be run once (prepared?)
                    handleRandomStartPosition()
                }
                video.prepared = true
                listener?.onVideoPrepared()
            }

            // Video is buffered, ready to play
            if (exoPlayer.playWhenReady && playbackState == Player.STATE_READY) {
                Timber.i("Ready, Playing...")
                if (GeneralPrefs.refreshRateSwitching) {
                    VideoPlayerHelper.setRefreshRate(context, exoPlayer.videoFormat?.frameRate)
                }
                setupAlmostFinishedRunnable()
            }
        }

        override fun onMediaItemTransition(
            mediaItem: MediaItem?,
            reason: Int,
        ) {
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT) {
                video.loopCount++
                Timber.i("Looping video, count: ${video.loopCount}")
            }
            super.onMediaItemTransition(mediaItem, reason)
        }

        override fun onPlayerError(error: PlaybackException) {
            super.onPlayerError(error)
            removeCallbacks(almostFinishedRunnable)
            postDelayed(onErrorRunnable, ScreenController.ERROR_DELAY)
        }

        override fun onPlayerErrorChanged(error: PlaybackException?) {
            super.onPlayerErrorChanged(error)
            error?.let { Timber.e(it) }
        }
        // endregion

        private fun changeSpeed(increase: Boolean) {
            if (!canChangePlaybackSpeed) {
                return
            }

            if (!video.prepared || !exoPlayer.isPlaying) {
                return // Must be playing a video
            }

            if (exoPlayer.currentPosition <= 3) {
                return // No speed change at the start of the video
            }

            if (exoPlayer.duration - exoPlayer.currentPosition <= 3) {
                return // No speed changes at the end of video
            }

            canChangePlaybackSpeed = false
            postDelayed(canChangePlaybackSpeedRunnable, CHANGE_PLAYBACK_SPEED_DELAY)

            val currentSpeed = playbackSpeed
            val speedValues = resources.getStringArray(R.array.playback_speed_values)
            val currentSpeedIdx = speedValues.indexOf(currentSpeed)

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
            val delay = VideoPlayerHelper.calculateDelay(video, exoPlayer, GeneralPrefs)
            if (progressBar) GlobalBus.post(ProgressBarEvent(ProgressState.START, 0, delay))
            postDelayed(almostFinishedRunnable, delay)
        }

        private fun handleSegmentedVideo() {
            if (!video.isSegmented) {
                VideoPlayerHelper.calculateSegments(exoPlayer.duration, maxVideoLength, video)
            }
            if (video.isSegmented && exoPlayer.currentPosition !in video.segmentStart - 500..video.segmentEnd + 500) {
                Timber.i("Seeking to segment ${video.segmentStart}ms")
                exoPlayer.seekTo(video.segmentStart)
                return
            }
            if (video.isSegmented) {
                Timber.i("At segment ${exoPlayer.currentPosition}ms (target ${video.segmentStart}ms), continuing...")
            }
        }

        private fun handleRandomStartPosition() {
            if (randomStartPositionRange < 5) {
                return
            }
            val seekPosition = (exoPlayer.duration * randomStartPositionRange / 100.0).toLong()
            val randomPosition = Random.nextLong(seekPosition)
            exoPlayer.seekTo(randomPosition)

            val percent = (randomPosition.toFloat() / exoPlayer.duration.toFloat() * 100).toInt()
            Timber.i("Seeking to ${randomPosition.milliseconds} ($percent%)")
        }

        interface OnVideoPlayerEventListener {
            fun onVideoAlmostFinished()

            fun onVideoError()

            fun onVideoPrepared()

            fun onVideoPlaybackSpeedChanged()
        }

        companion object {
            const val CHANGE_PLAYBACK_SPEED_DELAY: Long = 2000
        }
    }

data class VideoInfo(
    var prepared: Boolean = false,
    var playbackSpeed: Float = 1.0f,
    var loopCount: Int = 0,
    var isSegmented: Boolean = false,
    var segmentStart: Long = 0L, // start position
    var segmentEnd: Long = 0L, // end position
)
