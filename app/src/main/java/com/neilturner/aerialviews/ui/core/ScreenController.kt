package com.neilturner.aerialviews.ui.core

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.media3.common.MediaMetadata
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.databinding.AerialActivityBinding
import com.neilturner.aerialviews.databinding.ImageViewBinding
import com.neilturner.aerialviews.databinding.OverlayViewBinding
import com.neilturner.aerialviews.databinding.VideoViewBinding
import com.neilturner.aerialviews.models.LoadingStatus
import com.neilturner.aerialviews.models.MediaPlaylist
import com.neilturner.aerialviews.models.enums.AerialMediaType
import com.neilturner.aerialviews.models.enums.DateType
import com.neilturner.aerialviews.models.enums.LocationType
import com.neilturner.aerialviews.models.enums.MetadataType
import com.neilturner.aerialviews.models.enums.OverlayType
import com.neilturner.aerialviews.models.enums.ProgressBarLocation
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.models.videos.AerialMedia
import com.neilturner.aerialviews.services.KtorServer
import com.neilturner.aerialviews.services.MediaService
import com.neilturner.aerialviews.services.MusicPlayer
import com.neilturner.aerialviews.services.NowPlayingService
import com.neilturner.aerialviews.services.weather.WeatherService
import com.neilturner.aerialviews.ui.core.ImagePlayerView.OnImagePlayerEventListener
import com.neilturner.aerialviews.ui.core.VideoPlayerView.OnVideoPlayerEventListener
import com.neilturner.aerialviews.ui.overlays.MessageOverlay
import com.neilturner.aerialviews.ui.overlays.MetadataOverlay
import com.neilturner.aerialviews.ui.overlays.NowPlayingOverlay
import com.neilturner.aerialviews.ui.overlays.ProgressBar
import com.neilturner.aerialviews.ui.overlays.ProgressBarEvent
import com.neilturner.aerialviews.ui.overlays.ProgressState
import com.neilturner.aerialviews.ui.overlays.WeatherForecastOverlay
import com.neilturner.aerialviews.ui.overlays.WeatherNowOverlay
import com.neilturner.aerialviews.ui.overlays.state.MessageOverlayState
import com.neilturner.aerialviews.ui.overlays.state.OverlayEventBridge
import com.neilturner.aerialviews.ui.overlays.state.OverlayStateStore
import com.neilturner.aerialviews.ui.overlays.state.OverlayUiState
import com.neilturner.aerialviews.utils.ColourHelper
import com.neilturner.aerialviews.utils.FontHelper
import com.neilturner.aerialviews.utils.GradientHelper
import com.neilturner.aerialviews.utils.OverlayHelper
import com.neilturner.aerialviews.utils.PermissionHelper
import com.neilturner.aerialviews.utils.RefreshRateHelper
import com.neilturner.aerialviews.utils.SplashCache
import com.neilturner.aerialviews.utils.ToastHelper
import com.neilturner.aerialviews.utils.WindowHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import me.kosert.flowbus.GlobalBus
import timber.log.Timber
import kotlin.math.abs

