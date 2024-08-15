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
import com.neilturner.aerialviews.services.NowPlayingService
import com.neilturner.aerialviews.ui.core.ImagePlayerView.OnImagePlayerEventListener
import com.neilturner.aerialviews.ui.core.VideoPlayerView.OnVideoPlayerEventListener
import com.neilturner.aerialviews.ui.overlays.TextLocation
import com.neilturner.aerialviews.ui.overlays.TextNowPlaying
import com.neilturner.aerialviews.utils.FileHelper
import com.neilturner.aerialviews.utils.FontHelper
import com.neilturner.aerialviews.utils.OverlayHelper
import com.neilturner.aerialviews.utils.PermissionHelper
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

    private var nowPlayingService: NowPlayingService? = null
    private val shouldAlternateOverlays = GeneralPrefs.alternateTextPosition
    private val autoHideOverlayDelay = GeneralPrefs.overlayAutoHide.toLong()
    private var canShowOverlays = false
    private var alternate = false
    private var previousItem = false
    private var canSkip = false

    private val videoViewBinding: VideoViewBinding
    private val imageViewBinding: ImageViewBinding
    private val overlayViewBinding: OverlayViewBinding
    private val loadingView: View
    private val overlayView: View
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

        overlayViewBinding = binding.overlayView
        overlayView = overlayViewBinding.root

        videoViewBinding = binding.videoView
        videoPlayer = videoViewBinding.videoPlayer
        videoPlayer.setOnPlayerListener(this)

        imageViewBinding = binding.imageView
        imagePlayer = imageViewBinding.imagePlayer
        imagePlayer.setOnPlayerListener(this)
        imageViewBinding.root.setBackgroundColor(Color.BLACK)

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
        val overlayIds = overlayHelper.buildOverlaysAndIds(overlayViewBinding)
        this.bottomLeftIds = overlayIds.bottomLeftIds
        this.bottomRightIds = overlayIds.bottomRightIds
        this.topLeftIds = overlayIds.topLeftIds
        this.topRightIds = overlayIds.topRightIds

        // Gradients
        if (GeneralPrefs.showTopGradient) {
            overlayViewBinding.gradientTop.visibility = View.VISIBLE
        }

        if (GeneralPrefs.showBottomGradient) {
            overlayViewBinding.gradientBottom.visibility = View.VISIBLE
        }

        coroutineScope.launch {
            if (overlayHelper.isOverlayEnabled<TextNowPlaying>() &&
                PermissionHelper.hasNotificationListenerPermission(context)
            ) {
                nowPlayingService = NowPlayingService(context, GeneralPrefs)
            }

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
            Log.i(TAG, "Playing: ${media.description} - $url (${media.poi})")
        } else {
            Log.i(TAG, "Playing: ${media.description} - ${media.uri} (${media.poi})")
        }

        // Set overlay data for current video
        overlayHelper.findOverlay<TextLocation>().forEach {
            val locationType = GeneralPrefs.descriptionVideoManifestStyle
            if (locationType != null) {
                it.updateLocationData(media.description, media.poi, locationType, videoPlayer)
            }
        }

        // Set overlay positions
        overlayHelper.assignOverlaysAndIds(
            overlayViewBinding.flowBottomLeft,
            overlayViewBinding.flowBottomRight,
            bottomLeftIds,
            bottomRightIds,
            alternate,
        )

        overlayHelper.assignOverlaysAndIds(
            overlayViewBinding.flowTopLeft,
            overlayViewBinding.flowTopRight,
            topLeftIds,
            topRightIds,
            alternate,
        )

        if (shouldAlternateOverlays) {
            alternate = !alternate
        }

        // Videos
        if (FileHelper.isSupportedVideoType(media.uri.filename)) {
            videoPlayer.setVideo(media)
            videoViewBinding.root.visibility = View.VISIBLE
            imageViewBinding.root.visibility = View.INVISIBLE
        }

        // Images
        if (FileHelper.isSupportedImageType(media.uri.filename)) {
            imagePlayer.setImage(media)
            imageViewBinding.root.visibility = View.VISIBLE
            videoViewBinding.root.visibility = View.INVISIBLE
        }

        videoPlayer.start()
    }

    private fun fadeOutLoadingText() {
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
        canShowOverlays = false
        var startDelay: Long = 0
        val overlayDelay = (autoHideOverlayDelay * 1000) + ITEM_FADE_IN

        // If first video (ie. screensaver startup), fade out 'loading...' text
        if (loadingText.visibility == View.VISIBLE) {
            fadeOutLoadingText()
            startDelay = LOADING_DELAY
        }

        // Reset any overlay animations
        if (autoHideOverlayDelay >= 0) {
            overlayView.animate()?.cancel()
            overlayView.clearAnimation()
        }

        // Hide overlays immediately
        if (autoHideOverlayDelay.toInt() == 0) {
            overlayView.alpha = 0f
            canShowOverlays = true
        }

        // Hide overlays after a delay
        if (autoHideOverlayDelay > 0) {
            overlayView.alpha = 1f
            hideOverlays(overlayDelay)
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
                videoViewBinding.root.visibility = View.INVISIBLE
                videoViewBinding.videoPlayer.stop()

                imageViewBinding.root.visibility = View.INVISIBLE
                imageViewBinding.imagePlayer.stop()

                // Pick next/previous video
                val media =
                    if (!previousItem) {
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

    private fun hideOverlays(delay: Long = 0L) {
        overlayView
            .animate()
            .alpha(0f)
            .setStartDelay(delay)
            .setDuration(OVERLAY_FADE_OUT)
            .withEndAction {
                canShowOverlays = true
            }.start()
    }

    fun showOverlays() {
        // If media fading in/out
        if (!canSkip) return

        // Are overlays already visible
        if (!canShowOverlays) return
        canShowOverlays = false

        Log.i(TAG, "Show overlays")

        overlayView
            .animate()
            .alpha(1f)
            .setStartDelay(0)
            .setDuration(OVERLAY_FADE_IN)
            .withEndAction {
                hideOverlays(4 * 1000)
            }.start()
    }

    fun stop() {
        videoPlayer.release()
        imagePlayer.release()
        nowPlayingService?.stop()
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
        private const val TAG = "ScreenController"
        const val LOADING_FADE_OUT: Long = 300 // Fade out loading text
        const val LOADING_DELAY: Long = 400 // Delay before fading out loading view
        const val ERROR_DELAY: Long = 2000 // Delay before loading next item
        const val OVERLAY_FADE_OUT: Long = 500 // Fade out overlays
        const val OVERLAY_FADE_IN: Long = 500 // Fade in overlays
        val ITEM_FADE_IN = GeneralPrefs.fadeInDuration.toLong() // Fade out loading view
        val ITEM_FADE_OUT = GeneralPrefs.fadeOutDuration.toLong() // Fade in loading view
    }
}
