package com.neilturner.aerialviews.ui.core

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.util.AttributeSet
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.util.EventLogger
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.models.enums.AerialMediaSource
import com.neilturner.aerialviews.models.enums.LimitLongerVideos
import com.neilturner.aerialviews.models.enums.VideoScale
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.models.videos.AerialMedia
import com.neilturner.aerialviews.services.CustomRendererFactory
import com.neilturner.aerialviews.services.ImmichDataSourceFactory
import com.neilturner.aerialviews.services.PhilipsMediaCodecAdapterFactory
import com.neilturner.aerialviews.services.SambaDataSourceFactory
import com.neilturner.aerialviews.services.WebDavDataSourceFactory
import com.neilturner.aerialviews.utils.WindowHelper
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
        private val exoPlayer: ExoPlayer
        private var prepared = false

        private var listener: OnVideoPlayerEventListener? = null
        private var almostFinishedRunnable = Runnable { listener?.onVideoAlmostFinished() }
        private var canChangePlaybackSpeedRunnable = Runnable { this.canChangePlaybackSpeed = true }
        private var onErrorRunnable = Runnable { listener?.onVideoError() }

        private val enableTunneling = GeneralPrefs.enableTunneling
        private val useRefreshRateSwitching = GeneralPrefs.refreshRateSwitching
        private val philipsDolbyVisionFix = GeneralPrefs.philipsDolbyVisionFix
        private val fallbackDecoders = GeneralPrefs.allowFallbackDecoders
        private val extraLogging = GeneralPrefs.enablePlaybackLogging
        private var playbackSpeed = GeneralPrefs.playbackSpeed
        private val muteVideo = GeneralPrefs.muteVideos
        private val videoVolume = GeneralPrefs.videoVolume.toFloat() / 100
        private var canChangePlaybackSpeed = true

        private val maxVideoLength = GeneralPrefs.maxVideoLength.toInt() * 1000
        private val loopShortVideos = GeneralPrefs.loopShortVideos
        private val segmentLongVideos = GeneralPrefs.limitLongerVideos == LimitLongerVideos.SEGMENT
        private val allowLongerVideos = GeneralPrefs.limitLongerVideos == LimitLongerVideos.IGNORE
        private var isSegmentedVideo = false
        private var segmentStart = 0L
        private var segmentEnd = 0L
        private var loopCount = 0

        init {
            exoPlayer = buildPlayer(context)
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
            exoPlayer.release()
            removeCallbacks(almostFinishedRunnable)
            removeCallbacks(canChangePlaybackSpeedRunnable)
            removeCallbacks(onErrorRunnable)
            listener = null
        }

        @OptIn(UnstableApi::class)
        fun setVideo(media: AerialMedia) {
            prepared = false

            exoPlayer.repeatMode = Player.REPEAT_MODE_OFF
            isSegmentedVideo = false
            loopCount = 0

            val uri = media.uri
            val mediaItem = MediaItem.fromUri(uri)

            if (philipsDolbyVisionFix) {
                PhilipsMediaCodecAdapterFactory.mediaUrl = uri.toString()
            }

            when (media.source) {
                AerialMediaSource.SAMBA -> {
                    val mediaSource =
                        ProgressiveMediaSource
                            .Factory(SambaDataSourceFactory())
                            .createMediaSource(mediaItem)
                    exoPlayer.setMediaSource(mediaSource)
                }
                AerialMediaSource.IMMICH -> {
                    ProgressiveMediaSource
                        .Factory(ImmichDataSourceFactory())
                        .createMediaSource(mediaItem)
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

            exoPlayer.prepare()

            if (muteVideo) {
                exoPlayer.trackSelectionParameters =
                    exoPlayer.trackSelectionParameters
                        .buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
                        .build()
            }
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
                loopCount++
                Timber.i("Looping video, count: $loopCount")
            }
            super.onMediaItemTransition(mediaItem, reason)
        }

        // EventListener
        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_IDLE -> Timber.i("Idle...") // 1
                Player.STATE_BUFFERING -> Timber.i("Buffering...") // 2a
                Player.STATE_READY -> Timber.i("Ready to play...") // 3
                Player.STATE_ENDED -> Timber.i("Playback ended...") // 4
            }

            if (!prepared && playbackState == Player.STATE_READY) {
                if (segmentLongVideos) {
                    if (!isSegmentedVideo) {
                        val (isSegmented, segmentStart, segmentEnd) = calculateSegments()
                        this.isSegmentedVideo = isSegmented
                        this.segmentStart = segmentStart
                        this.segmentEnd = segmentEnd
                    }

                    if (isSegmentedVideo && exoPlayer.currentPosition !in segmentStart - 500..segmentEnd + 500) {
                        Timber.i("Seeking to segment ${segmentStart}ms")
                        exoPlayer.seekTo(segmentStart)
                        return
                    }
                    if (isSegmentedVideo) {
                        Timber.i("At segment ${exoPlayer.currentPosition}ms (target ${segmentStart}ms), continuing...")
                    }
                }
                prepared = true
                listener?.onVideoPrepared()
            }

            if (exoPlayer.playWhenReady && playbackState == Player.STATE_READY) {
                if (useRefreshRateSwitching) {
                    setRefreshRate()
                }
                setupAlmostFinishedRunnable()
                Timber.i("Playing...")
            }
        }

        @SuppressLint("UnsafeOptInUsageError")
        private fun setRefreshRate() {
            val frameRate = exoPlayer.videoFormat?.frameRate

            if (frameRate == null || frameRate == 0f) {
                Timber.i("Unable to get video frame rate...")
                return
            }

            Timber.i("${frameRate}fps video, setting refresh rate if needed...")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                WindowHelper.setLegacyRefreshRate(context, frameRate)
            }
        }

        fun increaseSpeed() = changeSpeed(true)

        fun decreaseSpeed() = changeSpeed(false)

        private fun changeSpeed(increase: Boolean) {
            if (!canChangePlaybackSpeed) {
                return
            }

            if (!prepared || !exoPlayer.isPlaying) {
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
            if (isSegmentedVideo) {
                val position = if (exoPlayer.currentPosition < segmentStart) 0 else exoPlayer.currentPosition - segmentStart
                return calculateEndOfVideo(segmentEnd - segmentStart, position)
            }

            // Check if we need to loop the video
            if (loopShortVideos &&
                exoPlayer.duration < maxVideoLength
            ) {
                val (isLooping, duration) = calculateLoopingVideo()
                if (isLooping) {
                    exoPlayer.repeatMode = Player.REPEAT_MODE_ALL
                }
                val position = (loopCount * exoPlayer.duration) + exoPlayer.currentPosition
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
            val actualPosition = if (isSegmentedVideo) position + segmentStart else position
            Timber.i("Delay: ${delay.milliseconds} (Duration: ${duration.milliseconds}, Position: ${actualPosition.milliseconds})")
            return if (delay < 0) 0 else delay
        }

        private fun calculateLoopingVideo(): Pair<Boolean, Long> {
            val loopCount = ceil(maxVideoLength / exoPlayer.duration.toDouble()).toInt()
            val targetDuration = exoPlayer.duration * loopCount
            Timber.i("Looping $loopCount times (video is ${exoPlayer.duration.milliseconds}, limit is ${maxVideoLength.milliseconds})")
            return Pair(loopCount > 1, targetDuration.toLong())
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

        @SuppressLint("UnsafeOptInUsageError")
        private fun buildPlayer(context: Context): ExoPlayer {
            val parametersBuilder = DefaultTrackSelector.Parameters.Builder(context)

            if (enableTunneling) {
                parametersBuilder
                    .setTunnelingEnabled(true)
            }

            val trackSelector = DefaultTrackSelector(context)
            trackSelector.parameters = parametersBuilder.build()

            var rendererFactory = DefaultRenderersFactory(context)
            if (fallbackDecoders) {
                rendererFactory.setEnableDecoderFallback(true)
            }
            if (philipsDolbyVisionFix) {
                rendererFactory = CustomRendererFactory(context)
            }

            val player =
                ExoPlayer
                    .Builder(context)
                    .setTrackSelector(trackSelector)
                    .setRenderersFactory(rendererFactory)
                    .build()

            if (extraLogging) {
                player.addAnalyticsListener(EventLogger())
            }

            if (!muteVideo) {
                player.volume = videoVolume
            } else {
                player.volume = 0f
            }

            // https://medium.com/androiddevelopers/prep-your-tv-app-for-android-12-9a859d9bb967
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && useRefreshRateSwitching) {
                Timber.i("Android 12, enabling refresh rate switching")
                exoPlayer.videoChangeFrameRateStrategy = C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_OFF
            }

            player.setPlaybackSpeed(playbackSpeed.toFloat())
            return player
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
