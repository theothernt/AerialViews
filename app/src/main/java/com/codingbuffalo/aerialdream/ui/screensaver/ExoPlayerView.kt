package com.codingbuffalo.aerialdream.ui.screensaver

import android.content.Context
import android.net.Uri
import android.util.AttributeSet
import android.util.Log
import android.view.SurfaceView
import android.widget.MediaController.MediaPlayerControl
import com.codingbuffalo.aerialdream.models.prefs.GeneralPrefs
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector.ParametersBuilder

class ExoPlayerView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : SurfaceView(context, attrs), MediaPlayerControl, Player.Listener {
    private val player: SimpleExoPlayer
    private var mediaItem: MediaItem? = null
    private var listener: OnPlayerEventListener? = null
    private var shouldRetry = true
    private var retries = 0
    private var aspectRatio = 0f
    private var useReducedBuffering: Boolean
    private var enableTunneling: Boolean
    private var exceedRendererCapabilities: Boolean
    private var muteVideo: Boolean
    private var prepared = false

    init {
        muteVideo = GeneralPrefs.muteVideos
        enableTunneling = GeneralPrefs.enableTunneling
        useReducedBuffering = GeneralPrefs.reducedBuffers
        exceedRendererCapabilities = GeneralPrefs.exceedRenderer

        player = buildPlayer(context)
        player.setVideoSurfaceView(this)
        player.addVideoListener(this)
        player.addListener(this)
    }

    fun setUri(uri: Uri?, retry: Boolean) {
        if (uri == null) {
            return
        }
        player.stop()
        prepared = false
        shouldRetry = retry
        retries = 0
        mediaItem = MediaItem.fromUri(uri)
        player.setMediaItem(mediaItem!!)
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

    fun release() {
        player.release()
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
            Player.STATE_BUFFERING -> Log.i("ExoPlayerView", "Player: Buffering...")
            Player.STATE_READY -> Log.i("ExoPlayerView", "Player: Ready...")
            Player.STATE_IDLE -> Log.i("ExoPlayerView", "Player: Idle...")
            Player.STATE_ENDED -> Log.i("ExoPlayerView", "Player: Ended...")
            else -> {
            }
        }
        if (!prepared && playbackState == Player.STATE_READY) {
            prepared = true
            listener!!.onPrepared(this)
        }
        if (playWhenReady && playbackState == Player.STATE_READY) {
            removeCallbacks(timerRunnable)
            postDelayed(timerRunnable, duration - DURATION)
        }
    }

    override fun onPlayerError(error: ExoPlaybackException) {
        error.printStackTrace()

        // Attempt to reload video
        if (shouldRetry) {
            removeCallbacks(errorRecoveryRunnable)
            postDelayed(errorRecoveryRunnable, DURATION)
        }
    }

    override fun onVideoSizeChanged(width: Int, height: Int, unappliedRotationDegrees: Int, pixelWidthHeightRatio: Float) {
        aspectRatio = if (height == 0) 0F else width * pixelWidthHeightRatio / height
        requestLayout()
    }

    private val timerRunnable = Runnable { listener!!.onAlmostFinished(this@ExoPlayerView) }
    private val errorRecoveryRunnable = Runnable {
        retries++
        Log.i("ExoPlayerView", "Retries: $retries")
        if (retries >= MAX_RETRIES) {
            listener!!.onError(this@ExoPlayerView)
        } else {
            player.stop()
            player.setMediaItem(mediaItem!!)
            player.prepare()
        }
    }

    private fun buildPlayer(context: Context): SimpleExoPlayer {
        val loadControl: DefaultLoadControl
        val loadControlBuilder = DefaultLoadControl.Builder()

        if (useReducedBuffering) {
            // Buffer sizes while playing
            val minBuffer = 5000
            val maxBuffer = 10000

            // Initial buffer size to start playback
            val bufferForPlayback = 1000
            val bufferForPlaybackAfterRebuffer = 5000
            loadControlBuilder
                    .setBufferDurationsMs(
                            minBuffer,
                            maxBuffer,
                            bufferForPlayback,
                            bufferForPlaybackAfterRebuffer)
        }
        loadControl = loadControlBuilder.build()
        val parametersBuilder = ParametersBuilder(context)

        if (enableTunneling) {
            parametersBuilder
                    .setTunnelingEnabled(true)
        }

        if (exceedRendererCapabilities) {
            parametersBuilder
                    .setExceedRendererCapabilitiesIfNecessary(true)
        }
        val trackSelector = DefaultTrackSelector(context)
        trackSelector.setParameters(parametersBuilder)

        val player = SimpleExoPlayer.Builder(context)
                .setLoadControl(loadControl)
                .setTrackSelector(trackSelector)
                .build()

        if (muteVideo) {
            player.volume = 0f
        }
        return player
    }

    interface OnPlayerEventListener {
        fun onAlmostFinished(view: ExoPlayerView?)
        fun onPrepared(view: ExoPlayerView?)
        fun onError(view: ExoPlayerView?)
    }

    companion object {
        const val DURATION: Long = 800
        const val MAX_RETRIES: Long = 1
    }
}