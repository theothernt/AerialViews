package com.neilturner.aerialviews.ui.core

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.SurfaceView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.models.enums.AerialMediaSource
import com.neilturner.aerialviews.models.enums.LimitLongerVideos
import com.neilturner.aerialviews.models.enums.VideoScale
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.models.videos.AerialMedia
import com.neilturner.aerialviews.services.PhilipsMediaCodecAdapterFactory
import com.neilturner.aerialviews.services.SambaDataSourceFactory
import com.neilturner.aerialviews.services.WebDavDataSourceFactory
import com.neilturner.aerialviews.ui.core.VideoPlayerHelper.buildPlayer
import com.neilturner.aerialviews.ui.core.VideoPlayerHelper.calculateDelay
import com.neilturner.aerialviews.ui.core.VideoPlayerHelper.calculateSegments
import com.neilturner.aerialviews.ui.core.VideoPlayerHelper.setRefreshRate
import timber.log.Timber

@SuppressLint("UnsafeOptInUsageError")
class VideoPlayerView(
    context: Context,
    attrs: AttributeSet? = null,
) : SurfaceView(context, attrs),
    Player.Listener {
    private val player: ExoPlayer

    private var aspectRatio = 0f
    private var prepared = false
    private var canChangePlaybackSpeed = true
    private var isSegmentedVideo = false
    private var segmentStart = 0L
    private var segmentEnd = 0L
    private var loopCount = 0
    private var videoWidth: Int = 0
    private var videoHeight: Int = 0

    private var listener: OnVideoPlayerEventListener? = null
    private var almostFinishedRunnable = Runnable { listener?.onVideoAlmostFinished() }
    private var canChangePlaybackSpeedRunnable = Runnable { this.canChangePlaybackSpeed = true }
    private var onErrorRunnable = Runnable { listener?.onVideoError() }

    private val useRefreshRateSwitching = GeneralPrefs.refreshRateSwitching
    private val philipsDolbyVisionFix = GeneralPrefs.philipsDolbyVisionFix
    private var playbackSpeed = GeneralPrefs.playbackSpeed
    private val muteVideo = GeneralPrefs.muteVideos
    private val videoScale = if (GeneralPrefs.videoScale == VideoScale.SCALE_TO_FIT) 1 else 2
    private val maxVideoLength = GeneralPrefs.maxVideoLength.toInt() * 1000
    private val segmentLongVideos = GeneralPrefs.limitLongerVideos == LimitLongerVideos.SEGMENT

    init {
        player = buildPlayer(context, GeneralPrefs)
        player.setVideoSurfaceView(this)
        player.addListener(this)
    }

    fun release() {
        player.release()
        removeCallbacks(almostFinishedRunnable)
        removeCallbacks(canChangePlaybackSpeedRunnable)
        removeCallbacks(onErrorRunnable)
        listener = null
    }

    fun setVideo(media: AerialMedia) {
        prepared = false

        player.repeatMode = Player.REPEAT_MODE_OFF
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
                player.setMediaSource(mediaSource)
            }
            AerialMediaSource.WEBDAV -> {
                val mediaSource =
                    ProgressiveMediaSource
                        .Factory(WebDavDataSourceFactory())
                        .createMediaSource(mediaItem)
                player.setMediaSource(mediaSource)
            }
            else -> {
                player.setMediaItem(mediaItem)
            }
        }

        player.prepare()

        if (muteVideo) {
            // Has to be set for each video
            // Removes audio track as well as AV receivers ignore 'mute'
            player.trackSelectionParameters =
                player.trackSelectionParameters
                    .buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
                    .build()
        }
    }

    override fun onDetachedFromWindow() {
        pause()
        super.onDetachedFromWindow()
    }

    override fun onMeasure(
        widthMeasureSpec: Int,
        heightMeasureSpec: Int,
    ) {
        if (videoScale == 2) {
            var newWidthMeasureSpec = widthMeasureSpec
            if (aspectRatio > 0) {
                val newHeight = MeasureSpec.getSize(heightMeasureSpec)
                val newWidth = (newHeight * aspectRatio).toInt()
                newWidthMeasureSpec = MeasureSpec.makeMeasureSpec(newWidth, MeasureSpec.EXACTLY)
            }
            super.onMeasure(newWidthMeasureSpec, heightMeasureSpec)
        } else {
            var width = MeasureSpec.getSize(widthMeasureSpec)
            var height = MeasureSpec.getSize(heightMeasureSpec)

            if (videoWidth > 0 && videoHeight > 0) {
                val widthRatio = width.toFloat() / videoWidth
                val heightRatio = height.toFloat() / videoHeight

                val aspectRatio =
                    if (widthRatio > heightRatio) {
                        height.toFloat() / videoHeight
                    } else {
                        width.toFloat() / videoWidth
                    }

                width = (videoWidth * aspectRatio).toInt()
                height = (videoHeight * aspectRatio).toInt()
            }
            setMeasuredDimension(width, height)
        }
    }

    override fun onSizeChanged(
        width: Int,
        height: Int,
        oldWidth: Int,
        oldHeight: Int,
    ) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        requestLayout()
    }

    fun setOnPlayerListener(listener: OnVideoPlayerEventListener?) {
        this.listener = listener
    }

    fun start() {
        player.playWhenReady = true
    }

    fun pause() {
        player.playWhenReady = false
    }

    fun stop() {
        removeCallbacks(almostFinishedRunnable)
        player.stop()
    }

    val position: Int
        get() = player.currentPosition.toInt()

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
                    val (isSegmented, segmentStart, segmentEnd) = calculateSegments(maxVideoLength, player.duration.toInt())
                    this.isSegmentedVideo = isSegmented
                    this.segmentStart = segmentStart
                    this.segmentEnd = segmentEnd
                }

                if (isSegmentedVideo && player.currentPosition !in segmentStart - 500..segmentEnd + 500) {
                    Timber.i("Seeking to segment ${segmentStart}ms")
                    player.seekTo(segmentStart)
                    return
                }
                if (isSegmentedVideo) {
                    Timber.i("At segment ${player.currentPosition}ms (target ${segmentStart}ms), continuing...")
                }
            }
            prepared = true
            listener?.onVideoPrepared()
        }

        if (player.playWhenReady && playbackState == Player.STATE_READY) {
            if (useRefreshRateSwitching) {
                setRefreshRate(player, context)
            }
            setupAlmostFinishedRunnable()
            Timber.i("Playing...")
        }
    }

    fun increaseSpeed() = changeSpeed(player, true)

    fun decreaseSpeed() = changeSpeed(player, false)

    private fun changeSpeed(
        player: ExoPlayer,
        increase: Boolean,
    ) {
        if (!canChangePlaybackSpeed) {
            return
        }

        if (!prepared || !player.isPlaying) {
            return // Must be playing a video
        }

        if (player.currentPosition <= 3) {
            return // No speed change at the start of the video
        }

        if (player.duration - player.currentPosition <= 3) {
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
        player.setPlaybackSpeed(newSpeed.toFloat())
        GeneralPrefs.playbackSpeed = playbackSpeed

        setupAlmostFinishedRunnable()
        listener?.onVideoPlaybackSpeedChanged()
    }

    private fun setupAlmostFinishedRunnable() {
        removeCallbacks(almostFinishedRunnable)
        val delay =
            calculateDelay(
                player,
                GeneralPrefs,
                isSegmentedVideo,
                segmentStart,
                segmentEnd,
                loopCount,
            )
        postDelayed(almostFinishedRunnable, delay)
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

    override fun onVideoSizeChanged(videoSize: VideoSize) {
        aspectRatio = if (height == 0) 0f else width * videoSize.pixelWidthHeightRatio / height
        if (videoSize.width != videoWidth || videoSize.height != videoHeight) {
            videoWidth = videoSize.width
            videoHeight = videoSize.height
        }
        requestLayout()
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
