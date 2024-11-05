package com.neilturner.aerialviews.ui.core

import android.content.Context
import android.util.AttributeSet
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.models.enums.AerialMediaSource
import com.neilturner.aerialviews.models.enums.LimitLongerVideos
import com.neilturner.aerialviews.models.enums.VideoScale
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.models.videos.AerialMedia
import com.neilturner.aerialviews.services.PhilipsMediaCodecAdapterFactory
import com.neilturner.aerialviews.services.SambaDataSourceFactory
import com.neilturner.aerialviews.services.WebDavDataSourceFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import timber.log.Timber
import kotlin.math.ceil
import kotlin.math.roundToLong
import kotlin.time.Duration.Companion.milliseconds

class VideoPlayerView
    @JvmOverloads
    @OptIn(UnstableApi::class)
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0,
    ) : PlayerView(context, attrs, defStyleAttr),
        Player.Listener {
        private val coroutineScope = CoroutineScope(Dispatchers.Main)
        private val exoPlayer: ExoPlayer
        private var video = VideoInfo()

        private var listener: OnVideoPlayerEventListener? = null
        private var almostFinishedRunnable = Runnable { listener?.onVideoAlmostFinished() }
        private var canChangePlaybackSpeedRunnable = Runnable { this.canChangePlaybackSpeed = true }
        private var onErrorRunnable = Runnable { listener?.onVideoError() }

        private var playbackSpeed = GeneralPrefs.playbackSpeed
        private val maxVideoLength = GeneralPrefs.maxVideoLength.toInt() * 1000
        private val loopShortVideos = GeneralPrefs.loopShortVideos
        private val segmentLongVideos = GeneralPrefs.limitLongerVideos == LimitLongerVideos.SEGMENT
        private val allowLongerVideos = GeneralPrefs.limitLongerVideos == LimitLongerVideos.IGNORE
        private var canChangePlaybackSpeed = true

        init {
            exoPlayer = VideoPlayerHelper.buildPlayer(context)
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

            // Test
//            coroutineScope.launch {
//                delay(7 * 1000)
//                exoPlayer.seekTo(20 * 1000)
//            }
        }

        fun release() {
            exoPlayer.release()
            removeCallbacks(almostFinishedRunnable)
            removeCallbacks(canChangePlaybackSpeedRunnable)
            removeCallbacks(onErrorRunnable)
            listener = null
        }

        @OptIn(UnstableApi::class)
        fun setVideo(media: AerialMedia) {
            video = VideoInfo()
            exoPlayer.repeatMode = Player.REPEAT_MODE_OFF

            if (GeneralPrefs.philipsDolbyVisionFix) {
                PhilipsMediaCodecAdapterFactory.mediaUrl = media.uri.toString()
            }

            val mediaItem = MediaItem.fromUri(media.uri)
            when (media.source) {
                AerialMediaSource.SAMBA -> {
                    val mediaSource =
                        ProgressiveMediaSource
                            .Factory(SambaDataSourceFactory())
                            .createMediaSource(mediaItem)
                    exoPlayer.setMediaSource(mediaSource)
                }
                AerialMediaSource.WEBDAV -> {
                    val mediaSource =
                        ProgressiveMediaSource
                            .Factory(WebDavDataSourceFactory())
                            .createMediaSource(mediaItem)
                    exoPlayer.setMediaSource(mediaSource)
                }
                else -> {
                    exoPlayer.setMediaItem(mediaItem)
                }
            }

            if (GeneralPrefs.muteVideos) {
                VideoPlayerHelper.disableAudioTrack(exoPlayer)
            }

            exoPlayer.prepare()
        }

        override fun onDetachedFromWindow() {
            pause()
            super.onDetachedFromWindow()
        }

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

    @OptIn(UnstableApi::class)
    override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_IDLE -> Timber.i("Idle...")
                Player.STATE_BUFFERING -> Timber.i("Buffering...")
                Player.STATE_READY -> Timber.i("Ready to play...")
                Player.STATE_ENDED -> Timber.i("Playback ended...")
            }

            if (playbackState == Player.STATE_BUFFERING) {
                // Pause progress bar
            }

            if (!video.prepared && playbackState == Player.STATE_READY) {
//                if (segmentLongVideos) {
//                    if (!video.isSegmented) {
//                        val (isSegmented, segmentStart, segmentEnd) = calculateSegments()
//                        video.isSegmented = isSegmented
//                        video.segmentStart = segmentStart
//                        video.segmentEnd = segmentEnd
//                    }
//                    if (video.isSegmented && exoPlayer.currentPosition !in video.segmentStart - 500..video.segmentEnd + 500) {
//                        Timber.i("Seeking to segment ${video.segmentStart}ms")
//                        exoPlayer.seekTo(video.segmentStart)
//                        return
//                    }
//                    if (video.isSegmented) {
//                        Timber.i("At segment ${exoPlayer.currentPosition}ms (target ${video.segmentStart}ms), continuing...")
//                    }
//                }

                video.duration = calculateDurationForPlaybackSpeed(exoPlayer.duration, GeneralPrefs.playbackSpeed.toFloat())

                video.prepared = true
                listener?.onVideoPrepared()
            }

            // Video is buffered, ready to play
            if (exoPlayer.playWhenReady && playbackState == Player.STATE_READY) {
                if (GeneralPrefs.refreshRateSwitching) {
                    VideoPlayerHelper.setRefreshRate(context, exoPlayer.videoFormat?.frameRate)
                }
                setupAlmostFinishedRunnable()
                Timber.i("Playing...")
            }
        }

        fun increaseSpeed() = changeSpeed(true)

        fun decreaseSpeed() = changeSpeed(false)

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

            exoPlayer.setPlaybackSpeed(newSpeed.toFloat())
            GeneralPrefs.playbackSpeed = newSpeed

            setupAlmostFinishedRunnable()
            listener?.onVideoPlaybackSpeedChanged()
        }

        private fun setupAlmostFinishedRunnable() {
            removeCallbacks(almostFinishedRunnable)
            val delay = calculateDelay()
            postDelayed(almostFinishedRunnable, delay)
        }

        private fun calculateDelay(): Long {
            // 10 seconds is the min. video length
            val tenSeconds = 10 * 1000

            // If max length disabled, play full video
            if (maxVideoLength < tenSeconds) {
                return calculateEndOfVideo(exoPlayer.duration, exoPlayer.currentPosition)
            }

            // Play a part/segment of a video only
            if (video.isSegmented) {
                val position = if (exoPlayer.currentPosition < video.segmentStart) 0 else exoPlayer.currentPosition - video.segmentStart
                return calculateEndOfVideo(video.segmentEnd - video.segmentStart, position)
            }

            // Check if we need to loop the video
            if (loopShortVideos &&
                exoPlayer.duration < maxVideoLength
            ) {
                val (isLooping, duration) = calculateLoopingVideo()
                if (isLooping) {
                    exoPlayer.repeatMode = Player.REPEAT_MODE_ALL
                }
                val position = (video.loopCount * exoPlayer.duration) + exoPlayer.currentPosition
                return calculateEndOfVideo(duration, position)
            }

            // Limit the duration of the video, or not
            if (maxVideoLength in tenSeconds until exoPlayer.duration &&
                !allowLongerVideos
            ) {
                Timber.i("Limiting duration (video is ${exoPlayer.duration.milliseconds}, limit is ${maxVideoLength.milliseconds})")
                return calculateEndOfVideo(maxVideoLength.toLong(), exoPlayer.currentPosition)
            }
            Timber.i("Ignoring limit (video is ${exoPlayer.duration.milliseconds}, limit is ${maxVideoLength.milliseconds})")
            return calculateEndOfVideo(exoPlayer.duration, exoPlayer.currentPosition)
        }

        private fun calculateSegments(): Triple<Boolean, Long, Long> {
            // 10 seconds is the min. video length
            val tenSeconds = 10 * 1000
            if (maxVideoLength < tenSeconds) {
                return Triple(false, 0L, 0L)
            }
            val segments = exoPlayer.duration / maxVideoLength
            if (segments < 2) {
                return Triple(false, 0L, 0L)
            }
            val length = exoPlayer.duration.floorDiv(segments).toLong()
            val random = (1..segments).random()
            val segmentStart = (random - 1) * length
            val segmentEnd = random * length

            val message1 = "Segment chosen: ${segmentStart.milliseconds} - ${segmentEnd.milliseconds}"
            val message2 = "(video is ${exoPlayer.duration.milliseconds}, Segments: $segments)"
            Timber.i("$message1 $message2")
            return Triple(true, segmentStart, segmentEnd)
        }

        private fun calculateEndOfVideo(
            duration: Long,
            position: Long,
        ): Long {
            // Adjust the duration based on the playback speed
            // Take into account the current player position in case of speed changes during playback
            val delay = (((duration - position) / playbackSpeed.toFloat()).roundToLong() - GeneralPrefs.mediaFadeOutDuration.toLong())
            val actualPosition = if (video.isSegmented) position + video.segmentStart else position
            Timber.i("Delay: ${delay.milliseconds} (Duration: ${duration.milliseconds}, Position: ${actualPosition.milliseconds})")
            return if (delay < 0) 0 else delay
        }

        private fun calculateLoopingVideo(): Pair<Boolean, Long> {
            val loopCount = ceil(maxVideoLength / exoPlayer.duration.toDouble()).toInt()
            val targetDuration = exoPlayer.duration * loopCount
            Timber.i("Looping $loopCount times (video is ${exoPlayer.duration.milliseconds}, limit is ${maxVideoLength.milliseconds})")
            return Pair(loopCount > 1, targetDuration.toLong())
        }

        private fun calculateDurationForPlaybackSpeed(duration: Long, playbackSpeed: Float): Long {
            return (duration / playbackSpeed).toLong()
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
    var duration: Long = 0L,
    var prepared: Boolean = false,
    var loopCount: Int = 0,
    var isSegmented: Boolean = false,
    var segmentStart: Long = 0L,
    var segmentEnd: Long = 0L,
)