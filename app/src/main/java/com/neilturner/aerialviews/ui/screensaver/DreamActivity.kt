package com.neilturner.aerialviews.ui.screensaver

import android.annotation.SuppressLint
import android.content.Context
import android.service.dreams.DreamService
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.lifecycleScope
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.databinding.AerialActivityBinding
import com.neilturner.aerialviews.models.LoadingStatus
import com.neilturner.aerialviews.models.enums.AerialMediaType
import com.neilturner.aerialviews.models.enums.ProgressBarLocation
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.models.videos.AerialMedia
import com.neilturner.aerialviews.ui.core.ExtractedVideoMetadata
import com.neilturner.aerialviews.ui.core.ImagePlayerView
import com.neilturner.aerialviews.ui.core.VideoPlayerView
import com.neilturner.aerialviews.ui.core.ScreenViewModel
import com.neilturner.aerialviews.ui.core.ScreenUiState
import com.neilturner.aerialviews.ui.overlays.state.OverlayUiState
import com.neilturner.aerialviews.utils.DeviceHelper
import com.neilturner.aerialviews.utils.FirebaseHelper
import com.neilturner.aerialviews.utils.GradientHelper
import com.neilturner.aerialviews.utils.InputHelper
import com.neilturner.aerialviews.utils.LocaleHelper
import com.neilturner.aerialviews.utils.OverlayHelper
import com.neilturner.aerialviews.utils.PreferenceHelper
import com.neilturner.aerialviews.utils.RefreshRateHelper
import com.neilturner.aerialviews.utils.WindowHelper.hideSystemUI
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import timber.log.Timber
import kotlin.math.abs

