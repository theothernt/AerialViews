@file:Suppress("JoinDeclarationAndAssignment")

package com.neilturner.aerialviews.ui.screensaver

import android.content.Context
import android.net.Uri
import android.util.AttributeSet
import android.util.Log
import android.view.SurfaceView
import android.widget.MediaController.MediaPlayerControl
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector.ParametersBuilder
import com.google.android.exoplayer2.video.VideoSize
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.models.BufferingStrategy
import com.neilturner.aerialviews.models.prefs.AppleVideoPrefs
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.services.SmbDataSourceFactory
import com.neilturner.aerialviews.utils.FileHelper
import com.neilturner.aerialviews.utils.PlayerHelper
import java.lang.Runnable
import kotlin.math.roundToLong

class ExoPlayerView(context: Context, attrs: AttributeSet? = null) : SurfaceView(context, attrs), MediaPlayerControl, Player.Listener {
    private var almostFinishedRunnable = Runnable { listener?.onAlmostFinished() }
    private val enableTunneling = GeneralPrefs.enableTunneling
    private val exceedRendererCapabilities = GeneralPrefs.exceedRenderer
    private val muteVideo = GeneralPrefs.muteVideos
    private var playbackSpeed = GeneralPrefs.playbackSpeed
    private var listener: OnPlayerEventListener? = null
    private val bufferingStrategy: BufferingStrategy
    private val player: ExoPlayer
    private var aspectRatio = 0f
    private var prepared = false

    init {
        // Use smaller buffer for local and network playback
        bufferingStrategy = if (!AppleVideoPrefs.enabled) {
            BufferingStrategy.SMALLER
        } else {
            BufferingStrategy.valueOf(GeneralPrefs.bufferingStrategy)
        }

        player = buildPlayer(context)
        player.setVideoSurfaceView(this)
        player.addListener(this)
    }

    fun release() {
        player.release()
        removeCallbacks(almostFinishedRunnable) // was causing circular reference if not cleaned up
        listener = null
    }

    fun setUri(uri: Uri?) {
        if (uri == null) {
            return
        }
        player.stop()
        prepared = false
        val mediaItem = MediaItem.fromUri(uri)

        if (FileHelper.isNetworkVideo(uri)) {
            val mediaSource = ProgressiveMediaSource.Factory(SmbDataSourceFactory())
                .createMediaSource(mediaItem)
            player.setMediaSource(mediaSource)
        } else {
            player.setMediaItem(mediaItem)
        }
        player.prepare()
    }

    override fun onDetachedFromWindow() {
        pause()
        super.onDetachedFromWindow()
    }

