package com.neilturner.aerialviews.ui.core

import android.content.Context
import android.util.AttributeSet
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.neilturner.aerialviews.models.videos.AerialMedia
import timber.log.Timber

class VideoPlayerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : PlayerView (context, attrs, defStyleAttr),
    Player.Listener {

    private var listener: OnVideoPlayerEventListener? = null

    init {
        Timber.i("VideoPlayerView init")
    }

    @OptIn(UnstableApi::class)
    override fun onAttachedToWindow() {
        Timber.i("VideoPlayerView onAttachedToWindow")
        player = ExoPlayer.Builder(context).build()

        useController = false

        // Create a MediaItem
        val mediaItem = MediaItem.fromUri("https://storage.googleapis.com/exoplayer-test-media-1/mkv/android-screens-lavf-56.36.100-aac-avc-main-1280x720.mkv")
        Timber.i("VideoPlayerView ${mediaItem.localConfiguration?.uri}")

        // Set the media item to be played
        player?.setMediaItem(mediaItem)

        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM

        // Prepare the player
        player?.prepare()

        // Start the playback
        player?.playWhenReady = true

        listener?.onVideoPrepared()

        super.onAttachedToWindow()
    }

    fun increaseSpeed() {

    }

    fun decreaseSpeed() {

    }

    val currentPosition: Int = player?.currentPosition?.toInt() ?: 0

    fun setVideo(media: AerialMedia) {

    }

    fun start() {
        player?.playWhenReady = true
    }

    fun pause() {
        player?.playWhenReady = false
    }

    fun stop() {
        player?.stop()
    }

    fun release() {

    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()

    }

    fun setOnPlayerListener(listener: OnVideoPlayerEventListener?) {
        this.listener = listener
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