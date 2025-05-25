package com.neilturner.aerialviews.ui.core

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.databinding.DataBindingUtil
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.databinding.AerialActivityBinding
import com.neilturner.aerialviews.databinding.ImageViewBinding
import com.neilturner.aerialviews.databinding.OverlayViewBinding
import com.neilturner.aerialviews.databinding.VideoViewBinding
import com.neilturner.aerialviews.models.MediaPlaylist
import com.neilturner.aerialviews.models.enums.AerialMediaType
import com.neilturner.aerialviews.models.enums.ProgressBarLocation
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.models.videos.AerialMedia
import com.neilturner.aerialviews.services.MediaService
import com.neilturner.aerialviews.services.NowPlayingService
import com.neilturner.aerialviews.services.weather.WeatherService
import com.neilturner.aerialviews.ui.core.ImagePlayerView.OnImagePlayerEventListener
import com.neilturner.aerialviews.ui.core.VideoPlayerView.OnVideoPlayerEventListener
import com.neilturner.aerialviews.ui.overlays.LocationOverlay
import com.neilturner.aerialviews.ui.overlays.ProgressBarEvent
import com.neilturner.aerialviews.ui.overlays.ProgressState
import com.neilturner.aerialviews.ui.overlays.WeatherOverlay
import com.neilturner.aerialviews.utils.ColourHelper
import com.neilturner.aerialviews.utils.FontHelper
import com.neilturner.aerialviews.utils.GradientHelper
import com.neilturner.aerialviews.utils.OverlayHelper
import com.neilturner.aerialviews.utils.PermissionHelper
import com.neilturner.aerialviews.utils.RefreshRateHelper
import com.neilturner.aerialviews.utils.WindowHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.kosert.flowbus.GlobalBus
import timber.log.Timber
import kotlin.math.abs

