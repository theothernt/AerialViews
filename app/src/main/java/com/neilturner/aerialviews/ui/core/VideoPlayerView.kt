package com.neilturner.aerialviews.ui.core

import android.content.Context
import android.util.AttributeSet
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.neilturner.aerialviews.models.videos.AerialMedia

class VideoPlayerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : PlayerView (context, attrs, defStyleAttr),
    Player.Listener {

    private var listener: OnVideoPlayerEventListener? = null

    init {

    }

    override fun onAttachedToWindow() {
        player = ExoPlayer.Builder(context).build()

        // Create a MediaItem
        //val mediaItem = MediaItem.fromUri("your_video_url_or_path")

        // Set the media item to be played
        //player?.setMediaItem(mediaItem)

        // Prepare the player
        //player?.prepare()

        // Start the playback
        //player?.play()

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