    @Suppress("NAME_SHADOWING")
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        var widthMeasureSpec = widthMeasureSpec
        if (aspectRatio > 0) {
            val newWidth: Int
            val newHeight: Int = MeasureSpec.getSize(heightMeasureSpec)
            newWidth = (newHeight * aspectRatio).toInt()
            widthMeasureSpec = MeasureSpec.makeMeasureSpec(newWidth, MeasureSpec.EXACTLY)
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    fun setOnPlayerListener(listener: OnPlayerEventListener?) {
        this.listener = listener
    }

    /* MediaPlayerControl */
    override fun start() {
        player.playWhenReady = true
    }

    override fun pause() {
        player.playWhenReady = false
    }

    override fun getDuration(): Int {
        return player.duration.toInt()
    }

    override fun getCurrentPosition(): Int {
        return player.currentPosition.toInt()
    }

    override fun seekTo(pos: Int) {
        player.seekTo(pos.toLong())
    }

    override fun isPlaying(): Boolean {
        return player.playWhenReady
    }

    override fun getBufferPercentage(): Int {
        return player.bufferedPercentage
    }

    override fun canPause(): Boolean {
        return player.duration > 0
    }

    override fun canSeekBackward(): Boolean {
        return player.duration > 0
    }

    override fun canSeekForward(): Boolean {
        return player.duration > 0
    }

    override fun getAudioSessionId(): Int {
        return player.audioSessionId
    }

    /* EventListener */
    override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {
        when (playbackState) {
            Player.STATE_IDLE -> Log.i(TAG, "Player: Idle...") // 1
            Player.STATE_BUFFERING -> Log.i(TAG, "Player: Buffering...") // 2
            Player.STATE_READY -> Log.i(TAG, "Player: Playing...") // 3
            Player.STATE_ENDED -> Log.i(TAG, "Player: Ended...") // 4
        }
        if (!prepared && playbackState == Player.STATE_READY) {
            prepared = true
            listener?.onPrepared()
        }
        if (playWhenReady && playbackState == Player.STATE_READY) {
            setupAlmostFinishedRunnable()
        }
    }

    fun increaseSpeed() {
        val currentSpeed = GeneralPrefs.playbackSpeed
        val speedValues = resources.getStringArray(R.array.playback_speed_values)
        val currentSpeedIdx = speedValues.indexOf(currentSpeed)
        if(currentSpeedIdx == speedValues.size - 1) {
            //we are at maximum speed already
            return
        }
        val newSpeed = speedValues.get(currentSpeedIdx + 1)
        GeneralPrefs.playbackSpeed = newSpeed
        player.setPlaybackSpeed(newSpeed.toFloat())
        setupAlmostFinishedRunnable()
    }

    fun decreaseSpeed() {
        val currentSpeed = GeneralPrefs.playbackSpeed
        val speedValues = resources.getStringArray(R.array.playback_speed_values)
        val currentSpeedIdx = speedValues.indexOf(currentSpeed)
        if(currentSpeedIdx == 0) {
            //we are at minimum speed already
            return
        }
        val newSpeed = speedValues.get(currentSpeedIdx - 1)
        GeneralPrefs.playbackSpeed = newSpeed
        player.setPlaybackSpeed(newSpeed.toFloat())
        setupAlmostFinishedRunnable()
    }

    fun setupAlmostFinishedRunnable() {
        removeCallbacks(almostFinishedRunnable)
        // compensate the duration based on the playback speed
        // take into account the current player position in case of speed changes during playback
        var delay = (((duration - player.currentPosition) / GeneralPrefs.playbackSpeed.toFloat()).roundToLong() - DURATION)
        if(delay < 0) {
            delay = 0
        }
        postDelayed(almostFinishedRunnable, delay)
    }

    override fun onPlayerError(error: PlaybackException) {
        super.onPlayerError(error)
        // error?.printStackTrace()
        error.cause?.let { Firebase.crashlytics.recordException(it) }
        removeCallbacks(almostFinishedRunnable)
        postDelayed({ listener?.onError() }, 3000)
    }

    override fun onPlayerErrorChanged(error: PlaybackException?) {
        super.onPlayerErrorChanged(error)
        // error?.printStackTrace()
        error?.message?.let { Log.e(TAG, it) }
    }

    override fun onVideoSizeChanged(videoSize: VideoSize) {
        aspectRatio = if (height == 0) 0F else width * videoSize.pixelWidthHeightRatio / height
        requestLayout()
    }

    private fun buildPlayer(context: Context): ExoPlayer {
        Log.i(TAG, "Buffering strategy: $bufferingStrategy")
        val loadControl = PlayerHelper.bufferingStrategy(bufferingStrategy).build()
        val parametersBuilder = ParametersBuilder(context)

        if (enableTunneling) {
            parametersBuilder
                .setTunnelingEnabled(true)
        }

        if (exceedRendererCapabilities) {
            parametersBuilder
                .setExceedVideoConstraintsIfNecessary(true)
                .setExceedRendererCapabilitiesIfNecessary(true)
        }

        val trackSelector = DefaultTrackSelector(context)
        trackSelector.parameters = parametersBuilder.build()

        val player = ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .setTrackSelector(trackSelector)
            .build()

        // player.addAnalyticsListener(com.google.android.exoplayer2.util.EventLogger(trackSelector))

        if (muteVideo) {
            player.volume = 0f
        }

        player.videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
        player.setPlaybackSpeed(playbackSpeed.toFloat())
        return player
    }

    interface OnPlayerEventListener {
        fun onAlmostFinished()
        fun onError()
        fun onPrepared()
    }

    companion object {
        private const val TAG = "ExoPlayerView"
        const val DURATION: Long = 1000
    }
}
