package com.neilturner.aerialviews.ui.core

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.util.AttributeSet
import android.util.Log
import android.view.SurfaceView
import android.widget.MediaController.MediaPlayerControl
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.util.EventLogger
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.models.enums.AerialMediaSource
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.models.videos.AerialMedia
import com.neilturner.aerialviews.services.CustomRendererFactory
import com.neilturner.aerialviews.services.PhilipsMediaCodecAdapterFactory
import com.neilturner.aerialviews.services.SambaDataSourceFactory
import com.neilturner.aerialviews.services.WebDavDataSourceFactory
import com.neilturner.aerialviews.utils.WindowHelper
import kotlin.math.ceil
import kotlin.math.roundToLong
import kotlin.time.Duration.Companion.milliseconds

@SuppressLint("UnsafeOptInUsageError")
class VideoPlayerView(context: Context, attrs: AttributeSet? = null) : SurfaceView(context, attrs), MediaPlayerControl, Player.Listener {
    private var almostFinishedRunnable = Runnable { listener?.onVideoAlmostFinished() }
    private var canChangePlaybackSpeedRunnable = Runnable { this.canChangePlaybackSpeed = true }
    private var onErrorRunnable = Runnable { listener?.onVideoError() }
    private val enableTunneling = GeneralPrefs.enableTunneling
    private val useRefreshRateSwitching = GeneralPrefs.refreshRateSwitching
    private val philipsDolbyVisionFix = GeneralPrefs.philipsDolbyVisionFix
    private var fallbackDecoders = GeneralPrefs.allowFallbackDecoders
    private var extraLogging = GeneralPrefs.enablePlaybackLogging
    private val maxVideoLength = GeneralPrefs.maxVideoLength.toInt() * 1000
    private var playbackSpeed = GeneralPrefs.playbackSpeed
    private var loopShortVideos = GeneralPrefs.loopShortVideos
    private var segmentLongVideos = GeneralPrefs.segmentLongVideos
    private val muteVideo = GeneralPrefs.muteVideos
    private var listener: OnVideoPlayerEventListener? = null
    private var canChangePlaybackSpeed = true
    private val player: ExoPlayer
    private var aspectRatio = 0f
    private var prepared = false

    init {
        player = buildPlayer(context)
        player.setVideoSurfaceView(this)
        player.addListener(this)
        player.repeatMode = Player.REPEAT_MODE_ALL

        // https://medium.com/androiddevelopers/prep-your-tv-app-for-android-12-9a859d9bb967
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && useRefreshRateSwitching) {
            Log.i(TAG, "Android 12, handle frame rate switching in app")
            player.videoChangeFrameRateStrategy = C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_OFF
        }
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

        val uri = media.uri
        val mediaItem = MediaItem.fromUri(uri)

        if (philipsDolbyVisionFix) {
            PhilipsMediaCodecAdapterFactory.mediaUrl = uri.toString()
        }

        when (media.source) {
            AerialMediaSource.SAMBA -> {
                val mediaSource =
                    ProgressiveMediaSource.Factory(SambaDataSourceFactory())
                        .createMediaSource(mediaItem)
                player.setMediaSource(mediaSource)
            }
            AerialMediaSource.WEBDAV -> {
                val mediaSource =
                    ProgressiveMediaSource.Factory(WebDavDataSourceFactory())
                        .createMediaSource(mediaItem)
                player.setMediaSource(mediaSource)
            }
            else -> {
                player.setMediaItem(mediaItem)
            }
        }

        player.prepare()

        if (muteVideo) {
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
        var newWidthMeasureSpec = widthMeasureSpec
        if (aspectRatio > 0) {
            val newHeight = MeasureSpec.getSize(heightMeasureSpec)
            val newWidth = (newHeight * aspectRatio).toInt()
            newWidthMeasureSpec = MeasureSpec.makeMeasureSpec(newWidth, MeasureSpec.EXACTLY)
        }
        super.onMeasure(newWidthMeasureSpec, heightMeasureSpec)
    }

    fun setOnPlayerListener(listener: OnVideoPlayerEventListener?) {
        this.listener = listener
    }

    // MediaPlayerControl
    override fun start() {
        player.playWhenReady = true
    }

    override fun pause() {
        player.playWhenReady = false
    }

    fun stop() {
        removeCallbacks(almostFinishedRunnable)
        player.stop()
        player.seekTo(0)
    }

    override fun getDuration(): Int = player.duration.toInt()

    override fun getCurrentPosition(): Int = player.currentPosition.toInt()

    override fun seekTo(pos: Int) = player.seekTo(pos.toLong())