class DreamActivity : DreamService(),
    LifecycleOwner,
    VideoPlayerView.OnVideoPlayerEventListener,
    ImagePlayerView.OnImagePlayerEventListener {
    
    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry

    private val viewModel: ScreenViewModel by inject()
    private lateinit var binding: AerialActivityBinding
    private lateinit var overlayHelper: OverlayHelper

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
    private var isPaused = false
    private var pauseStartTime: Long = 0
    private var currentMedia: AerialMedia? = null
    private var blackOutMode = false

    private lateinit var videoPlayer: VideoPlayerView
    private lateinit var imagePlayer: ImagePlayerView

    private lateinit var topLeftIds: List<Int>
    private lateinit var topRightIds: List<Int>
    private lateinit var bottomLeftIds: List<Int>
    private lateinit var bottomRightIds: List<Int>

    @SuppressLint("AppBundleLocaleChanges")
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        
        // Setup
        isFullscreen = true
        isInteractive = true

        // Hide system UI on phones
        hideSystemUI(window)

        val localeContext = if (GeneralPrefs.localeScreensaver.startsWith("default")) {
            this
        } else {
            LocaleHelper.alternateLocale(this, GeneralPrefs.localeScreensaver)
        }
        
        binding = AerialActivityBinding.inflate(LayoutInflater.from(localeContext))
        setContentView(binding.root)

        // Initialize view components
        videoPlayer = binding.videoView.videoPlayer
        imagePlayer = binding.imageView.imagePlayer
        
        videoPlayer.setListener(this)
        imagePlayer.setListener(this)

        overlayHelper = OverlayHelper(
            context = localeContext,
            binding = binding.overlayView,
            viewModel = viewModel,
        )

        val overlayIds = overlayHelper.buildOverlaysAndIds(binding.overlayView)
        topLeftIds = overlayIds.topLeftIds
        topRightIds = overlayIds.topRightIds
        bottomLeftIds = overlayIds.bottomLeftIds
        bottomRightIds = overlayIds.bottomRightIds

        InputHelper.setupGestureListener(
            context = localeContext,
            view = binding.root,
            handler = viewModel,
            exit = ::altWakeUp,
        )

        GradientHelper.setupGradients(
            context = localeContext,
            binding = binding,
            isAlternate = false,
        )

        // Initial visibility
        binding.loadingView.root.isVisible = true
        binding.videoView.root.isVisible = false
        binding.imageView.root.isVisible = false
        binding.overlayView.root.alpha = 0f

        startUdfCollection()
    }

    override fun onWakeUp() {
        try {
            super.onWakeUp()
        } catch (e: Exception) {
            // Doesn't matter
        }
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        FirebaseHelper.analyticsScreenView("Screensaver", this)
    }

    private fun altWakeUp(exitApp: Boolean) {
        if (exitApp) wakeUp()
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        return try {
            super.dispatchTouchEvent(event)
        } catch (e: SecurityException) {
            // Android bug: DreamService internally reads a restricted settings key
            // on Android 12+. Safe to swallow — touch handling may be degraded
            // but the dream will continue running.
            true
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (InputHelper.handleKeyEvent(event, viewModel, ::altWakeUp)) {
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean =
        try {
            super.dispatchGenericMotionEvent(event)
        } catch (e: SecurityException) {
            // Ignore the restricted setting access error
            false
        }

    override fun onDreamingStopped() {
        super.onDreamingStopped()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        stop()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    }

    private fun startUdfCollection() {
        lifecycleScope.launch {
            viewModel.uiState.collectLatest { state ->
                if (state.currentMedia != currentMedia) {
                    val oldMedia = currentMedia
                    currentMedia = state.currentMedia
                    loadItemIntoPlayer(state.currentMedia, oldMedia)
                }
                
                if (state.isPaused != isPaused) {
                    isPaused = state.isPaused
                    if (isPaused) pauseMedia() else resumeMedia()
                }
                
                if (state.blackOutMode != blackOutMode) {
                    blackOutMode = state.blackOutMode
                    toggleBlackOutModeVisuals(blackOutMode)
                }

                if (state.loadingStatus != null) {
                    binding.loadingView.loadingText.text = when (state.loadingStatus) {
                        LoadingStatus.RESUMING -> getString(R.string.loading_resuming)
                        LoadingStatus.BUILDING -> getString(R.string.loading_building)
                        LoadingStatus.LOADING -> getString(R.string.loading_title)
                    }
                    binding.loadingView.loadingSpinner.isVisible = true
                }
                
                if (state.error != null) {
                    showLoadingError()
                }
                
                alternate = state.alternate
                updateOverlays(state.overlayState)

                // Handle events
                handleEvents(state)
            }
        }
    }

    private fun handleEvents(state: ScreenUiState) {
        if (state.showOverlaysEvent > 0) showOverlays()
        if (state.seekForwardEvent > 0) videoPlayer.seekForward()
        if (state.seekBackwardEvent > 0) videoPlayer.seekBackward()
        if (state.playbackSpeedChangedEvent > 0) videoPlayer.updatePlaybackSpeed()
        if (state.brightnessChangedEvent > 0) {
            binding.brightnessView.alpha = abs(state.brightness - 1.0f)
            binding.brightnessView.isVisible = state.brightness < 1.0f
        }
    }

    private fun loadItemIntoPlayer(media: AerialMedia?, oldMedia: AerialMedia?) {
        if (media == null) return
        pauseStartTime = 0

        overlayHelper.assignOverlaysAndIds(
            binding.overlayView.flowBottomLeft,
            binding.overlayView.flowBottomRight,
            bottomLeftIds,
            bottomRightIds,
            alternate,
        )

        overlayHelper.assignOverlaysAndIds(
            binding.overlayView.flowTopLeft,
            binding.overlayView.flowTopRight,
            topLeftIds,
            topRightIds,
            alternate,
        )

        if (media.type == AerialMediaType.VIDEO) {
            binding.videoView.root.isVisible = true
            binding.imageView.root.isVisible = false
            videoPlayer.load(media)
        } else {
            binding.videoView.root.isVisible = false
            binding.imageView.root.isVisible = true
            imagePlayer.load(media)
        }
    }

    private fun updateOverlays(state: OverlayUiState) {
        overlayHelper.render(state, videoPlayer)
        binding.progressBar.render(state.progress)
    }

    private fun pauseMedia() {
        if (currentMedia?.type == AerialMediaType.VIDEO) videoPlayer.pause() else imagePlayer.pause()
        pauseStartTime = System.currentTimeMillis()
    }

    private fun resumeMedia() {
        if (currentMedia?.type == AerialMediaType.VIDEO) videoPlayer.play() else imagePlayer.play()
        pauseStartTime = 0
    }

    private fun toggleBlackOutModeVisuals(blackOut: Boolean) {
        binding.blackOutView.isVisible = blackOut
        if (blackOut) stop()
    }

    private fun showLoadingError() {
        binding.loadingView.loadingText.text = getString(R.string.loading_error)
        binding.loadingView.loadingSpinner.isVisible = false
    }

    private fun stop() {
        videoPlayer.stop()
        imagePlayer.stop()
    }

    // Player Listeners
    override fun onVideoPrepared() {
        fadeInNextItem()
        videoPlayer.play()
    }

    override fun onVideoAlmostFinished() {
        fadeOutCurrentItem()
    }

    override fun onVideoError() {
        Timber.e("Video error")
        fadeOutCurrentItem()
    }

    override fun onVideoMetadataExtracted(metadata: ExtractedVideoMetadata) {
        viewModel.onVideoMetadataExtracted(metadata)
    }

    override fun onVideoPlaybackSpeedChanged() {
        // Overlays might need to update if they show playback speed
    }

    override fun onImagePrepared() {
        fadeInNextItem()
        viewModel.onImagePrepared()
    }

    override fun onImageFinished() {
        fadeOutCurrentItem()
    }

    override fun onImageError() {
        Timber.e("Image error")
        fadeOutCurrentItem()
    }

    // Animations
    private fun fadeInNextItem() {
        canShowOverlays = false
        var startDelay: Long = 0
        val overlayDelay = (autoHideOverlayDelay * 1000) + mediaFadeIn

        if (binding.loadingView.root.isVisible) {
            fadeOutLoadingText()
            startDelay = LOADING_DELAY
        }

        if (autoHideOverlayDelay >= 0) {
            overlayHelper.getOverlaysToFade().forEach { view ->
                view.animate()?.cancel()
                view.clearAnimation()
            }
        }

        if (autoHideOverlayDelay.toInt() == 0) {
            overlayHelper.getOverlaysToFade().forEach { it.alpha = 0f }
            if (GeneralPrefs.showTopGradient && overlayHelper.hasTopOverlaysToFade() && !overlayHelper.hasTopPersistentOverlays()) {
                binding.overlayView.gradientTop.alpha = 0f
            }
            if (GeneralPrefs.showBottomGradient && overlayHelper.hasBottomOverlaysToFade() && !overlayHelper.hasBottomPersistentOverlays()) {
                binding.overlayView.gradientBottom.alpha = 0f
            }
            canShowOverlays = true
        }

        if (autoHideOverlayDelay > 0) {
            overlayHelper.getOverlaysToFade().forEach { it.alpha = 1f }
            if (GeneralPrefs.showTopGradient && overlayHelper.hasTopOverlaysToFade()) {
                binding.overlayView.gradientTop.alpha = 1f
            }
            if (GeneralPrefs.showBottomGradient && overlayHelper.hasBottomOverlaysToFade()) {
                binding.overlayView.gradientBottom.alpha = 1f
            }
            hideOverlays(overlayDelay)
        }

        binding.loadingView.root
            .animate()
            .alpha(0f)
            .setStartDelay(startDelay)
            .setDuration(mediaFadeIn)
            .withLayer()
            .withEndAction {
                binding.loadingView.root.alpha = 0f
                binding.loadingView.root.isVisible = false
                canSkip = true
                viewModel.canSkip(true)
            }.start()
    }

    private fun fadeOutCurrentItem() {
        if (!canSkip) return
        canSkip = false
        viewModel.canSkip(false)

        if (currentMedia?.type == AerialMediaType.VIDEO) {
            videoPlayer.fadeOutAudio(mediaFadeOut)
        }

        binding.loadingView.root
            .animate()
            .alpha(1f)
            .setDuration(mediaFadeOut)
            .withLayer()
            .withStartAction {
                binding.loadingView.root.isVisible = true
                binding.loadingView.root.alpha = 0f
            }.withEndAction {
                videoPlayer.stop()
                imagePlayer.stop()

                if (!blackOutMode) {
                    viewModel.skipItem(previousItem)
                    previousItem = false
                }
            }.start()
    }

    private fun fadeOutLoadingText() {
        binding.loadingView.loadingContainer
            .animate()
            .alpha(0f)
            .setDuration(LOADING_FADE_OUT)
            .withLayer()
            .withEndAction {
                binding.loadingView.loadingContainer.isVisible = false
            }.start()
    }

    private fun hideOverlays(delay: Long = 0L) {
        val overlaysToFade = overlayHelper.getOverlaysToFade()
        if (overlaysToFade.isEmpty()) {
            canShowOverlays = true
            return
        }

        overlaysToFade.forEachIndexed { index, view ->
            val animator = view.animate()
                .alpha(0f)
                .setStartDelay(delay)
                .setDuration(overlayFadeOut)

            if (index == overlaysToFade.lastIndex) {
                animator.withEndAction { canShowOverlays = true }
            }
            animator.start()
        }

        if (GeneralPrefs.showTopGradient && overlayHelper.hasTopOverlaysToFade() && !overlayHelper.hasTopPersistentOverlays()) {
            binding.overlayView.gradientTop.animate().alpha(0f).setStartDelay(delay).setDuration(overlayFadeOut).start()
        }
        if (GeneralPrefs.showBottomGradient && overlayHelper.hasBottomOverlaysToFade() && !overlayHelper.hasBottomPersistentOverlays()) {
            binding.overlayView.gradientBottom.animate().alpha(0f).setStartDelay(delay).setDuration(overlayFadeOut).start()
        }
    }

    private fun showOverlays() {
        if (!canShowOverlays) return
        canShowOverlays = false

        overlayHelper.getOverlaysToFade().forEach { view ->
            view.animate().cancel()
            view.alpha = 1f
        }
        if (GeneralPrefs.showTopGradient && overlayHelper.hasTopOverlaysToFade()) {
            binding.overlayView.gradientTop.alpha = 1f
        }
        if (GeneralPrefs.showBottomGradient && overlayHelper.hasBottomOverlaysToFade()) {
            binding.overlayView.gradientBottom.alpha = 1f
        }

        hideOverlays(overlayRevealTimeout * 1000L)
    }

    companion object {
        private const val LOADING_DELAY = 1000L
        private const val LOADING_FADE_OUT = 1000L
    }
}
