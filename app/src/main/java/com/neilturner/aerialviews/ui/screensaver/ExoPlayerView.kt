@file:Suppress("JoinDeclarationAndAssignment")

package com.neilturner.aerialviews.ui.screensaver

import android.app.Activity
import android.content.Context
import android.hardware.display.DisplayManager
import android.hardware.display.DisplayManager.MATCH_CONTENT_FRAMERATE_ALWAYS
import android.net.Uri
import android.os.Build
import android.util.AttributeSet
import android.util.Log
import android.view.Display
import android.view.Surface
import android.view.Surface.CHANGE_FRAME_RATE_ALWAYS
import android.view.Surface.CHANGE_FRAME_RATE_ONLY_IF_SEAMLESS
import android.view.Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE
import android.view.SurfaceView
import android.widget.MediaController.MediaPlayerControl
import android.widget.Toast
import androidx.annotation.RequiresApi
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.video.VideoSize
import com.neilturner.aerialviews.BuildConfig
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.models.BufferingStrategy
import com.neilturner.aerialviews.models.prefs.AppleVideoPrefs
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.services.SmbDataSourceFactory
import com.neilturner.aerialviews.utils.FileHelper
import com.neilturner.aerialviews.utils.PlayerHelper
import kotlin.math.roundToLong

class ExoPlayerView(context: Context, attrs: AttributeSet? = null) : SurfaceView(context, attrs), MediaPlayerControl, Player.Listener {
    private var almostFinishedRunnable = Runnable { listener?.onAlmostFinished() }
    private var canChangePlaybackSpeedRunnable = Runnable { canChangePlaybackSpeed = true }
    private var onErrorRunnable = Runnable { listener?.onError() }
    private val enableTunneling = GeneralPrefs.enableTunneling
    private val exceedRendererCapabilities = GeneralPrefs.exceedRenderer
    private val muteVideo = GeneralPrefs.muteVideos
    private var playbackSpeed = GeneralPrefs.playbackSpeed
    private var listener: OnPlayerEventListener? = null
    private val bufferingStrategy: BufferingStrategy
    private var canChangePlaybackSpeed = true
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

