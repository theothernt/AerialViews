package com.neilturner.aerialviews.ui.screensaver

import android.content.Context
import android.graphics.Typeface
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.databinding.DataBindingUtil
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.databinding.AerialActivityBinding
import com.neilturner.aerialviews.databinding.VideoViewBinding
import com.neilturner.aerialviews.models.VideoPlaylist
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.models.prefs.InterfacePrefs
import com.neilturner.aerialviews.models.videos.AerialVideo
import com.neilturner.aerialviews.services.VideoService
import com.neilturner.aerialviews.ui.overlays.TextLocation
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
    private var typeface: Typeface? = null
    private val resources = context.resources!!

    private var shouldAlternateOverlays = InterfacePrefs.alternateTextPosition
    private var alternate = false
    private var previousVideo = false
    private var canSkip = false

    private val videoView: VideoViewBinding
    private val loadingView: View
    private var loadingText: TextView
    private var player: ExoPlayerView
    val view: View

    private val topLeftIds: List<Int>
    private val topRightIds: List<Int>
    private val bottomLeftIds: List<Int>
    private val bottomRightIds: List<Int>

    init {
        val inflater = LayoutInflater.from(context)
        val binding = DataBindingUtil.inflate(inflater, R.layout.aerial_activity, null, false) as AerialActivityBinding

        view = binding.root
        loadingView = binding.loadingView.root
        loadingText = binding.loadingView.loadingText

        videoView = binding.videoView
        player = videoView.player
        player.setOnPlayerListener(this)

        // Should try/catch etc
        // Take pref as param
        typeface = FontHelper.getTypeface(context)
        loadingText.typeface = typeface

        // Init overlays and set initial positions
        overlayHelper = OverlayHelper(context, typeface, InterfacePrefs)
        val overlayIds = overlayHelper.buildOverlaysAndIds(videoView)
        this.bottomLeftIds = overlayIds.bottomLeftIds
        this.bottomRightIds = overlayIds.bottomRightIds
        this.topLeftIds = overlayIds.topLeftIds
        this.topRightIds = overlayIds.topRightIds

        coroutineScope.launch {
            playlist = VideoService(context).fetchVideos()
            if (playlist.size > 0) {
                Log.i(TAG, "Playlist items: ${playlist.size}")
                loadVideo(playlist.nextVideo())
            } else {
                showLoadingError()
            }
        }

        // 1. Load playlist
        // 2. load video, setup location/POI, start playback call
        // 3. playback started callback, fade out loading text, fade out loading view
        // 4. when video is almost finished - or skip - fade in loading view
        // 5. goto 2
    }

    private fun loadVideo(video: AerialVideo) {
        Log.i(TAG, "Playing: ${video.location} - ${video.uri} (${video.poi})")

        // Set overlay data for current video
        overlayHelper.findOverlay(TextLocation::class)?.apply {
            updateLocationData(video.location, video.poi, InterfacePrefs.locationStyle, player)
        }

        // Set overlay positions
        overlayHelper.assignOverlaysAndIds(
            videoView.flowBottomLeft,
            videoView.flowBottomRight,
            bottomLeftIds,
            bottomRightIds,
            alternate
        )

        overlayHelper.assignOverlaysAndIds(
            videoView.flowTopLeft,
            videoView.flowTopRight,
            topLeftIds,
            topRightIds,
            alternate
        )

        if (shouldAlternateOverlays) {
            alternate = !alternate
        }

        player.setUri(video.uri)
        player.start()
    }

    private fun fadeOutLoading() {
        // Fade out TextView
        loadingText
            .animate()
            .alpha(0f)
            .setDuration(500)
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

        var fadeDuration = ExoPlayerView.FADE_DURATION
        // If first video (ie. screensaver startup), fade out 'loading...' text
        if (loadingText.visibility == View.VISIBLE) {
            fadeOutLoading()
            fadeDuration += 1500 // Fade in after 1st load should be a little longer
        }

        // Fade out LoadingView
        // Video should be playing underneath
        loadingView
            .animate()
            .alpha(0f)
            .setDuration(fadeDuration)
            .withEndAction {
                loadingView.visibility = View.GONE
                canSkip = true
            }.start()
    }

    private fun fadeOutCurrentVideo() {
        if (!canSkip) return
        canSkip = false

        overlayHelper.findOverlay(TextLocation::class)?.apply {
            isFadingOutVideo = true
        }

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

                loadVideo(video)
            }.start()
    }

    private fun showLoadingError() {
        loadingText.text = resources.getString(R.string.loading_error)
    }

    fun stop() {
        player.release()
    }

    fun skipVideo(previous: Boolean = false) {
        previousVideo = previous
        fadeOutCurrentVideo()
    }

    fun increaseSpeed() {
        player.increaseSpeed()
    }

    fun decreaseSpeed() {
        player.decreaseSpeed()
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
        val message = resources.getString(R.string.playlist_playback_speed_changed, GeneralPrefs.playbackSpeed + "x")
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }

    override fun onError() {
        if (loadingView.visibility == View.VISIBLE) {
            loadVideo(playlist.nextVideo())
        } else {
            fadeOutCurrentVideo()
        }
    }

    companion object {
        private const val TAG = "VideoController"
    }
}