class ScreenController(
    context: Context,
) : OnVideoPlayerEventListener,
    OnImagePlayerEventListener {
    private val context = context.applicationContext
    private val mainScope = CoroutineScope(Dispatchers.Main)
    private lateinit var playlist: MediaPlaylist
    private var overlayHelper: OverlayHelper
    private val resources by lazy { context.resources }

    private var nowPlayingService: NowPlayingService? = null
    private var weatherService: WeatherService? = null
    private var ktorServer: KtorServer? = null
    private var musicPlayer: MusicPlayer? = null
    private val overlayStateStore = OverlayStateStore()
    private val overlayEventBridge = OverlayEventBridge(overlayStateStore)
    private val metadataResolver = MetadataResolver()

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
    private var sleepTimerJob: Job? = null
    private val metadataJobs = mutableMapOf<OverlayType, Job>()
    private var currentMedia: AerialMedia? = null

    private val videoViewBinding: VideoViewBinding
    private val imageViewBinding: ImageViewBinding
    private val overlayViewBinding: OverlayViewBinding
    private val loadingView: View
    private val overlayView: View
    private var loadingText: TextView
    private var loadingSpinner: View
    private var loadingContainer: View
    private var videoPlayer: VideoPlayerView
    private var imagePlayer: ImagePlayerView
    private val brightnessView: View
    private val gradientTopView: View
    private val gradientBottomView: View
    private val progressBarView: ProgressBar
    val view: View

    private val topLeftIds: List<Int>
    private val topRightIds: List<Int>
    private val bottomLeftIds: List<Int>
    private val bottomRightIds: List<Int>

    var blackOutMode = false
        private set

    init {
        val inflater = LayoutInflater.from(context)
        val binding = AerialActivityBinding.inflate(inflater)

        val backgroundLoading = ColourHelper.colourFromString(GeneralPrefs.backgroundLoading)
        val backgroundVideos = ColourHelper.colourFromString(GeneralPrefs.backgroundVideos)
        val backgroundPhotos = ColourHelper.colourFromString(GeneralPrefs.backgroundPhotos)

        // Setup binding for all views and controls
        view = binding.root
        loadingView = binding.loadingView.root
        loadingView.setBackgroundColor(backgroundLoading)
        loadingText = binding.loadingView.loadingText
        loadingSpinner = binding.loadingView.loadingSpinner
        loadingContainer = binding.loadingView.loadingContainer

        overlayViewBinding = binding.overlayView
        overlayView = overlayViewBinding.root
        gradientTopView = overlayViewBinding.gradientTop
        gradientBottomView = overlayViewBinding.gradientBottom

        val initialVideoRoot = binding.videoView.root
        val videoParent = initialVideoRoot.parent as? ViewGroup
        val videoLayoutRes =
            if (GeneralPrefs.useTextureViewForVideo) {
                R.layout.video_view_texture
            } else {
                R.layout.video_view
            }

        videoViewBinding =
            if (videoParent != null) {
                val index = videoParent.indexOfChild(initialVideoRoot)
                videoParent.removeView(initialVideoRoot)
                // Use applicationContext to prevent activity context leaks in parent views
                val appContextInflater = LayoutInflater.from(context.applicationContext)
                val replacementVideoRoot = appContextInflater.inflate(videoLayoutRes, videoParent, false)
                videoParent.addView(replacementVideoRoot, index)
                VideoViewBinding.bind(replacementVideoRoot)
            } else {
                binding.videoView
            }

        videoViewBinding.root.setBackgroundColor(backgroundVideos)
        videoPlayer = videoViewBinding.videoPlayer
        videoPlayer.setOnPlayerListener(this)

        imageViewBinding = binding.imageView
        imageViewBinding.root.setBackgroundColor(backgroundPhotos)
        imagePlayer = imageViewBinding.imagePlayer
        imagePlayer.setOnPlayerListener(this)

        brightnessView = binding.brightnessView
        progressBarView = binding.progressBar

        // Setup loading message or hide it
        if (GeneralPrefs.showLoadingText) {
            loadingText.apply {
                textSize = GeneralPrefs.loadingTextSize.toFloat()
                typeface = FontHelper.getTypeface(context, GeneralPrefs.fontTypeface, GeneralPrefs.loadingTextWeight)
            }
        } else {
            loadingContainer.visibility = View.INVISIBLE
        }

        // Setup overlays and set initial positions
        overlayHelper = OverlayHelper(context, GeneralPrefs)
        val overlayIds = overlayHelper.buildOverlaysAndIds(overlayViewBinding)
        this.bottomLeftIds = overlayIds.bottomLeftIds
        this.bottomRightIds = overlayIds.bottomRightIds
        this.topLeftIds = overlayIds.topLeftIds
        this.topRightIds = overlayIds.topRightIds
        bindOverlayState()
        overlayEventBridge.start()

        // Setup progress bar
        if (GeneralPrefs.progressBarLocation != ProgressBarLocation.DISABLED) {
            val gravity = if (GeneralPrefs.progressBarLocation == ProgressBarLocation.TOP) Gravity.TOP else Gravity.BOTTOM
            (progressBarView.layoutParams as FrameLayout.LayoutParams).gravity = gravity

            val alpha = GeneralPrefs.progressBarOpacity.toFloat() / 100
            progressBarView.alpha = alpha
            Timber.i("Progress bar: $alpha, ${GeneralPrefs.progressBarLocation}")

            progressBarView.visibility = View.VISIBLE
        }

        // Setup brightness/dimness
        if (GeneralPrefs.videoBrightness != "100") {
            val view = brightnessView
            view.setBackgroundColor(Color.BLACK)
            view.alpha = abs((GeneralPrefs.videoBrightness.toFloat() - 100) / 100)
            view.visibility = View.VISIBLE
        }

        // Reset animation speed if needed
        if (GeneralPrefs.ignoreAnimationScale) {
            WindowHelper.resetSystemAnimationDuration(context)
        }

        // Gradients - set up backgrounds but visibility will be managed with overlays
        if (GeneralPrefs.showTopGradient) {
            gradientTopView.background = GradientHelper.smoothBackgroundAlt(GradientDrawable.Orientation.TOP_BOTTOM)
            gradientTopView.visibility = View.VISIBLE
        }

        if (GeneralPrefs.showBottomGradient) {
            gradientBottomView.background = GradientHelper.smoothBackgroundAlt(GradientDrawable.Orientation.BOTTOM_TOP)
            gradientBottomView.visibility = View.VISIBLE
        }

        // Show a previously-cached photo behind the loading UI instead of a black screen.
        SplashCache.loadBitmap(context)?.let(imagePlayer::showSplashBitmap)

        mainScope.launch {
            // Launch if we have permission
            // Used for a) Skip music tracks b) music info widget
            if (PermissionHelper.hasNotificationListenerPermission(context)) {
                nowPlayingService = NowPlayingService(context)
            }

            if (overlayHelper.findOverlay<MessageOverlay>().isNotEmpty() && GeneralPrefs.messageApiEnabled) {
                ktorServer =
                    KtorServer(context) { messageEvent ->
                        GlobalBus.post(messageEvent)
                    }.apply {
                        start()
                    }
            }

            // Build playlist and start screensaver
            val mediaResult =
                MediaService(context).fetchMedia { status ->
                    mainScope.launch {
                        loadingText.text =
                            when (status) {
                                LoadingStatus.RESUMING -> resources.getString(R.string.loading_resuming)
                                LoadingStatus.BUILDING -> resources.getString(R.string.loading_building)
                                LoadingStatus.LOADING -> resources.getString(R.string.loading_title)
                            }
                        loadingSpinner.visibility = View.VISIBLE
                    }
                }
            playlist = mediaResult.mediaPlaylist
            if (playlist.size > 0) {
                Timber.i("Playlist size: ${playlist.size}")
                loadItem(playlist.nextItem())
                scheduleSleepTimer()
            } else {
                showLoadingError()
            }

            // Setup music service
            setupMusicPlayer(mediaResult.musicPlaylist, mediaResult.musicResumeIndex)

            // Setup weather service
            val hasWeatherNowOverlay = overlayHelper.findOverlay<WeatherNowOverlay>().isNotEmpty()
            val hasForecastOverlay = overlayHelper.findOverlay<WeatherForecastOverlay>().isNotEmpty()
            if (hasWeatherNowOverlay || hasForecastOverlay) {
                weatherService =
                    WeatherService(context).apply {
                        startUpdates(
                            fetchCurrentWeather = hasWeatherNowOverlay,
                            fetchForecast = hasForecastOverlay,
                        )
                    }
            }
        }
        // 1. Load playlist
        // 2. load video, setup location/POI, start playback call
        // 3. playback started callback, fade out loading text, fade out loading view
        // 4. when video is almost finished - or skip - fade in loading view
        // 5. goto 2
    }

    private fun scheduleSleepTimer() {
        sleepTimerJob?.cancel()
        val minutes = GeneralPrefs.sleepTimer.toLongOrNull() ?: 0L
        if (minutes <= 0L) {
            Timber.i("Sleep timer disabled")
            return
        }
        Timber.i("Scheduling sleep timer for $minutes minute(s)")
        sleepTimerJob =
            mainScope.launch {
                delay(minutes * 60_000L)
                if (!blackOutMode) {
                    Timber.i("Sleep timer finished - toggling blackout mode")
                    toggleBlackOutMode()
                }
            }
    }

    private fun setupMusicPlayer(
        musicPlaylist: com.neilturner.aerialviews.models.music.MusicPlaylist?,
        resumeIndex: Int = 0,
    ) {
        val backgroundMusicSelected = GeneralPrefs.playsBackgroundMusic
        videoPlayer.setForcedMute(backgroundMusicSelected)

        if (!backgroundMusicSelected) {
            Timber.i("MusicPlayer: background music not selected, skipping")
            return
        }

        if (musicPlaylist == null || musicPlaylist.size == 0) {
            Timber.i("MusicPlayer: no music playlist available, skipping")
            return
        }

        musicPlayer = MusicPlayer(context, musicPlaylist)
        musicPlayer?.createPlayer()
        if (resumeIndex > 0) {
            musicPlayer?.seekToTrack(resumeIndex)
        }
        musicPlayer?.play()
        Timber.i("MusicPlayer: playing ${musicPlaylist.size} tracks")
    }

    private fun loadItem(media: AerialMedia) {
        // Reset pause state when loading new item
        isPaused = false
        pauseStartTime = 0
        currentMedia = media
        overlayStateStore.resetForNextMedia()

        if (media.uri.toString().contains("smb://")) {
            val pattern = Regex("(smb://)([^:]+):([^@]+)@([\\d.]+)/")
            val replacement = "$1****:****@****/"
            val url = pattern.replace(media.uri.toString(), replacement)
            Timber.i("Loading: ${media.metadata.shortDescription} - $url (${media.metadata.pointsOfInterest})")
        } else {
            Timber.i("Loading: ${media.metadata.shortDescription} - ${media.uri} (${media.metadata.pointsOfInterest})")
        }

        updateMetadataOverlayData(media)

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
        // Fade out container (text + spinner)
        loadingContainer
            .animate()
            .alpha(0f)
            .setDuration(LOADING_FADE_OUT)
            .withLayer()
            .withEndAction {
                loadingContainer.visibility = View.GONE
            }.start()
    }

    private fun fadeInNextItem() {
        canShowOverlays = false
        var startDelay: Long = 0
        val overlayDelay = (autoHideOverlayDelay * 1000) + mediaFadeIn

        // If first video (ie. screensaver startup), fade out 'loading...' text/spinner
        if (loadingContainer.isVisible) {
            fadeOutLoadingText()
            startDelay = LOADING_DELAY
        }

        // Reset any overlay animations
        if (autoHideOverlayDelay >= 0) {
            overlayHelper.getOverlaysToFade().forEach { view ->
                view.animate()?.cancel()
                view.clearAnimation()
            }
        }

        // Hide overlays immediately
        if (autoHideOverlayDelay.toInt() == 0) {
            overlayHelper.getOverlaysToFade().forEach { it.alpha = 0f }
            // Also hide gradients immediately if they have fading overlays
            // AND no persistent overlays
            if (GeneralPrefs.showTopGradient && overlayHelper.hasTopOverlaysToFade() && !overlayHelper.hasTopPersistentOverlays()) {
                gradientTopView.alpha = 0f
            }
            if (GeneralPrefs.showBottomGradient && overlayHelper.hasBottomOverlaysToFade() &&
                !overlayHelper.hasBottomPersistentOverlays()
            ) {
                gradientBottomView.alpha = 0f
            }
            canShowOverlays = true
        }

        // Hide overlays after a delay
        if (autoHideOverlayDelay > 0) {
            overlayHelper.getOverlaysToFade().forEach { it.alpha = 1f }
            // Also show gradients initially if they have fading overlays
            if (GeneralPrefs.showTopGradient && overlayHelper.hasTopOverlaysToFade()) {
                gradientTopView.alpha = 1f
            }
            if (GeneralPrefs.showBottomGradient && overlayHelper.hasBottomOverlaysToFade()) {
                gradientBottomView.alpha = 1f
            }
            hideOverlays(overlayDelay)
        }

        // Fade out LoadingView
        // Video should be playing underneath
        loadingView
            .animate()
            .alpha(0f)
            .setStartDelay(startDelay)
            .setDuration(mediaFadeIn)
            .withLayer()
            .withEndAction {
                loadingView.alpha = 0f
                loadingView.visibility = View.INVISIBLE
                canSkip = true
            }.start()
    }

    private fun fadeOutCurrentItem() {
        if (!canSkip) return
        canSkip = false

        overlayHelper.findOverlay<MetadataOverlay>().forEach {
            it.isFadingOutMedia = true
        }

        if (currentMedia?.type == AerialMediaType.VIDEO) {
            videoPlayer.fadeOutAudio(mediaFadeOut)
        }

        // Fade in LoadView (ie. black screen)
        loadingView
            .animate()
            .alpha(1f)
            .setDuration(mediaFadeOut)
            .withLayer()
            .withStartAction {
                loadingView.visibility = View.VISIBLE
                loadingView.alpha = 0f
            }.withEndAction {
                // Hide content views after faded out
                videoViewBinding.root.visibility = View.INVISIBLE
                videoViewBinding.videoPlayer.stop()

                imageViewBinding.root.visibility = View.INVISIBLE
                imageViewBinding.imagePlayer.stop()

                // Reset pause state when transitioning between items
                isPaused = false
                pauseStartTime = 0

                if (!blackOutMode) {
                    mainScope.launch {
                        val media =
                            if (!previousItem) {
                                playlist.nextItem()
                            } else {
                                playlist.previousItem()
                            }
                        previousItem = false
                        loadItem(media)
                    }
                } else {
                    previousItem = false
                }
            }.start()
    }

    private fun showLoadingError() {
        loadingText.text = resources.getString(R.string.loading_error)
        loadingSpinner.visibility = View.GONE
    }

    private fun hideOverlays(delay: Long = 0L) {
        val overlaysToFade = overlayHelper.getOverlaysToFade()

        if (overlaysToFade.isEmpty()) {
            canShowOverlays = true
            return
        }

        overlaysToFade.forEachIndexed { index, view ->
            val animator =
                view
                    .animate()
                    .alpha(0f)
                    .setStartDelay(delay)
                    .setDuration(overlayFadeOut)

            // Only set the end action on the last overlay
            if (index == overlaysToFade.lastIndex) {
                animator.withEndAction { canShowOverlays = true }
            }
            animator.start()
        }

        // Fade out gradients if their corresponding region has fading overlays
        // AND no persistent overlays (otherwise gradient should stay visible)
        if (GeneralPrefs.showTopGradient && overlayHelper.hasTopOverlaysToFade() && !overlayHelper.hasTopPersistentOverlays()) {
            gradientTopView
                .animate()
                .alpha(0f)
                .setStartDelay(delay)
                .setDuration(overlayFadeOut)
                .start()
        }
        if (GeneralPrefs.showBottomGradient && overlayHelper.hasBottomOverlaysToFade() && !overlayHelper.hasBottomPersistentOverlays()) {
            gradientBottomView
                .animate()
                .alpha(0f)
                .setStartDelay(delay)
                .setDuration(overlayFadeOut)
                .start()
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

        val overlaysToFade = overlayHelper.getOverlaysToFade()

        if (overlaysToFade.isEmpty()) return

        canShowOverlays = false

        overlaysToFade.forEachIndexed { index, view ->
            val animator =
                view
                    .animate()
                    .alpha(1f)
                    .setStartDelay(0)
                    .setDuration(overlayFadeIn)

            // Only set the end action on the last overlay
            if (index == overlaysToFade.lastIndex) {
                animator.withEndAction { hideOverlays(overlayRevealTimeout * 1000) }
            }
            animator.start()
        }

        // Fade in gradients if their corresponding region has fading overlays
        if (GeneralPrefs.showTopGradient && overlayHelper.hasTopOverlaysToFade()) {
            gradientTopView
                .animate()
                .alpha(1f)
                .setStartDelay(0)
                .setDuration(overlayFadeIn)
                .start()
        }
        if (GeneralPrefs.showBottomGradient && overlayHelper.hasBottomOverlaysToFade()) {
            gradientBottomView
                .animate()
                .alpha(1f)
                .setStartDelay(0)
                .setDuration(overlayFadeIn)
                .start()
        }
    }

    fun stop() {
        if (this::playlist.isInitialized) {
            CoroutineScope(Dispatchers.IO).launch {
                val cacheRepository =
                    com.neilturner.aerialviews.data
                        .PlaylistCacheRepository(context)
                cacheRepository.saveMediaPosition(playlist.currentPosition)
                musicPlayer?.let {
                    cacheRepository.saveMusicTrackIndex(it.getCurrentTrackIndex())
                }
            }
        }
        RefreshRateHelper.restoreOriginalMode(context)
        overlayEventBridge.stop()
        // Remove video view from parent to break context reference chain
        val videoParent = videoViewBinding.root.parent as? ViewGroup
        videoParent?.removeView(videoViewBinding.root)
        videoPlayer.release()
        imagePlayer.release()
        ktorServer?.stop()
        nowPlayingService?.stop()
        weatherService?.stop()
        musicPlayer?.pause()
        musicPlayer?.release()
        sleepTimerJob?.cancel()
        metadataJobs.values.forEach { it.cancel() }
        metadataJobs.clear()
        mainScope.cancel()
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
            // Cancel any pending sleep timer as we've already entered blackout
            sleepTimerJob?.cancel()
            fadeOutCurrentItem()
        } else {
            blackOutMode = false
            mainScope.launch {
                loadItem(playlist.nextItem())
            }
            // Restart sleep timer if preference still enabled
            scheduleSleepTimer()
        }
    }

    fun nextTrack() {
        val music = musicPlayer
        if (music != null && music.hasMusic()) {
            music.nextTrack()
        } else {
            nowPlayingService?.nextTrack()
        }
    }

    fun previousTrack() {
        val music = musicPlayer
        if (music != null && music.hasMusic()) {
            music.previousTrack()
        } else {
            nowPlayingService?.previousTrack()
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

    fun togglePause() {
        if (isPaused) {
            resumeMedia()
        } else {
            pauseMedia()
        }
    }

    fun toggleLooping() {
        if (videoViewBinding.root.isVisible) {
            videoPlayer.toggleLooping()
        }
    }

    fun increaseBrightness() = changeBrightness(true)

    fun decreaseBrightness() = changeBrightness(false)

    private fun changeBrightness(increase: Boolean) {
        if (blackOutMode) return

        val brightnessValues = resources.getStringArray(R.array.percentage1_values)
        val currentBrightness = GeneralPrefs.videoBrightness
        val currentIndex = brightnessValues.indexOf(currentBrightness)

        if (currentIndex == -1) return

        if (increase && currentIndex == brightnessValues.size - 1) return
        if (!increase && currentIndex == 0) return

        val newIndex = if (increase) currentIndex + 1 else currentIndex - 1
        val newBrightness = brightnessValues[newIndex]

        GeneralPrefs.videoBrightness = newBrightness

        // Update view
        val view = brightnessView
        if (newBrightness == "100") {
            view.visibility = View.GONE
        } else {
            view.setBackgroundColor(Color.BLACK)
            view.alpha = abs((newBrightness.toFloat() - 100) / 100)
            view.visibility = View.VISIBLE
        }

        // Show toast
        mainScope.launch {
            ToastHelper.show(context, "Brightness: $newBrightness%")
        }
    }

    fun toggleMute() {
        videoPlayer.toggleMute()
    }

    private fun pauseMedia() {
        if (isPaused) return

        isPaused = true
        pauseStartTime = System.currentTimeMillis()

        // Pause video if currently showing
        if (videoViewBinding.root.isVisible) {
            videoPlayer.pause()
        }

        // Pause image timer if currently showing
        if (imageViewBinding.root.isVisible) {
            imagePlayer.pauseTimer()
        }

        // Pause progress bar
        if (GeneralPrefs.progressBarLocation != ProgressBarLocation.DISABLED) {
            GlobalBus.post(ProgressBarEvent(ProgressState.PAUSE))
        }
    }

    private fun resumeMedia() {
        if (!isPaused) return

        isPaused = false
        val pauseDuration = System.currentTimeMillis() - pauseStartTime

        // Resume video if currently showing
        if (videoViewBinding.root.isVisible) {
            videoPlayer.resume()
        }

        // Resume image timer if currently showing
        if (imageViewBinding.root.isVisible) {
            imagePlayer.resumeTimer(pauseDuration)
        }

        // Resume progress bar
        if (GeneralPrefs.progressBarLocation != ProgressBarLocation.DISABLED) {
            GlobalBus.post(ProgressBarEvent(ProgressState.RESUME))
        }
    }

    private fun handleError() {
        mainScope.launch {
            delay(ERROR_DELAY)
            if (loadingView.isVisible) {
                loadItem(playlist.nextItem())
            } else {
                fadeOutCurrentItem()
            }
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

    override fun onVideoMetadataExtracted(mediaMetadata: MediaMetadata) {
        val media = currentMedia ?: return
        Timber.i("Video metadata: %s", formatVideoMetadataForLog(mediaMetadata))
        val changed = applyVideoMetadataToMedia(media, mediaMetadata)

        if (changed) {
            updateMetadataOverlayData(media)
        }
    }

    override fun onImageFinished() = fadeOutCurrentItem()

    override fun onImageError() = handleError()

    private fun updateMetadataOverlayData(media: AerialMedia) {
        val metadataSlots =
            listOf(
                OverlayType.METADATA1,
                OverlayType.METADATA2,
                OverlayType.METADATA3,
                OverlayType.METADATA4,
            )

        metadataSlots.forEach { slot ->
            metadataJobs[slot]?.cancel()
            metadataJobs[slot] =
                mainScope.launch {
                    try {
                        val preferences = getMetadataPreferences(slot)
                        val resolved = metadataResolver.resolve(context, media, preferences)
                        if (currentMedia !== media) return@launch

                        overlayStateStore.setMetadata(
                            slot,
                            resolved.text,
                            resolved.poi,
                            resolved.metadataType,
                        )
                    } catch (e: Exception) {
                        Timber.e(e, "Metadata slot $slot resolver failed")
                        if (currentMedia === media) {
                            overlayStateStore.setMetadata(slot, "", emptyMap(), MetadataType.STATIC)
                        }
                    }
                }
        }
    }

    private fun getMetadataPreferences(slot: OverlayType): MetadataResolver.Preferences =
        when (slot) {
            OverlayType.METADATA1 -> {
                MetadataResolver.Preferences(
                    videoSelection = GeneralPrefs.overlayMetadata1Videos,
                    videoFolderDepth = GeneralPrefs.overlayMetadata1VideosFolderLevel.toIntOrNull() ?: 1,
                    videoLocationType =
                        GeneralPrefs.overlayMetadata1VideosLocationType ?: LocationType.CITY_COUNTRY,
                    photoSelection = GeneralPrefs.overlayMetadata1Photos,
                    photoFolderDepth = GeneralPrefs.overlayMetadata1PhotosFolderLevel.toIntOrNull() ?: 1,
                    photoLocationType =
                        GeneralPrefs.overlayMetadata1PhotosLocationType ?: LocationType.CITY_COUNTRY,
                    photoDateType =
                        GeneralPrefs.overlayMetadata1PhotosDateType ?: DateType.COMPACT,
                    photoDateCustom = GeneralPrefs.overlayMetadata1PhotosDateCustom,
                )
            }

            OverlayType.METADATA2 -> {
                MetadataResolver.Preferences(
                    videoSelection = GeneralPrefs.overlayMetadata2Videos,
                    videoFolderDepth = GeneralPrefs.overlayMetadata2VideosFolderLevel.toIntOrNull() ?: 1,
                    videoLocationType =
                        GeneralPrefs.overlayMetadata2VideosLocationType ?: LocationType.CITY_COUNTRY,
                    photoSelection = GeneralPrefs.overlayMetadata2Photos,
                    photoFolderDepth = GeneralPrefs.overlayMetadata2PhotosFolderLevel.toIntOrNull() ?: 1,
                    photoLocationType =
                        GeneralPrefs.overlayMetadata2PhotosLocationType ?: LocationType.CITY_COUNTRY,
                    photoDateType =
                        GeneralPrefs.overlayMetadata2PhotosDateType ?: DateType.COMPACT,
                    photoDateCustom = GeneralPrefs.overlayMetadata2PhotosDateCustom,
                )
            }

            OverlayType.METADATA3 -> {
                MetadataResolver.Preferences(
                    videoSelection = GeneralPrefs.overlayMetadata3Videos,
                    videoFolderDepth = GeneralPrefs.overlayMetadata3VideosFolderLevel.toIntOrNull() ?: 1,
                    videoLocationType =
                        GeneralPrefs.overlayMetadata3VideosLocationType ?: LocationType.CITY_COUNTRY,
                    photoSelection = GeneralPrefs.overlayMetadata3Photos,
                    photoFolderDepth = GeneralPrefs.overlayMetadata3PhotosFolderLevel.toIntOrNull() ?: 1,
                    photoLocationType =
                        GeneralPrefs.overlayMetadata3PhotosLocationType ?: LocationType.CITY_COUNTRY,
                    photoDateType =
                        GeneralPrefs.overlayMetadata3PhotosDateType ?: DateType.COMPACT,
                    photoDateCustom = GeneralPrefs.overlayMetadata3PhotosDateCustom,
                )
            }

            else -> {
                MetadataResolver.Preferences(
                    videoSelection = GeneralPrefs.overlayMetadata4Videos,
                    videoFolderDepth = GeneralPrefs.overlayMetadata4VideosFolderLevel.toIntOrNull() ?: 1,
                    videoLocationType =
                        GeneralPrefs.overlayMetadata4VideosLocationType ?: LocationType.CITY_COUNTRY,
                    photoSelection = GeneralPrefs.overlayMetadata4Photos,
                    photoFolderDepth = GeneralPrefs.overlayMetadata4PhotosFolderLevel.toIntOrNull() ?: 1,
                    photoLocationType =
                        GeneralPrefs.overlayMetadata4PhotosLocationType ?: LocationType.CITY_COUNTRY,
                    photoDateType =
                        GeneralPrefs.overlayMetadata4PhotosDateType ?: DateType.COMPACT,
                    photoDateCustom = GeneralPrefs.overlayMetadata4PhotosDateCustom,
                )
            }
        }

    override fun onImagePrepared() {
        Timber.d("onImagePrepared")
        currentMedia
            ?.takeIf { it.type == AerialMediaType.IMAGE }
            ?.let { updateMetadataOverlayData(it) }
        fadeInNextItem()
    }

    private fun bindOverlayState() {
        mainScope.launch {
            overlayStateStore.uiState.collectLatest { state ->
                renderOverlayState(state)
            }
        }
    }

    private fun renderOverlayState(state: OverlayUiState) {
        overlayHelper.findOverlay<MetadataOverlay>().forEach { overlay ->
            val locationState = state.metadata[overlay.type]
            if (locationState != null) {
                overlay.render(locationState, videoPlayer)
            }
        }

        overlayHelper.findOverlay<NowPlayingOverlay>().forEach {
            it.render(state.nowPlaying)
        }

        overlayHelper.findOverlay<WeatherNowOverlay>().forEach {
            it.render(state.weather)
        }

        overlayHelper.findOverlay<WeatherForecastOverlay>().forEach {
            it.render(state.forecast)
        }

        overlayHelper.findOverlay<MessageOverlay>().forEach { overlay ->
            overlay.render(state.message[overlay.type] ?: MessageOverlayState())
        }

        progressBarView.render(state.progress)
    }

    companion object {
        const val LOADING_FADE_OUT: Long = 300 // Fade out loading text
        const val LOADING_DELAY: Long = 400 // Delay before fading out loading view
        const val ERROR_DELAY: Long = 2000 // Delay before loading next item, after error
    }
}