        // https://medium.com/androiddevelopers/prep-your-tv-app-for-android-12-9a859d9bb967

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Log.i(TAG, "Android 12, handle frame rate switching in app")
            player.videoChangeFrameRateStrategy = C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_OFF
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    fun setRefreshRate(context: Context, surface: Surface, newRefreshRate: Float) {
        // https://gist.github.com/pflammertsma/5a453e24938722b4218528a3e5a60259#file-mainactivity-kt

        /* Copyright 2021 Google LLC.
        SPDX-License-Identifier: Apache-2.0 */

        // Determine whether the transition will be seamless.
        // Non-seamless transitions may cause a 1-2 second black screen.
        val refreshRates = display?.mode?.alternativeRefreshRates?.toList()
        val willBeSeamless = refreshRates?.contains(newRefreshRate)
        if (willBeSeamless == true) {
            Log.i(TAG, "Trying seamless...")
            // Set the frame rate, but only if the transition will be seamless.
            surface.setFrameRate(newRefreshRate,
                FRAME_RATE_COMPATIBILITY_FIXED_SOURCE,
                CHANGE_FRAME_RATE_ONLY_IF_SEAMLESS)
        } else {
            Log.i(TAG, "Trying non-seamless...")
            val prefersNonSeamless = (context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager)
                .matchContentFrameRateUserPreference == MATCH_CONTENT_FRAMERATE_ALWAYS
            if (prefersNonSeamless) {
                // Show UX to inform the user that a switch is about to occur
                //showUxForNonSeamlessSwitchWithDelay();
                // Set the frame rate if the user has requested it to match content
                surface.setFrameRate(newRefreshRate,
                    FRAME_RATE_COMPATIBILITY_FIXED_SOURCE,
                    CHANGE_FRAME_RATE_ALWAYS)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    fun setLegacyRefreshRate(context: Context, newRefreshRate: Float) {
        // https://github.com/moneytoo/Player/blob/6d3dc72734d7d9d2df2267eaf35cc473ac1dd3b4/app/src/main/java/com/brouken/player/Utils.java

        val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val display = displayManager.displays[0]
        val supportedModes = display.supportedModes
        val activeMode = display.mode

        Log.i(TAG, "Supported modes: ${supportedModes.size}")
        if (supportedModes.size > 1) {
            // Refresh rate >= video FPS
            val modesHigh = mutableListOf<Display.Mode>()

            // Max refresh rate
            var modeTop = activeMode
            var modesResolutionCount = 0

            // Filter only resolutions same as current
            for (mode in supportedModes) {
                if (mode.physicalWidth == activeMode.physicalWidth &&
                    mode.physicalHeight == activeMode.physicalHeight
                ) {
                    modesResolutionCount++

                    if (normRate(mode.refreshRate) >= normRate(newRefreshRate))
                        modesHigh.add(mode)

                    if (normRate(mode.refreshRate) > normRate(modeTop.refreshRate))
                        modeTop = mode
                }
            }

            Log.i(TAG, "Available modes: $modesResolutionCount")
            if (modesResolutionCount > 1) {
                var modeBest: Display.Mode? = null
                var modes = "Available refreshRates:"

                for (mode in modesHigh) {
                    modes += " " + mode.refreshRate
                    if (normRate(mode.refreshRate) % normRate(newRefreshRate) <= 0.0001f) {
                        if (modeBest == null || normRate(mode.refreshRate) > normRate(modeBest.refreshRate)) {
                            modeBest = mode
                        }
                    }
                }

                Log.i(TAG, "Trying to change window properties...")
                val activity = context as? Activity
                if (activity == null) {
                    Log.i(TAG, "Unable to get Window object")
                    return
                }

                val window = activity.window
                val layoutParams = window.attributes

                if (modeBest == null)
                    modeBest = modeTop

                val switchingModes = modeBest?.modeId != activeMode?.modeId
                if (switchingModes) {
                    Log.i(TAG, "Switching mode from ${activeMode?.modeId} to ${modeBest?.modeId}")
                    layoutParams.preferredDisplayModeId = modeBest?.modeId!!
                    window.attributes = layoutParams
                } else {
                    Log.i(TAG, "Already in mode ${activeMode?.modeId}, no need to change.")
                }

                if (BuildConfig.DEBUG)
                    Toast.makeText(activity, modes + "\n" +
                            "Video frameRate: " + newRefreshRate + "\n" +
                            "Current display refreshRate: " + modeBest?.refreshRate, Toast.LENGTH_LONG).show()
            }
        } else {
            Log.i(TAG, "Only 1 mode found, exiting")
        }
    }

    private fun normRate(rate: Float): Int {
        return (rate * 100f).toInt()
    }

    fun release() {
        player.release()
        removeCallbacks(almostFinishedRunnable)
        removeCallbacks(canChangePlaybackSpeedRunnable)
        removeCallbacks(onErrorRunnable)
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

    override fun onMeasure(_widthMeasureSpec: Int, heightMeasureSpec: Int) {
        var widthMeasureSpec = _widthMeasureSpec
        if (aspectRatio > 0) {
            val newHeight = MeasureSpec.getSize(heightMeasureSpec)
            val newWidth = (newHeight * aspectRatio).toInt()
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
    override fun onPlaybackStateChanged(playbackState: Int) {
        when (playbackState) {
            Player.STATE_IDLE -> Log.i(TAG, "Idle...") // 1
            Player.STATE_BUFFERING -> Log.i(TAG, "Buffering...") // 2
            Player.STATE_READY -> Log.i(TAG, "Playing...") // 3
            Player.STATE_ENDED -> Log.i(TAG, "Playback ended...") // 4
        }

        if (!prepared && playbackState == Player.STATE_READY) {
            prepared = true
            listener?.onPrepared()
        }

        if (player.playWhenReady && playbackState == Player.STATE_READY) {
            setupAlmostFinishedRunnable()
        }

        if (playbackState == Player.STATE_READY) {
            val frameRate = player.videoFormat?.frameRate
            val surface = this.holder.surface

            if (frameRate == null || frameRate == 0f) {
                Log.i(TAG, "Unable to get video frame rate...")
                return
            }

            Log.i(TAG, "${frameRate}fps video, setting refresh rate if needed...")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Log.i(TAG, "Android 12")
                setRefreshRate(context, surface, frameRate)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Log.i(TAG, "Not Android 12")
                setLegacyRefreshRate(context, frameRate)
            }
        }
    }

    fun increaseSpeed() {
        changeSpeed(true)
    }

    fun decreaseSpeed() {
        changeSpeed(false)
    }

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
        postDelayed(canChangePlaybackSpeedRunnable, 2000)

        val currentSpeed = GeneralPrefs.playbackSpeed
        val speedValues = resources.getStringArray(R.array.playback_speed_values)
        val currentSpeedIdx = speedValues.indexOf(currentSpeed)

        if (!increase && currentSpeedIdx == 0) {
            return // we are at minimum speed already
        }

        if (increase && currentSpeedIdx == speedValues.size - 1) {
            return // we are at maximum speed already
        }

        val newSpeed = if (increase) {
            speedValues[currentSpeedIdx + 1]
        } else {
            speedValues[currentSpeedIdx - 1]
        }

        GeneralPrefs.playbackSpeed = newSpeed
        player.setPlaybackSpeed(newSpeed.toFloat())

        setupAlmostFinishedRunnable()
        listener?.onPlaybackSpeedChanged()
    }

    private fun setupAlmostFinishedRunnable() {
        removeCallbacks(almostFinishedRunnable)
        // compensate the duration based on the playback speed
        // take into account the current player position in case of speed changes during playback
        var delay = (((duration - player.currentPosition) / GeneralPrefs.playbackSpeed.toFloat()).roundToLong() - DURATION)
        if (delay < 0) {
            delay = 0
        }
        postDelayed(almostFinishedRunnable, delay)
    }

    override fun onPlayerError(error: PlaybackException) {
        super.onPlayerError(error)
        // error?.printStackTrace()
        // error.cause?.let { Firebase.crashlytics.recordException(it) }
        removeCallbacks(almostFinishedRunnable)
        postDelayed(onErrorRunnable, 3000)
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
        val parametersBuilder = DefaultTrackSelector.Parameters.Builder(context)

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
        fun onPlaybackSpeedChanged()
    }

    companion object {
        private const val TAG = "ExoPlayerView"
        const val DURATION: Long = 1000
    }
}
