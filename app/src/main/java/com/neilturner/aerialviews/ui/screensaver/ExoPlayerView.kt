package com.neilturner.aerialviews.ui.screensaver

import android.content.Context
import android.net.Uri
import android.util.AttributeSet
import android.util.Log
import android.view.SurfaceView
import android.widget.MediaController.MediaPlayerControl
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector.ParametersBuilder
import com.google.android.exoplayer2.video.VideoSize
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.services.SmbDataSourceFactory
import com.neilturner.aerialviews.utils.FileHelper
import kotlin.math.roundToLong

class ExoPlayerView(context: Context, attrs: AttributeSet? = null) : SurfaceView(context, attrs), MediaPlayerControl, Player.Listener {
    private val player: ExoPlayer
    private val useReducedBuffering: Boolean = GeneralPrefs.reducedBuffers
    private val enableTunneling: Boolean = GeneralPrefs.enableTunneling
    private val exceedRendererCapabilities: Boolean = GeneralPrefs.exceedRenderer
    private val muteVideo: Boolean = GeneralPrefs.muteVideos
    private var listener: OnPlayerEventListener? = null
    private var aspectRatio = 0f
    private var prepared = false
    private var playbackSpeed = GeneralPrefs.playbackSpeed

    init {
        player = buildPlayer(context)
        player.setVideoSurfaceView(this)
        player.addListener(this)
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
            Player.STATE_IDLE -> Log.i(TAG, "Player: Idle...") // 1
            Player.STATE_BUFFERING -> Log.i(TAG, "Player: Buffering...") // 2
            Player.STATE_READY -> Log.i(TAG, "Player: Ready...") // 3
            Player.STATE_ENDED -> Log.i(TAG, "Player: Ended...") // 4
        }
        if (!prepared && playbackState == Player.STATE_READY) {
            prepared = true
            listener!!.onPrepared(this)
        }
        if (playWhenReady && playbackState == Player.STATE_READY) {
            removeCallbacks(timerRunnable)
            //compensate the duration based on the playback speed
            postDelayed(timerRunnable, ((duration / GeneralPrefs.playbackSpeed.toFloat()).roundToLong() - DURATION))
        }
    }

    override fun onPlayerError(error: PlaybackException) {
        error.printStackTrace()
        super.onPlayerError(error)
    }

    override fun onPlayerErrorChanged(error: PlaybackException?) {
        error?.printStackTrace()
        super.onPlayerErrorChanged(error)
    }

    override fun onVideoSizeChanged(videoSize: VideoSize) {
        aspectRatio = if (height == 0) 0F else width * videoSize.pixelWidthHeightRatio / height
        requestLayout()
    }

    private val timerRunnable = Runnable { listener!!.onAlmostFinished(this@ExoPlayerView) }

    private fun buildPlayer(context: Context): ExoPlayer {
        val loadControl: DefaultLoadControl
        val loadControlBuilder = DefaultLoadControl.Builder()

        if (useReducedBuffering) {
            // Buffer sizes while playing
            val minBuffer = 5000
            val maxBuffer = 10000

            // Initial buffer size to start playback
            val bufferForPlayback = 1024
            val bufferForPlaybackAfterRebuffer = 1024

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
                .setExceedVideoConstraintsIfNecessary(true)
                .setExceedRendererCapabilitiesIfNecessary(true)
        }

        val trackSelector = DefaultTrackSelector(context)
        trackSelector.parameters = parametersBuilder.build()

        val player = ExoPlayer.Builder(context)
                .setLoadControl(loadControl)
                .setTrackSelector(trackSelector)
                .build()

        //player.addAnalyticsListener(EventLogger(trackSelector))
        
        if (muteVideo) {
            player.volume = 0f
        }

        player.setPlaybackSpeed(playbackSpeed.toFloat())
        return player
    }

    interface OnPlayerEventListener {
        fun onAlmostFinished(view: ExoPlayerView?)
        fun onPrepared(view: ExoPlayerView?)
        fun onError(view: ExoPlayerView?)
    }

    companion object {
        private const val TAG = "ExoPlayerView"
        const val DURATION: Long = 800
    }
}