    override fun isPlaying(): Boolean = player.playWhenReady

    override fun getBufferPercentage(): Int = player.bufferedPercentage

    override fun canPause(): Boolean = player.duration > 0

    override fun canSeekBackward(): Boolean = player.duration > 0

    override fun canSeekForward(): Boolean = player.duration > 0

    @SuppressLint("UnsafeOptInUsageError")
    override fun getAudioSessionId(): Int = player.audioSessionId

    // EventListener
    override fun onPlaybackStateChanged(playbackState: Int) {
        when (playbackState) {
            Player.STATE_IDLE -> Log.i(TAG, "Idle...") // 1
            Player.STATE_BUFFERING -> Log.i(TAG, "Buffering...") // 2
            Player.STATE_READY -> Log.i(TAG, "Playing...") // 3
            Player.STATE_ENDED -> Log.i(TAG, "Playback ended...") // 4
        }

        if (!prepared && playbackState == Player.STATE_READY) {
            prepared = true
            listener?.onVideoPrepared()
        }

        if (player.playWhenReady && playbackState == Player.STATE_READY) {
            if (useRefreshRateSwitching) {
                setRefreshRate()
            }
            setupAlmostFinishedRunnable()
        }

        if (segmentLongVideos &&
            duration > maxVideoLength
        ) {
            val segments = duration / maxVideoLength

            if (segments < 2) {
                return
            }
            val length = duration.floorDiv(segments).milliseconds.inWholeSeconds
            val random = (1..segments).random()
            Log.i(TAG, "Video is ${duration.milliseconds}, Segments: $segments, Picking: $random")

            val segmentStart = (random - 1) * length
            val segmentEnd = random * length
            Log.i(TAG, "Random segments: $segmentStart - $segmentEnd")
        }
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun setRefreshRate() {
        val frameRate = player.videoFormat?.frameRate
        // val surface = this.holder.surface

        if (frameRate == null || frameRate == 0f) {
            Log.i(TAG, "Unable to get video frame rate...")
            return
        }

        Log.i(TAG, "${frameRate}fps video, setting refresh rate if needed...")
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

        if (!prepared || !player.isPlaying) {
            return // Must be playing a video
        }

        if (player.currentPosition <= 3) {
            return // No speed change at the start of the video
        }

        if (duration - player.currentPosition <= 3) {
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

        // Set initial duration to actual length of video
        var targetDuration = duration

        // Check if we need to loop the video
        if (loopShortVideos &&
            maxVideoLength != 0 &&
            duration < maxVideoLength
        ) {
            val loopCount = ceil(maxVideoLength / duration.toDouble()).toInt()
            Log.i(TAG, "Video is ${duration.milliseconds}, limit is ${maxVideoLength.milliseconds}, looping $loopCount times")
            targetDuration = duration * loopCount
        }

        // Check if we need to limit the duration of the video
        val tenSeconds = 10 * 1000
        if (maxVideoLength in tenSeconds until duration
        ) {
            Log.i(TAG, "Video is ${duration.milliseconds}, limit is ${maxVideoLength.milliseconds}, limiting duration")
            targetDuration = maxVideoLength
        }

        if (duration == targetDuration) {
            Log.i(TAG, "As-is")
        }

        // compensate the duration based on the playback speed
        // take into account the current player position in case of speed changes during playback
        var delay = (((targetDuration - player.currentPosition) / playbackSpeed.toFloat()).roundToLong() - ScreenController.ITEM_FADE_OUT)
        if (delay < 0) {
            delay = 0
        }
        postDelayed(almostFinishedRunnable, delay)
    }

    override fun onPlayerError(error: PlaybackException) {
        super.onPlayerError(error)
        removeCallbacks(almostFinishedRunnable)
        postDelayed(onErrorRunnable, ScreenController.ERROR_DELAY)
    }

    override fun onPlayerErrorChanged(error: PlaybackException?) {
        super.onPlayerErrorChanged(error)
        error?.message?.let { Log.e(TAG, it) }
    }

    override fun onVideoSizeChanged(videoSize: VideoSize) {
        aspectRatio = if (height == 0) 0f else width * videoSize.pixelWidthHeightRatio / height
        requestLayout()
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
            ExoPlayer.Builder(context)
                .setTrackSelector(trackSelector)
                .setRenderersFactory(rendererFactory)
                .build()

        if (extraLogging) {
            player.addAnalyticsListener(EventLogger())
        }

        if (muteVideo) {
            player.volume = 0f
        }

        player.videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
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
        private const val TAG = "VideoPlayerView"
        const val CHANGE_PLAYBACK_SPEED_DELAY: Long = 2000
    }
}
