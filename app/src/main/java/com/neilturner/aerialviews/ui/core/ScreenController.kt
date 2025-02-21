package com.neilturner.aerialviews.ui.core

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
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
import com.neilturner.aerialviews.services.NowPlayingServiceAlt
import com.neilturner.aerialviews.ui.core.ImagePlayerView.OnImagePlayerEventListener
import com.neilturner.aerialviews.ui.core.VideoPlayerView.OnVideoPlayerEventListener
import com.neilturner.aerialviews.ui.overlays.ProgressBarEvent
import com.neilturner.aerialviews.ui.overlays.ProgressState
import com.neilturner.aerialviews.ui.overlays.TextLocation
import com.neilturner.aerialviews.ui.overlays.TextNowPlaying
import com.neilturner.aerialviews.utils.FontHelper
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
    private var overlayHelper: OverlayHelper
    private val resources = context.resources!!

    private var nowPlayingService: NowPlayingServiceAlt? = null
    private val shouldAlternateOverlays = GeneralPrefs.alternateTextPosition
    private val autoHideOverlayDelay = GeneralPrefs.overlayAutoHide.toLong()
    private val overlayRevealTimeout = GeneralPrefs.overlayRevealTimeout.toLong()
    private val overlayFadeOut: Long = GeneralPrefs.overlayFadeOutDuration.toLong()
    private val overlayFadeIn: Long = GeneralPrefs.overlayFadeInDuration.toLong()
    private val mediaFadeIn = GeneralPrefs.mediaFadeInDuration.toLong()
    private val mediaFadeOut = GeneralPrefs.mediaFadeOutDuration.toLong()

    private val loadingViewAlphaVisible = abs((GeneralPrefs.videoBrightness.toFloat() - 100) / 100)

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

    var blackOutMode = false
        private set

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
        videoViewBinding.root.setBackgroundColor(Color.BLACK)

        imageViewBinding = binding.imageView
        imagePlayer = imageViewBinding.imagePlayer
        imagePlayer.setOnPlayerListener(this)
        imageViewBinding.root.setBackgroundColor(Color.BLACK)

        if (GeneralPrefs.progressBarLocation != ProgressBarLocation.DISABLED) {
            val gravity = if (GeneralPrefs.progressBarLocation == ProgressBarLocation.TOP) Gravity.TOP else Gravity.BOTTOM
            (binding.progressBar.layoutParams as FrameLayout.LayoutParams).gravity = gravity

            val alpha = GeneralPrefs.progressBarOpacity.toFloat() / 100
            binding.progressBar.alpha = alpha
            Timber.i("Progress bar: $alpha, ${GeneralPrefs.progressBarLocation}")

            binding.progressBar.visibility = View.VISIBLE
        }

        if (GeneralPrefs.showLoadingText) {
            loadingText.apply {
                textSize = GeneralPrefs.loadingTextSize.toFloat()
                typeface = FontHelper.getTypeface(context, GeneralPrefs.fontTypeface, GeneralPrefs.loadingTextWeight)
            }
        } else {
            loadingText.visibility = View.INVISIBLE
        }

        if (GeneralPrefs.ignoreAnimationScale) {
            WindowHelper.resetSystemAnimationDuration(context)
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

        // Get duration scale from the global settings.
        var durationScale =
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                0f,
            )

        // If global duration scale is not 1 (default), try to override it
        // for the current application.
        if (durationScale != 1f) {
            try {
                ValueAnimator::class.java.getMethod("setDurationScale", Float::class.java).invoke(null, 1f)
                durationScale = 1f
            } catch (t: Throwable) {
                // It means something bad happened, and animations are still
                // altered by the global settings. You should warn the user and
                // exit application.
            }
        }

        Timber.i("Duration scale: $durationScale")

        mainScope.launch {
            if (overlayHelper.isOverlayEnabled<TextNowPlaying>() &&
                PermissionHelper.hasNotificationListenerPermission(context)
            ) {
                nowPlayingService = NowPlayingServiceAlt(context)
            }

            playlist = MediaService(context).fetchMedia()

            if (playlist.size > 0) {
                Timber.i("Playlist size: ${playlist.size}")
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
            Timber.i("Loading: ${media.description} - $url (${media.poi})")
        } else {
            Timber.i("Loading: ${media.description} - ${media.uri} (${media.poi})")
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
        val overlayDelay = (autoHideOverlayDelay * 1000) + mediaFadeIn

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
            .alpha(loadingViewAlphaVisible)
            .setStartDelay(startDelay)
            .setDuration(mediaFadeIn)
            .withEndAction {
                // loadingView.visibility = View.GONE
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
            .setDuration(mediaFadeOut)
            .withStartAction {
                loadingView.visibility = View.VISIBLE
            }.withEndAction {
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

                if (!blackOutMode) {
                    loadItem(media)
                }
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
            .setDuration(overlayFadeOut)
            .withEndAction {
                canShowOverlays = true
            }.start()
    }

    fun showOverlays() {
        // Overlay auto hide pref must be enabled
        if (autoHideOverlayDelay < 0) return

        // If media fading in/out
        if (!canSkip) return

        // Are overlays already visible
        if (!canShowOverlays) return
        canShowOverlays = false

        overlayView
            .animate()
            .alpha(1f)
            .setStartDelay(0)
            .setDuration(overlayFadeIn)
            .withEndAction {
                hideOverlays(overlayRevealTimeout * 1000)
            }.start()
    }

    fun stop() {
        RefreshRateHelper.restoreOriginalMode(context)
        videoPlayer.release()
        imagePlayer.release()
        nowPlayingService?.stop()
    }

    fun skipItem(previous: Boolean = false) {
        previousItem = previous
        fadeOutCurrentItem()
    }

    fun toggleBlackOutMode() {
        if (playlist.size == 0) {
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
        const val LOADING_FADE_OUT: Long = 300 // Fade out loading text
        const val LOADING_DELAY: Long = 400 // Delay before fading out loading view
        const val ERROR_DELAY: Long = 2000 // Delay before loading next item, after error
    }
}
