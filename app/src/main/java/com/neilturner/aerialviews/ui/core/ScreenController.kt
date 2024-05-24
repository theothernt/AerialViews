package com.neilturner.aerialviews.ui.core

import android.content.Context
import android.graphics.Color
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.databinding.DataBindingUtil
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.databinding.AerialActivityBinding
import com.neilturner.aerialviews.databinding.ImageViewBinding
import com.neilturner.aerialviews.databinding.OverlayViewBinding
import com.neilturner.aerialviews.databinding.VideoViewBinding
import com.neilturner.aerialviews.models.MediaPlaylist
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.models.videos.AerialMedia
import com.neilturner.aerialviews.services.MediaService
import com.neilturner.aerialviews.ui.core.ImagePlayerView.OnImagePlayerEventListener
import com.neilturner.aerialviews.ui.core.VideoPlayerView.OnVideoPlayerEventListener
import com.neilturner.aerialviews.ui.overlays.TextLocation
import com.neilturner.aerialviews.utils.FileHelper
import com.neilturner.aerialviews.utils.FontHelper
import com.neilturner.aerialviews.utils.OverlayHelper
import com.neilturner.aerialviews.utils.filename
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ScreenController(private val context: Context) :
    OnVideoPlayerEventListener,
    OnImagePlayerEventListener {

    private val coroutineScope = CoroutineScope(Dispatchers.Main)
    private lateinit var playlist: MediaPlaylist
    private var overlayHelper: OverlayHelper
    private val resources = context.resources!!

    private var shouldAlternateOverlays = GeneralPrefs.alternateTextPosition
    private var alternate = false
    private var previousItem = false
    private var canSkip = false

    private val videoView: VideoViewBinding
    private val imageView: ImageViewBinding
    private val overlayView: OverlayViewBinding
    private val loadingView: View
    private var loadingText: TextView
    private var videoPlayer: VideoPlayerView
    private var imagePlayer: ImagePlayerView
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
        overlayView = binding.overlayView

        videoView = binding.videoView
        videoPlayer = videoView.videoPlayer
        videoPlayer.setOnPlayerListener(this)

        imageView = binding.imageView
        imagePlayer = imageView.imagePlayer
        imagePlayer.setOnPlayerListener(this)
        imageView.root.setBackgroundColor(Color.BLACK)

        if (GeneralPrefs.showLoadingText) {
            loadingText.apply {
                textSize = GeneralPrefs.loadingTextSize.toFloat()
                typeface = FontHelper.getTypeface(context, GeneralPrefs.fontTypeface, GeneralPrefs.loadingTextWeight)
            }
        } else {
            loadingText.visibility = View.INVISIBLE
        }

        // Init overlays and set initial positions
        overlayHelper = OverlayHelper(context, GeneralPrefs)
        val overlayIds = overlayHelper.buildOverlaysAndIds(overlayView)
        this.bottomLeftIds = overlayIds.bottomLeftIds
        this.bottomRightIds = overlayIds.bottomRightIds
        this.topLeftIds = overlayIds.topLeftIds
        this.topRightIds = overlayIds.topRightIds

        // Gradients
        if (GeneralPrefs.showTopGradient) {
            overlayView.gradientTop.visibility = View.VISIBLE
        }

        if (GeneralPrefs.showBottomGradient) {
            overlayView.gradientBottom.visibility = View.VISIBLE
        }

        coroutineScope.launch {
            playlist = MediaService(context).fetchMedia()
            if (playlist.size > 0) {
                Log.i(TAG, "Playlist items: ${playlist.size}")
                loadItem(playlist.nextItem())
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

    private fun loadItem(media: AerialMedia) {
        if (media.uri.toString().contains("smb://")) {
            val pattern = Regex("(smb://)([^:]+):([^@]+)@([\\d\\.]+)/")
            val replacement = "$1****:****@****/"
            val url = pattern.replace(media.uri.toString(), replacement)
            Log.i(TAG, "Playing: ${media.location} - $url (${media.poi})")
        } else {
            Log.i(TAG, "Playing: ${media.location} - ${media.uri} (${media.poi})")
        }

        // Set overlay data for current video
        overlayHelper.findOverlay<TextLocation>().forEach {
            val locationType = GeneralPrefs.locationStyle
            if (locationType != null) {
                it.updateLocationData(media.location, media.poi, locationType, videoPlayer)
            }
        }

        // Set overlay positions
        overlayHelper.assignOverlaysAndIds(
            overlayView.flowBottomLeft,
            overlayView.flowBottomRight,
            bottomLeftIds,
            bottomRightIds,
            alternate
        )

        overlayHelper.assignOverlaysAndIds(
            overlayView.flowTopLeft,
            overlayView.flowTopRight,
            topLeftIds,
            topRightIds,
            alternate
        )

        if (shouldAlternateOverlays) {
            alternate = !alternate
        }

        // Videos
        if (FileHelper.isSupportedVideoType(media.uri.filename)) {
            videoPlayer.setUri(media.uri)
            videoView.root.visibility = View.VISIBLE
            imageView.root.visibility = View.INVISIBLE
        }

        // Images
        if (FileHelper.isSupportedImageType(media.uri.filename)) {
            imagePlayer.setUri(media.uri)
            imageView.root.visibility = View.VISIBLE
            videoView.root.visibility = View.INVISIBLE
        }

        videoPlayer.start()
    }

    private fun fadeOutLoading() {
        // Fade out TextView
        loadingText
            .animate()
            .alpha(0f)
            .setDuration(LOADING_FADE_OUT)
            .withEndAction {
                loadingText.visibility = TextView.GONE
            }.start()
    }

    private fun fadeInNextItem() {
        // LoadingView should always be hidden/gone
        // Remove?
        if (loadingView.visibility == View.GONE) {
            return
        }

        var startDelay: Long = 0
        // If first video (ie. screensaver startup), fade out 'loading...' text
        if (loadingText.visibility == View.VISIBLE) {
            fadeOutLoading()
            startDelay = LOADING_DELAY
        }

        // Fade out LoadingView
        // Video should be playing underneath
        loadingView
            .animate()
            .alpha(0f)
            .setStartDelay(startDelay)
            .setDuration(ITEM_FADE_IN)
            .withEndAction {
                loadingView.visibility = View.GONE
                canSkip = true
            }.start()
    }

    private fun fadeOutCurrentItem() {
        if (!canSkip) return
        canSkip = false

        overlayHelper.findOverlay<TextLocation>().forEach {
            it.isFadingOutMedia = true
        }

        // Fade in LoadView (ie. black screen)
        loadingView
            .animate()
            .alpha(1f)
            .setStartDelay(0)
            .setDuration(ITEM_FADE_OUT)
            .withStartAction {
                loadingView.visibility = View.VISIBLE
            }
            .withEndAction {
                // Hide content views after faded out
                videoView.root.visibility = View.INVISIBLE
                videoView.videoPlayer.stop()

                imageView.root.visibility = View.INVISIBLE
                imageView.imagePlayer.stop()

                // Pick next/previous video
                val media = if (!previousItem) {
                    playlist.nextItem()
                } else {
                    playlist.previousItem()
                }
                previousItem = false

                loadItem(media)
            }.start()
    }

    private fun showLoadingError() {
        loadingText.text = resources.getString(R.string.loading_error)
    }

    fun stop() {
        videoPlayer.release()
        imagePlayer.release()
    }

    fun skipItem(previous: Boolean = false) {
        previousItem = previous
        fadeOutCurrentItem()
    }

    fun increaseSpeed() = videoPlayer.increaseSpeed()

    fun decreaseSpeed() = videoPlayer.decreaseSpeed()

    private fun handleError() {
        if (loadingView.visibility == View.VISIBLE) {
            loadItem(playlist.nextItem())
        } else {
            fadeOutCurrentItem()
        }
    }

    private fun handlePlaybackSpeedChanged() {
        val message = resources.getString(R.string.playlist_playback_speed_changed, GeneralPrefs.playbackSpeed + "x")
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }

    override fun onVideoPlaybackSpeedChanged() = handlePlaybackSpeedChanged()
    override fun onVideoAlmostFinished() = fadeOutCurrentItem()
    override fun onVideoPrepared() = fadeInNextItem()
    override fun onVideoError() = handleError()
    override fun onImageFinished() = fadeOutCurrentItem()
    override fun onImageError() = handleError()
    override fun onImagePrepared() = fadeInNextItem()

    companion object {
        private const val TAG = "VideoController"
        const val LOADING_FADE_OUT: Long = 300
        const val LOADING_DELAY: Long = 400
        const val ITEM_FADE_IN: Long = 800
        const val ITEM_FADE_OUT: Long = 1000
        const val ERROR_DELAY: Long = 2000
    }
}