class ScreenController(
    private val context: Context,
) : OnVideoPlayerEventListener,
    OnImagePlayerEventListener {
    private val mainScope = CoroutineScope(Dispatchers.Main)
    private lateinit var playlist: MediaPlaylist
    private lateinit var overlayHelper: OverlayHelper
    private val resources by lazy { context.resources }

    private var nowPlayingService: NowPlayingService? = null
    private var weatherService: WeatherService? = null

    private val shouldAlternateOverlays = GeneralPrefs.alternateTextPosition
    private val autoHideOverlayDelay = GeneralPrefs.overlayAutoHide.toLong()
    private val overlayRevealTimeout = GeneralPrefs.overlayRevealTimeout.toLong()
    private val overlayFadeOut: Long = GeneralPrefs.overlayFadeOutDuration.toLong()
    private val overlayFadeIn: Long = GeneralPrefs.overlayFadeInDuration.toLong()
    private val mediaFadeIn = GeneralPrefs.mediaFadeInDuration.toLong()
    private val mediaFadeOut = GeneralPrefs.mediaFadeOutDuration.toLong()

    private var canShowOverlays = false
    private var alternate = false
    private var previousItem = false
    private var canSkip = false

    private lateinit var videoViewBinding: VideoViewBinding
    private lateinit var imageViewBinding: ImageViewBinding
    private lateinit var overlayViewBinding: OverlayViewBinding
    private lateinit var loadingView: View
    private lateinit var overlayView: View
    private lateinit var loadingText: TextView
    private lateinit var videoPlayer: VideoPlayerView
    private lateinit var imagePlayer: ImagePlayerView
    var view: View

    private lateinit var topLeftIds: List<Int>
    private lateinit var topRightIds: List<Int>
    private lateinit var bottomLeftIds: List<Int>
    private lateinit var bottomRightIds: List<Int>

    var blackOutMode = false
        private set

    init {
        val inflater = LayoutInflater.from(context)
        val binding = DataBindingUtil.inflate(inflater, R.layout.aerial_activity, null, false) as AerialActivityBinding
        view = binding.root // Assign view early

        setupViews(binding)
        setupAppearance(binding)
        setupOverlays(binding)
        loadInitialData()
    }

    private fun setupViews(binding: AerialActivityBinding) {
        val backgroundLoading = ColourHelper.colourFromString(GeneralPrefs.backgroundLoading)
        val backgroundVideos = ColourHelper.colourFromString(GeneralPrefs.backgroundVideos)
        val backgroundPhotos = ColourHelper.colourFromString(GeneralPrefs.backgroundPhotos)

        // Setup binding for all views and controls
        loadingView = binding.loadingView.root
        loadingView.setBackgroundColor(backgroundLoading)
        loadingText = binding.loadingView.loadingText

        overlayViewBinding = binding.overlayView
        overlayView = overlayViewBinding.root

        videoViewBinding = binding.videoView
        videoViewBinding.root.setBackgroundColor(backgroundVideos)
        videoPlayer = videoViewBinding.videoPlayer
        videoPlayer.setOnPlayerListener(this)

        imageViewBinding = binding.imageView
        imageViewBinding.root.setBackgroundColor(backgroundPhotos)
        imagePlayer = imageViewBinding.imagePlayer
        imagePlayer.setOnPlayerListener(this)

        // Setup loading message or hide it
        if (GeneralPrefs.showLoadingText) {
            loadingText.apply {
                textSize = GeneralPrefs.loadingTextSize.toFloat()
                typeface = FontHelper.getTypeface(context, GeneralPrefs.fontTypeface, GeneralPrefs.loadingTextWeight)
            }
        } else {
            loadingText.visibility = View.INVISIBLE
        }
    }

    private fun setupAppearance(binding: AerialActivityBinding) {
        // Setup progress bar
        if (GeneralPrefs.progressBarLocation != ProgressBarLocation.DISABLED) {
            val gravity = if (GeneralPrefs.progressBarLocation == ProgressBarLocation.TOP) Gravity.TOP else Gravity.BOTTOM
            (binding.progressBar.layoutParams as FrameLayout.LayoutParams).gravity = gravity
            val alpha = GeneralPrefs.progressBarOpacity.toFloat() / 100
            binding.progressBar.alpha = alpha
            binding.progressBar.visibility = View.VISIBLE
            Timber.i("Progress bar: $alpha, ${GeneralPrefs.progressBarLocation}")
        }

        // Setup brightness/dimness
        if (GeneralPrefs.videoBrightness != "100") {
            val brightnessView = binding.brightnessView
            brightnessView.setBackgroundColor(Color.BLACK)
            brightnessView.alpha = abs((GeneralPrefs.videoBrightness.toFloat() - 100) / 100)
            brightnessView.visibility = View.VISIBLE
        }

        // Reset animation speed if needed
        if (GeneralPrefs.ignoreAnimationScale) {
            WindowHelper.resetSystemAnimationDuration(context)
        }

        // Setup gradients
        if (GeneralPrefs.showTopGradient) {
            overlayViewBinding.gradientTop.apply {
                background = GradientHelper.smoothBackgroundAlt(GradientDrawable.Orientation.TOP_BOTTOM)
                visibility = View.VISIBLE
            }
        }

        if (GeneralPrefs.showBottomGradient) {
            overlayViewBinding.gradientBottom.apply {
                background = GradientHelper.smoothBackgroundAlt(GradientDrawable.Orientation.BOTTOM_TOP)
                visibility = View.VISIBLE
            }
        }
    }

    private fun setupOverlays(binding: AerialActivityBinding) {
        overlayHelper = OverlayHelper(context, GeneralPrefs)
        val overlayIds = overlayHelper.buildOverlaysAndIds(overlayViewBinding)
        bottomLeftIds = overlayIds.bottomLeftIds
        bottomRightIds = overlayIds.bottomRightIds
        topLeftIds = overlayIds.topLeftIds
        topRightIds = overlayIds.topRightIds
    }

    private fun loadInitialData() {
        mainScope.launch {
            // Launch if we have permission for NowPlayingService
            if (PermissionHelper.hasNotificationListenerPermission(context)) {
                nowPlayingService = NowPlayingService(context)
            }

            // Build playlist and start screensaver
            playlist = MediaService(context).fetchMedia()
            if (playlist.size > 0) {
                Timber.i("Playlist size: ${playlist.size}")
                loadItem(playlist.nextItem())
            } else {
                showLoadingError()
            }

            // Setup weather service
            if (overlayHelper.findOverlay<WeatherOverlay>().isNotEmpty()) {
                weatherService = WeatherService(context).apply {
                    startUpdates()
                }
            }
        }
    }

    private fun loadItem(media: AerialMedia) {
        if (media.uri.toString().contains("smb://")) {
            val pattern = Regex("(smb://)([^:]+):([^@]+)@([\\d.]+)/")
            val replacement = "$1****:****@****/"
            val url = pattern.replace(media.uri.toString(), replacement)
            Timber.i("Loading: ${media.description} - $url (${media.poi})")
        } else {
            Timber.i("Loading: ${media.description} - ${media.uri} (${media.poi})")
        }

        // Set overlay data for current video
        overlayHelper.findOverlay<LocationOverlay>().forEach {
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
        if (media.type == AerialMediaType.VIDEO) {
            videoPlayer.setVideo(media)
            videoViewBinding.root.visibility = View.VISIBLE
            imageViewBinding.root.visibility = View.INVISIBLE
        }

        // Images
        if (media.type == AerialMediaType.IMAGE) {
            imagePlayer.setImage(media)
            imageViewBinding.root.visibility = View.VISIBLE
            videoViewBinding.root.visibility = View.INVISIBLE
        }

        // Best to rest progress bar (if enabled) before media playback
        if (GeneralPrefs.progressBarLocation != ProgressBarLocation.DISABLED) {
            GlobalBus.post(ProgressBarEvent(ProgressState.RESET))
        }

        videoPlayer.start()
    }

    private fun fadeOutLoadingText() {
        loadingText.fadeOut(LOADING_FADE_OUT) {
            loadingText.visibility = TextView.GONE
        }
    }

    private fun fadeInNextItem() {
        canShowOverlays = false
        var startDelay: Long = 0
        val overlayDelay = (autoHideOverlayDelay * 1000) + mediaFadeIn

        // If first video (ie. screensaver startup), fade out 'loading...' text
        if (loadingText.isVisible) {
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

        // Fade out LoadingView using helper
        loadingView.fadeOut(mediaFadeIn, startDelay) {
            loadingView.alpha = 0f
            canSkip = true
        }
    }

    private fun fadeOutCurrentItem() {
        if (!canSkip) return
        canSkip = false

        overlayHelper.findOverlay<LocationOverlay>().forEach {
            it.isFadingOutMedia = true
        }

        // Fade in LoadView (ie. black screen) using helper
        loadingView.fadeIn(
            duration = mediaFadeOut,
            onStart = { loadingView.alpha = 0f },
            onEnd = {
                // Hide content views after faded out
                videoViewBinding.root.visibility = View.INVISIBLE
                videoViewBinding.videoPlayer.stop()

                imageViewBinding.root.visibility = View.INVISIBLE
                imageViewBinding.imagePlayer.stop()

                // Pick next/previous video
                val media = if (!previousItem) {
                    playlist.nextItem()
                } else {
                    playlist.previousItem()
                }
                previousItem = false

                if (!blackOutMode) {
                    loadItem(media)
                }
            }
        )
    }

    private fun showLoadingError() {
        loadingText.text = resources.getString(R.string.loading_error)
    }

    private fun hideOverlays(delay: Long = 0L) {
        overlayView.fadeOut(overlayFadeOut, delay) {
            canShowOverlays = true
        }
    }

    fun showOverlays() {
        // Overlay auto hide pref must be enabled
        if (autoHideOverlayDelay < 0) return

        // If blackout mode is on, exit
        if (blackOutMode) return

        // If media fading in/out
        if (!canSkip) return

        // Are overlays already visible
        if (!canShowOverlays) return
        canShowOverlays = false

        overlayView.fadeIn(overlayFadeIn) {
            hideOverlays(overlayRevealTimeout * 1000)
        }
    }

    fun stop() {
        RefreshRateHelper.restoreOriginalMode(context)
        videoPlayer.release()
        imagePlayer.release()
        nowPlayingService?.stop()
        weatherService?.stop()
    }

    fun skipItem(previous: Boolean = false) {
        previousItem = previous
        fadeOutCurrentItem()
    }

    fun toggleBlackOutMode() {
        if (!this::playlist.isInitialized || playlist.size == 0) {
            return
        }

        if (!blackOutMode) {
            blackOutMode = true
            fadeOutCurrentItem()
        } else {
            blackOutMode = false
            loadItem(playlist.nextItem())
        }
    }

    fun nextTrack() {
        nowPlayingService?.nextTrack()
    }

    fun previousTrack() {
        nowPlayingService?.previousTrack()
    }

    fun increaseSpeed() {
        if (blackOutMode) {
            return
        }
        videoPlayer.increaseSpeed()
    }

    fun decreaseSpeed() {
        if (blackOutMode) {
            return
        }
        videoPlayer.decreaseSpeed()
    }

    fun seekForward() {
        if (blackOutMode) {
            return
        }
        videoPlayer.seekForward()
    }

    fun seekBackward() {
        if (blackOutMode) {
            return
        }
        videoPlayer.seekBackward()
    }

    private fun handleError() {
        if (loadingView.isVisible) {
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

    // Animation helper extension functions
    private fun View.fadeIn(duration: Long, delay: Long = 0, onStart: (() -> Unit)? = null, onEnd: (() -> Unit)? = null) {
        this.animate()
            .alpha(1f)
            .setStartDelay(delay)
            .setDuration(duration)
            .withStartAction {
                this.visibility = View.VISIBLE
                this.alpha = 0f
                onStart?.invoke()
            }
            .withEndAction { onEnd?.invoke() }
            .start()
    }

    private fun View.fadeOut(duration: Long, delay: Long = 0, onStart: (() -> Unit)? = null, onEnd: (() -> Unit)? = null) {
        this.animate()
            .alpha(0f)
            .setStartDelay(delay)
            .setDuration(duration)
            .withStartAction { onStart?.invoke() }
            .withEndAction {
                this.visibility = View.INVISIBLE
                onEnd?.invoke()
            }
            .start()
    }

    companion object {
        const val LOADING_FADE_OUT: Long = 300 // Fade out loading text
        const val LOADING_DELAY: Long = 400 // Delay before fading out loading view
        const val ERROR_DELAY: Long = 500 // Delay before loading next item, after error
    }
}
