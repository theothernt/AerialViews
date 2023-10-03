package com.neilturner.aerialviews.ui.screensaver

import android.content.Context
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.databinding.DataBindingUtil
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.databinding.AerialActivityBinding
import com.neilturner.aerialviews.databinding.VideoViewBinding
import com.neilturner.aerialviews.models.VideoPlaylist
import com.neilturner.aerialviews.models.enums.LocationType
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.models.prefs.InterfacePrefs
import com.neilturner.aerialviews.models.videos.AerialVideo
import com.neilturner.aerialviews.services.VideoService
import com.neilturner.aerialviews.ui.screensaver.ExoPlayerView.OnPlayerEventListener
import com.neilturner.aerialviews.utils.FontHelper
import com.neilturner.aerialviews.utils.OverlayHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class VideoController(private val context: Context) : OnPlayerEventListener {
    private val coroutineScope = CoroutineScope(Dispatchers.Main)
    private lateinit var playlist: VideoPlaylist
    private var overlayHelper: OverlayHelper

    // private lateinit var currentVideo: AerialVideo
    private val textAlpha = 1f
    private var previousVideo = false
    private var canSkip = false
    private val videoView: VideoViewBinding
    private val loadingView: View
    private var loadingText: TextView
    private var shouldAlternateTextPosition = InterfacePrefs.alternateTextPosition

    private var showClock = InterfacePrefs.clockStyle
    private var clockSize = InterfacePrefs.clockSize

    private var showLocation = InterfacePrefs.locationStyle != LocationType.OFF
    private var locationSize = InterfacePrefs.locationSize

    val view: View

    init {
        val inflater = LayoutInflater.from(context)
        val binding = DataBindingUtil.inflate(inflater, R.layout.aerial_activity, null, false) as AerialActivityBinding
        binding.videoView.player.setOnPlayerListener(this)
        overlayHelper = OverlayHelper(context)

        videoView = binding.videoView
        loadingView = binding.loadingView.root
        loadingText = binding.loadingView.loadingText
        view = binding.root

        try {
            val typeface = FontHelper.getTypeface(context)
            loadingText.typeface = typeface
            videoView.clock.typeface = typeface
            videoView.location.typeface = typeface
        } catch (e: Exception) {
            Log.e(TAG, "Exception in while ${e.message}")
        }

        videoView.showClock = showClock
        videoView.clock.setTextSize(TypedValue.COMPLEX_UNIT_SP, clockSize.toFloat())
        videoView.showLocation = showLocation
        videoView.location.setTextSize(TypedValue.COMPLEX_UNIT_SP, locationSize.toFloat())

        val service = VideoService(context)
        coroutineScope.launch {
            playlist = service.fetchVideos()
            Log.i(TAG, "Playlist items: ${playlist.size}")
            if (playlist.size > 0) {
                loadVideo(videoView, playlist.nextVideo())
            } else {

                showLoadingError(context)
            }
        }

        // 1. Load playlist
        // 2. load video, setup location/POI, start playback call
        // 3. playback started callback, fade out loading text, fade out loading view
        // 4. when video is almost finished - or skip - fade in loading view
        // 5. goto 2
    }

    private fun loadVideo(videoBinding: VideoViewBinding, video: AerialVideo) {
        Log.i(TAG, "Playing: ${video.location} - ${video.uri} (${video.poi})")

        // overlayHelper.loadOverlays()
        // currentVideo = video

        // If show-location:
        // OverlayHelper.addLocationData(video.location, video.poi)

        // OverlayHelper.loadOverlays(flow1, "slot_bottom_left1", "slot_bottom_left2")
        // ...
        // ...
        // OverlayHelper.loadOverlays(flow1, "slot_top_right1", "slot_top_right2")

        // TextLocation (context, poi)
        // AltTextClock
        // TextDate
        // TextMessage1, TextMessage2
        // MusicText / NowPlayingText

        // 5. Set new video and init playback
        videoBinding.player.setUri(video.uri)
        videoBinding.player.start()
    }

    private fun fadeOutLoading() {
        // Fade out TextView
        loadingText
            .animate()
            .alpha(0f)
            .setDuration(1000)
            .withEndAction {
                loadingText.visibility = TextView.GONE
            }.start()
    }

    private fun fadeInNextVideo() {
        // LoadingView should always be hidden/gone
        // Remove?
        if (loadingView.visibility == View.GONE) {
            return
        }

        // If first video (ie. screensaver startup), fade out 'loading...' text
        if (loadingText.visibility == View.VISIBLE) {
            fadeOutLoading()
        }

        // Fade out LoadingView
        // Video should be playing underneath
        loadingView
            .animate()
            .alpha(0f)
            .setDuration(ExoPlayerView.FADE_DURATION)
            .withEndAction {
                loadingView.visibility = View.GONE
                canSkip = true
            }.start()
    }

    private fun fadeOutCurrentVideo() {
        if (!canSkip) return
        canSkip = false

        // Fade in LoadView (ie. black screen)
        loadingView
            .animate()
            .alpha(1f)
            .setDuration(ExoPlayerView.FADE_DURATION)
            .withStartAction {
                loadingView.visibility = View.VISIBLE
            }
            .withEndAction {
                // Pick next/previous video
                val video = if (!previousVideo) {
                    playlist.nextVideo()
                } else {
                    playlist.previousVideo()
                }
                previousVideo = false

                // Setting text + alpha on fade out
                // Should be moved?
                videoView.location.text = ""
                videoView.location.alpha = textAlpha

                loadVideo(videoView, video)

                // Change text alignment per video
                // Should be moved?
                if (shouldAlternateTextPosition) {
                    videoView.shouldAlternateTextPosition = !videoView.shouldAlternateTextPosition
                }
            }.start()
    }

    private fun showLoadingError(context: Context) {
        val res = context.resources!!
        loadingText.text = res.getString(R.string.loading_error)
    }

    fun stop() {
        videoView.player.release()
    }

    fun skipVideo(previous: Boolean = false) {
        previousVideo = previous
        fadeOutCurrentVideo()
    }

    fun increaseSpeed() {
        videoView.player.increaseSpeed()
    }

    fun decreaseSpeed() {
        videoView.player.decreaseSpeed()
    }

    override fun onPrepared() {
        // Player has buffered video and has started playback
        fadeInNextVideo()
    }

    override fun onAlmostFinished() {
        // Player indicates video is nearly over
        fadeOutCurrentVideo()
    }

    override fun onPlaybackSpeedChanged() {
        val message = "Playback speed changed to: ${GeneralPrefs.playbackSpeed}x"
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }

    override fun onError() {
        // val message = "Error while trying to play ${currentVideo.uri}"
        // Toast.makeText(context, message, Toast.LENGTH_LONG).show()

        if (loadingView.visibility == View.VISIBLE) {
            loadVideo(videoView, playlist.nextVideo())
        } else {
            fadeOutCurrentVideo()
        }
    }

    companion object {
        private const val TAG = "VideoController"
    }
}
