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
import androidx.core.view.isVisible
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.data.PlaylistCacheRepository
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
import com.neilturner.aerialviews.models.music.MusicPlaylist
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.models.prefs.SonosPrefs
import com.neilturner.aerialviews.models.videos.AerialMedia
import com.neilturner.aerialviews.services.KtorServer
import com.neilturner.aerialviews.services.MediaService
import com.neilturner.aerialviews.services.MusicPlayer
import com.neilturner.aerialviews.services.NowPlayingService
import com.neilturner.aerialviews.services.sonos.SonosService
import com.neilturner.aerialviews.services.weather.WeatherService
import com.neilturner.aerialviews.ui.controls.ProgressBar
import com.neilturner.aerialviews.ui.controls.ProgressBarEvent
import com.neilturner.aerialviews.ui.controls.ProgressState
import com.neilturner.aerialviews.ui.core.ImagePlayerView.OnImagePlayerEventListener
import com.neilturner.aerialviews.ui.core.VideoPlayerView.OnVideoPlayerEventListener
import com.neilturner.aerialviews.ui.helpers.ColourHelper
import com.neilturner.aerialviews.ui.helpers.FontHelper
import com.neilturner.aerialviews.ui.helpers.GradientHelper
import com.neilturner.aerialviews.ui.helpers.NotificationHelper
import com.neilturner.aerialviews.ui.helpers.OverlayHelper
import com.neilturner.aerialviews.ui.helpers.OverlayOpacityHelper
import com.neilturner.aerialviews.ui.helpers.PermissionHelper
import com.neilturner.aerialviews.ui.helpers.RefreshRateHelper
import com.neilturner.aerialviews.ui.helpers.WindowHelper
import com.neilturner.aerialviews.ui.overlays.MessageOverlay
import com.neilturner.aerialviews.ui.overlays.MetadataOverlay
import com.neilturner.aerialviews.ui.overlays.NowPlayingOverlay
import com.neilturner.aerialviews.ui.overlays.WeatherForecastOverlay
import com.neilturner.aerialviews.ui.overlays.WeatherNowOverlay
import com.neilturner.aerialviews.ui.overlays.state.MessageOverlayState
import com.neilturner.aerialviews.ui.overlays.state.OverlayEventBridge
import com.neilturner.aerialviews.ui.overlays.state.OverlayStateStore
import com.neilturner.aerialviews.ui.overlays.state.OverlayUiState
import com.neilturner.aerialviews.utils.DreamTiming
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import me.kosert.flowbus.GlobalBus
import timber.log.Timber
import kotlin.math.abs
import kotlin.time.Duration.Companion.milliseconds

class ScreenController(
    val context: Context,
) : OnVideoPlayerEventListener,
    OnImagePlayerEventListener {
    private val mainScope = CoroutineScope(Dispatchers.Main)
    private lateinit var playlist: MediaPlaylist
    private var overlayHelper: OverlayHelper
    private val resources by lazy { context.resources }
    private var isStopped = false

    private var nowPlayingService: NowPlayingService? = null
    private var sonosService: SonosService? = null
    private var weatherService: WeatherService? = null
    private var musicPlayer: MusicPlayer? = null
    private val overlayStateStore = OverlayStateStore()
    private val overlayEventBridge = OverlayEventBridge(overlayStateStore)
    private val metadataResolver = MetadataResolver()

    private val shouldAlternateOverlays = GeneralPrefs.alternateTextPosition
    private val overlayVisibilityMode = GeneralPrefs.overlayVisibility
    private val overlayVisibilityDelay = GeneralPrefs.overlayVisibilityDelay.toLong()
    private val overlayRevealTimeout = GeneralPrefs.overlayRevealTimeout.toLong()
    private val overlayFadeOut: Long = GeneralPrefs.overlayFadeOutDuration.toLong()
    private val overlayFadeIn: Long = GeneralPrefs.overlayFadeInDuration.toLong()
    private val mediaFadeIn = GeneralPrefs.mediaFadeInDuration.toLong()
    private val mediaFadeOut = GeneralPrefs.mediaFadeOutDuration.toLong()
    private val overlayVisibleAlpha = OverlayOpacityHelper.visibleAlpha(context)
    private val backgroundVideosColour = ColourHelper.colourFromString(GeneralPrefs.backgroundVideos)

    private val controller =
        ScreensaverController(
            scope = mainScope,
            config =
                ScreensaverConfig(
                    cacheResumeDelayMs = CACHE_RESUME_DELAY,
                    errorRetryDelayMs = ERROR_DELAY,
                    sleepTimerMinutes = GeneralPrefs.sleepTimer.toLongOrNull() ?: 0L,
                    scheduledBlackoutEnabled = GeneralPrefs.scheduledBlackoutEnabled,
                    scheduledBlackoutStart = parseLocalTime(GeneralPrefs.scheduledBlackoutStart),
                    scheduledBlackoutEnd = parseLocalTime(GeneralPrefs.scheduledBlackoutEnd),
                    loopUntilSkipped = GeneralPrefs.loopUntilSkipped,
                    alternateTextPosition = shouldAlternateOverlays,
                    backgroundMusicEnabled = GeneralPrefs.playsBackgroundMusic,
                ),
            overlayStateStore = overlayStateStore,
            externalTrackControl =
                object : ExternalTrackControl {
                    override fun nextTrack() {
                        nowPlayingService?.nextTrack()
                    }

                    override fun previousTrack() {
                        nowPlayingService?.previousTrack()
                    }
                },
        )

    private var isPaused = false
    private var pauseStartTime: Long = 0
    private val metadataJobs = mutableMapOf<OverlayType, Job>()
    private var forcedMuteVideo = false
    private var userMutedVideo = !GeneralPrefs.playsVideoAudio
    private var playbackStarted = false
    private var renderedGeneration = -1L
    private var handledTransitionId = -1L
    private var lastBlackoutActive = false
    private var lastForcedMute: Boolean? = null
    private var lastError: ScreensaverError? = null
    private val cacheRepository = PlaylistCacheRepository(context)

    private val currentMedia: AerialMedia?
        get() = controller.state.value.media
    private lateinit var videoViewBinding: VideoViewBinding
    private val imageViewBinding: ImageViewBinding
    private val overlayViewBinding: OverlayViewBinding
    private val loadingView: View
    private val overlayView: View
    private var loadingText: TextView
    private var loadingSpinner: View
    private var loadingContainer: View
    private lateinit var videoPlayer: VideoPlayerView
    private var imagePlayer: ImagePlayerView
    private val brightnessView: View
    private val gradientTopView: View
    private val gradientBottomView: View
    private val progressBarView: ProgressBar
    private val notificationContainer: ViewGroup
    val view: View

    private val topLeftIds: List<Int>
    private val topRightIds: List<Int>
    private val bottomLeftIds: List<Int>
    private val bottomRightIds: List<Int>

    val blackOutMode: Boolean
        get() = controller.state.value.blackout.active
    val blackOutSource: BlackOutSource
        get() = controller.state.value.blackout.source

    init {
        val inflater = LayoutInflater.from(context)
        val binding = AerialActivityBinding.inflate(inflater)

        val backgroundLoading = ColourHelper.colourFromString(GeneralPrefs.backgroundLoading)
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
        val videoRoot =
            if (videoParent != null) {
                val index = videoParent.indexOfChild(initialVideoRoot)
                // The player from the inflated layout is discarded here, so release it. Otherwise its
                // ExoPlayer is kept alive by the DefaultBandwidthMeter singleton, which retains the
                // discarded view hierarchy and the Activity for the life of the process.
                binding.videoView.videoPlayer.release()
                videoParent.removeView(initialVideoRoot)
                val replacementVideoRoot = LayoutInflater.from(context).inflate(R.layout.video_view, videoParent, false)
                videoParent.addView(replacementVideoRoot, index)
                replacementVideoRoot
            } else {
                initialVideoRoot
            }

        installVideoView(videoRoot)

        imageViewBinding = binding.imageView
        imageViewBinding.root.setBackgroundColor(backgroundPhotos)
        imagePlayer = imageViewBinding.imagePlayer
        imagePlayer.setOnPlayerListener(this)

        brightnessView = binding.brightnessView
        progressBarView = binding.progressBar
        notificationContainer = view.findViewById(R.id.notification_container)

        // Setup loading message or hide it
        if (GeneralPrefs.showLoadingText) {
            loadingText.apply {
                textSize = GeneralPrefs.loadingTextSize.toFloat()
                typeface = FontHelper.getTypeface(context, GeneralPrefs.fontTypeface, GeneralPrefs.loadingTextWeight)
                includeFontPadding = false
                val offset = FontHelper.getFontVerticalOffset(context, GeneralPrefs.fontTypeface, textSize)
                setPadding(0, offset, 0, -offset)
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
        bindControllerState()
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

        DreamTiming.mark("screen.built")

        mainScope.launch {
            // Launch if we have permission
            // Used for a) Skip music tracks b) music info widget
            if (PermissionHelper.hasNotificationListenerPermission(context)) {
                nowPlayingService = NowPlayingService(context)
            }

            if (SonosPrefs.enabled && SonosPrefs.ipAddress.isNotEmpty()) {
                if (PermissionHelper.hasLocalNetworkPermission(context)) {
                    sonosService = SonosService(SonosPrefs.ipAddress, SonosPrefs.pollInterval).also { it.start() }
                } else {
                    Timber.w("SonosService not started: ACCESS_LOCAL_NETWORK permission not granted")
                }
            }

            if (overlayHelper.findOverlay<MessageOverlay>().isNotEmpty() && GeneralPrefs.messageApiEnabled) {
                KtorServer.start(context) { messageEvent ->
                    GlobalBus.post(messageEvent)
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
            DreamTiming.mark("playlist.resolved", "size=${playlist.size};cached=${mediaResult.isFromCache}")
            if (playlist.size > 0) {
                Timber.i("Playlist size: ${playlist.size}")
            }

            val music = createMusicPlayback(mediaResult.musicPlaylist)
            controller.start(
                playlist = playlist,
                fromCache = mediaResult.isFromCache,
                music = music,
                musicResumeIndex = mediaResult.musicResumeIndex,
            )

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

    private fun setVideoForcedMute(enabled: Boolean) {
        forcedMuteVideo = enabled
        videoPlayer.setMuteState(forcedMuteVideo, userMutedVideo)
    }

    private fun createMusicPlayback(musicPlaylist: MusicPlaylist?): MusicPlayback? {
        if (!GeneralPrefs.playsBackgroundMusic || musicPlaylist == null || musicPlaylist.size == 0) {
            Timber.i("MusicPlayer: no background music playlist available, skipping")
            return null
        }

        val player = MusicPlayer(context, musicPlaylist)
        player.onMediaItemChanged = {
            saveMusicTrackPosition()
            controller.onMusicTrackChanged(player.getCurrentTrackIndex())
        }
        player.onPlaybackFailed = {
            controller.onMusicPlaybackFailed()
            musicPlayer = null
        }
        musicPlayer = player
        return LegacyMusicPlayback(player)
    }

    private fun loadItem(
        media: AerialMedia,
        alternate: Boolean,
    ) {
        // Reset pause state when loading new item
        isPaused = false
        pauseStartTime = 0
        if (!playbackStarted) {
            DreamTiming.mark("playback.start", "cached=${GeneralPrefs.playlistCache}")
            playbackStarted = true
        }

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

        // Videos
        if (media.type == AerialMediaType.VIDEO) {
            videoPlayer.setVideo(media)
            videoViewBinding.root.visibility = View.VISIBLE
            imageViewBinding.root.visibility = View.INVISIBLE
        }

        // Images
        if (media.type == AerialMediaType.IMAGE) {
            // A video that was skipped, trimmed or looping would otherwise keep playing behind the
            // photo, and its finish callbacks would advance the photo before its timer elapses.
            videoPlayer.suspendPlayback()
            imagePlayer.setImage(media)
            imageViewBinding.root.visibility = View.VISIBLE
            videoViewBinding.root.visibility = View.INVISIBLE
        }

        // Best to rest progress bar (if enabled) before media playback
        if (GeneralPrefs.progressBarLocation != ProgressBarLocation.DISABLED) {
            GlobalBus.post(ProgressBarEvent(ProgressState.RESET))
        }

        // Only a video has playback to start here: starting for a photo would resume the video
        // that was suspended above.
        if (media.type == AerialMediaType.VIDEO) {
            videoPlayer.start()
        }
    }

    private fun fadeOutLoadingText() {
        // Fade out container (text + spinner)
        loadingContainer
            .animate()
            .alpha(0f)
            .setDuration(LOADING_FADE_OUT)
            .withEndAction {
                loadingContainer.visibility = View.GONE
            }.start()
    }

    private fun fadeInNextItem() {
        if (blackOutMode) return

        savePlaybackPosition()

        var startDelay: Long = 0
        val overlayDelay = (overlayVisibilityDelay * 1000) + mediaFadeIn

        // If first video (ie. screensaver startup), fade out 'loading...' text/spinner
        if (loadingContainer.isVisible) {
            fadeOutLoadingText()
            startDelay = LOADING_DELAY
        }

        // Reset any overlay animations
        overlayHelper.getOverlaysToFade().forEach { view ->
            view.animate()?.cancel()
            view.clearAnimation()
        }

        // Hide overlays immediately
//        if (autoHideOverlayDelay.toInt() == 0) {
//            overlayHelper.isHidden = true
//            setOverlayInstancesHidden(true)
//            overlayHelper.getOverlaysToFade().forEach { it.alpha = 0f }
//            // Also hide gradients immediately if they have fading overlays
//            // AND no persistent overlays
//            if (GeneralPrefs.showTopGradient && overlayHelper.hasTopOverlaysToFade() && !overlayHelper.hasTopPersistentOverlays()) {
//                gradientTopView.alpha = 0f
        when (overlayVisibilityMode) {
            "ALWAYS_VISIBLE" -> {
                // Overlays stay visible, no hiding
                overlayHelper.getOverlaysToFade().forEach { it.alpha = overlayVisibleAlpha }
                if (GeneralPrefs.showTopGradient && overlayHelper.hasTopOverlaysToFade()) {
                    gradientTopView.alpha = 1f
                }
                if (GeneralPrefs.showBottomGradient && overlayHelper.hasBottomOverlaysToFade()) {
                    gradientBottomView.alpha = 1f
                }
            }

            "ALWAYS_HIDDEN" -> {
                // Hide overlays immediately, only show on user reveal
                overlayHelper.getOverlaysToFade().forEach { it.alpha = 0f }
                if (GeneralPrefs.showTopGradient && overlayHelper.hasTopOverlaysToFade() && !overlayHelper.hasTopPersistentOverlays()) {
                    gradientTopView.alpha = 0f
                }
                if (GeneralPrefs.showBottomGradient && overlayHelper.hasBottomOverlaysToFade() &&
                    !overlayHelper.hasBottomPersistentOverlays()
                ) {
                    gradientBottomView.alpha = 0f
                }
            }

            "HIDE_AFTER_DELAY" -> {
                // Show overlays, then hide after delay
                overlayHelper.getOverlaysToFade().forEach { it.alpha = overlayVisibleAlpha }
                if (GeneralPrefs.showTopGradient && overlayHelper.hasTopOverlaysToFade()) {
                    gradientTopView.alpha = 1f
                }
                if (GeneralPrefs.showBottomGradient && overlayHelper.hasBottomOverlaysToFade()) {
                    gradientBottomView.alpha = 1f
                }
                hideOverlays(overlayDelay)
            }

            "SHOW_AFTER_DELAY" -> {
                // Hide overlays initially, show after delay, stay visible
                overlayHelper.getOverlaysToFade().forEach { it.alpha = 0f }
                if (GeneralPrefs.showTopGradient && overlayHelper.hasTopOverlaysToFade() && !overlayHelper.hasTopPersistentOverlays()) {
                    gradientTopView.alpha = 0f
                }
                if (GeneralPrefs.showBottomGradient && overlayHelper.hasBottomOverlaysToFade() &&
                    !overlayHelper.hasBottomPersistentOverlays()
                ) {
                    gradientBottomView.alpha = 0f
                }
                controller.onOverlaysBusy()
                mainScope.launch {
                    delay(overlayDelay.milliseconds)
                    val overlaysToFade = overlayHelper.getOverlaysToFade()
                    overlaysToFade.forEachIndexed { index, view ->
                        val animator =
                            view
                                .animate()
                                .alpha(overlayVisibleAlpha)
                                .setStartDelay(0)
                                .setDuration(overlayFadeIn)
                        if (index == overlaysToFade.lastIndex) {
                            animator.withEndAction { controller.onOverlaysReady() }
                        }
                        animator.start()
                    }
                    if (overlaysToFade.isEmpty()) {
                        controller.onOverlaysReady()
                    }
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
            }
        }

        // Fade out LoadingView
        // Video should be playing underneath
        loadingView
            .animate()
            .alpha(0f)
            .setStartDelay(startDelay)
            .setDuration(mediaFadeIn)
            .withEndAction {
                loadingView.alpha = 0f
                loadingView.visibility = View.INVISIBLE
                controller.onMediaVisible()
                if (overlayVisibilityMode == "ALWAYS_VISIBLE" || overlayVisibilityMode == "ALWAYS_HIDDEN") {
                    controller.onOverlaysReady()
                }
            }.start()
    }

    private fun fadeOutCurrentItem(request: TransitionRequest) {
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
            .withStartAction {
                loadingView.visibility = View.VISIBLE
                loadingView.alpha = 0f
            }.withEndAction {
                // Hide content views after faded out
                videoViewBinding.root.visibility = View.INVISIBLE
                // Let setVideo() replace the source without forcing a Realtek codec teardown.

                imageViewBinding.root.visibility = View.INVISIBLE
                imageViewBinding.imagePlayer.stop()

                // Reset pause state when transitioning between items
                isPaused = false
                pauseStartTime = 0
                controller.onTransitionCompleted(request.id)
            }.start()
    }

    private fun showLoadingError() {
        loadingText.text = resources.getString(R.string.loading_error)
        loadingSpinner.visibility = View.GONE
    }

    private fun hideOverlays(delay: Long = 0L) {
        val overlaysToFade = overlayHelper.getOverlaysToFade()

        if (overlaysToFade.isEmpty()) {
            controller.onOverlaysReady()
            return
        }

        controller.onOverlaysBusy()
        overlayHelper.isHidden = true
        setOverlayInstancesHidden(true)

        overlaysToFade.forEachIndexed { index, view ->
            val animator =
                view
                    .animate()
                    .alpha(0f)
                    .setStartDelay(delay)
                    .setDuration(overlayFadeOut)

            // Only set the end action on the last overlay
            if (index == overlaysToFade.lastIndex) {
                animator.withEndAction { controller.onOverlaysReady() }
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

    private fun setOverlayInstancesHidden(hidden: Boolean) {
        overlayHelper.findOverlay<NowPlayingOverlay>().forEach { it.isHidden = hidden }
        overlayHelper.findOverlay<WeatherNowOverlay>().forEach { it.isHidden = hidden }
        overlayHelper.findOverlay<WeatherForecastOverlay>().forEach { it.isHidden = hidden }
    }

    private fun renderMetadataOverlays(state: OverlayUiState) {
        overlayHelper.findOverlay<MetadataOverlay>().forEach { overlay ->
            state.metadata[overlay.type]?.let { overlay.render(it, videoPlayer) }
        }
    }

    fun showOverlays() {
        // Only allow reveal when overlays can be hidden
        if (overlayVisibilityMode == "ALWAYS_VISIBLE") return

        // If blackout mode is on, exit
        if (blackOutMode) return

        // If media fading in/out
        if (!controller.state.value.canSkip) return

        // Are overlays already visible
        if (!controller.state.value.canShowOverlays) return

        val overlaysToFade = overlayHelper.getOverlaysToFade()

        if (overlaysToFade.isEmpty()) return

        controller.onOverlaysBusy()
        overlayHelper.isHidden = false
        setOverlayInstancesHidden(false)

        overlaysToFade.forEachIndexed { index, view ->
            val animator =
                view
                    .animate()
                    .alpha(overlayVisibleAlpha)
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

    private fun savePlaybackPosition() {
        if (this::playlist.isInitialized && GeneralPrefs.playlistCache) {
            mainScope.launch {
                Timber.d("PlaylistCache: Saving playback position: ${playlist.currentPosition}")
                cacheRepository.saveMediaPosition(playlist.currentPosition)
            }
        }
    }

    private fun saveMusicTrackPosition() {
        if (GeneralPrefs.playlistCache) {
            mainScope.launch {
                musicPlayer?.let {
                    cacheRepository.saveMusicTrackIndex(it.getCurrentTrackIndex())
                }
            }
        }
    }

    fun stop() {
        if (isStopped) return
        isStopped = true

        if (GeneralPrefs.playlistCache) {
            // ExoPlayer must be accessed on the main thread
            val trackIndex = musicPlayer?.getCurrentTrackIndex() ?: 0
            runBlocking(Dispatchers.IO) {
                cacheRepository.saveMusicTrackIndex(trackIndex)
            }
        }
        controller.release()
        musicPlayer = null
        RefreshRateHelper.restoreOriginalMode(context)
        overlayEventBridge.stop()
        // Release before detaching: View.removeCallbacks() does nothing once mAttachInfo is null,
        // which would leave pending delayed messages holding the view and the Activity alive.
        videoPlayer.release()
        // Remove video view from parent to break context reference chain
        val videoParent = videoViewBinding.root.parent as? ViewGroup
        videoParent?.removeView(videoViewBinding.root)
        imagePlayer.release()
        KtorServer.stop()
        nowPlayingService?.stop()
        sonosService?.stop()
        weatherService?.stop()
        metadataJobs.values.forEach { it.cancel() }
        metadataJobs.clear()
        mainScope.cancel()
    }

    fun skipItem(previous: Boolean = false) = controller.skip(previous)

    fun toggleBlackOutMode(source: BlackOutSource = BlackOutSource.USER) = controller.toggleBlackout(source)

    /** Applies the controller's active blackout to the legacy views immediately. */
    private fun enterBlackOutMode() {
        loadingView.animate().cancel()
        loadingView.setBackgroundColor(Color.BLACK)
        loadingContainer.visibility = View.GONE
        loadingView.alpha = 1f
        loadingView.visibility = View.VISIBLE

        videoViewBinding.root.visibility = View.INVISIBLE
        videoViewBinding.videoPlayer.stop()
        imageViewBinding.root.visibility = View.INVISIBLE
        imageViewBinding.imagePlayer.stop()

        // The loading view sits below these layers, so hide them for a true blackout.
        overlayView.visibility = View.INVISIBLE
        progressBarView.visibility = View.INVISIBLE
        brightnessView.visibility = View.INVISIBLE
        notificationContainer.visibility = View.INVISIBLE
    }

    private fun exitBlackOutMode() {
        loadingView.setBackgroundColor(ColourHelper.colourFromString(GeneralPrefs.backgroundLoading))
        overlayView.visibility = View.VISIBLE
        progressBarView.visibility =
            if (GeneralPrefs.progressBarLocation == ProgressBarLocation.DISABLED) View.GONE else View.VISIBLE
        brightnessView.visibility =
            if (GeneralPrefs.videoBrightness == "100") View.GONE else View.VISIBLE
        notificationContainer.visibility = View.VISIBLE
    }

    fun nextTrack() = controller.nextTrack()

    fun previousTrack() = controller.previousTrack()

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
        val enabled = controller.toggleLoopUntilSkipped()
        // Persist the toggle so the player and the next launch agree on the current state
        GeneralPrefs.loopUntilSkipped = enabled
        videoPlayer.refreshFinishTimer()
        val message =
            if (enabled) {
                resources.getString(
                    R.string.playlist_loop_enabled,
                )
            } else {
                resources.getString(R.string.playlist_loop_disabled)
            }
        NotificationHelper.show(notificationContainer, message)
    }

    fun restartVideo() {
        if (blackOutMode) return
        if (currentMedia?.type != AerialMediaType.VIDEO) return
        // Restart resumes playback, so clear any paused state
        if (isPaused) resumeMedia()
        videoPlayer.restart()
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

        // Show notification
        NotificationHelper.show(notificationContainer, resources.getString(R.string.brightness_notification, newBrightness))
    }

    fun toggleMute() {
        videoPlayer.toggleMute()
        userMutedVideo = videoPlayer.mutedState
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
        if (blackOutMode) return
        recreateVideoPlayer()
        controller.onPlaybackError()
    }

    private fun installVideoView(
        root: View,
        visibility: Int = View.VISIBLE,
    ) {
        videoViewBinding = VideoViewBinding.bind(root)
        videoViewBinding.root.setBackgroundColor(backgroundVideosColour)
        videoViewBinding.root.visibility = visibility
        videoPlayer = videoViewBinding.videoPlayer
        videoPlayer.setOnPlayerListener(this)
        videoPlayer.setMuteState(forcedMuteVideo, userMutedVideo)
    }

    private fun recreateVideoPlayer() {
        val oldRoot = videoViewBinding.root
        val videoParent = oldRoot.parent as? ViewGroup ?: return
        val index = videoParent.indexOfChild(oldRoot)
        val previousVisibility = oldRoot.visibility
        videoPlayer.release()
        videoParent.removeView(oldRoot)

        val replacement = LayoutInflater.from(context).inflate(R.layout.video_view, videoParent, false)
        videoParent.addView(replacement, index)
        installVideoView(replacement, previousVisibility)

        // The metadata overlays hold a reference to the old player for POI timings.
        renderMetadataOverlays(overlayStateStore.uiState.value)
    }

    private fun handlePlaybackSpeedChanged() {
        val message = resources.getString(R.string.playlist_playback_speed_changed, GeneralPrefs.playbackSpeed + "x")
        NotificationHelper.show(notificationContainer, message)
    }

    override fun onVideoPlaybackSpeedChanged() = handlePlaybackSpeedChanged()

    override fun onVideoAlmostFinished() {
        // A video can still report a finish after the screen has moved on to a photo.
        if (!PlaybackCallbacks.videoEventAppliesTo(currentMedia?.type)) return
        controller.onItemFinished()
    }

    override fun onVideoPrepared() {
        if (!blackOutMode && PlaybackCallbacks.videoEventAppliesTo(currentMedia?.type)) fadeInNextItem()
    }

    override fun onVideoError() {
        // A late error from a video must not skip the photo that replaced it.
        if (!PlaybackCallbacks.videoEventAppliesTo(currentMedia?.type)) return
        handleError()
    }

    override fun onVideoMetadataExtracted(metadata: ExtractedVideoMetadata) {
        // A late metadata report from the previous video must not overwrite the photo's details.
        val media = currentMedia?.takeIf { PlaybackCallbacks.videoEventAppliesTo(it.type) } ?: return
        Timber.i("Video metadata: %s", formatVideoMetadataForLog(metadata))
        val changed = applyVideoMetadataToMedia(media, metadata)

        if (changed) {
            updateMetadataOverlayData(media)
        }
    }

    override fun onImageFinished() = controller.onItemFinished()

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
        if (blackOutMode) return
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

    private fun bindControllerState() {
        mainScope.launch {
            controller.state.collect { state ->
                if (!isStopped) {
                    renderControllerState(state)
                }
            }
        }
    }

    private fun renderControllerState(state: ScreensaverState) {
        if (state.music.forcedVideoMute != lastForcedMute) {
            lastForcedMute = state.music.forcedVideoMute
            setVideoForcedMute(state.music.forcedVideoMute)
        }

        if (state.blackout.active != lastBlackoutActive) {
            lastBlackoutActive = state.blackout.active
            if (state.blackout.active) {
                enterBlackOutMode()
            } else {
                exitBlackOutMode()
            }
        }

        if (state.error != lastError) {
            lastError = state.error
            if (state.error == ScreensaverError.EMPTY_PLAYLIST || state.error == ScreensaverError.ITEM_UNAVAILABLE) {
                showLoadingError()
            }
        }

        val media = state.media
        if (media != null && state.itemGeneration != renderedGeneration) {
            renderedGeneration = state.itemGeneration
            loadItem(media, state.alternateOverlays)
        }

        val transition = state.transition
        if (transition != null && transition.id != handledTransitionId) {
            handledTransitionId = transition.id
            fadeOutCurrentItem(transition)
        }
    }

    private fun renderOverlayState(state: OverlayUiState) {
        renderMetadataOverlays(state)

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
        const val ERROR_DELAY: Long = 1000 // Delay before loading next item, after error
        const val CACHE_RESUME_DELAY: Long = 1000 // Delay before starting playback when restoring from cache
    }
}

private fun parseLocalTime(timeStr: String): java.time.LocalTime? =
    try {
        val parts = timeStr.split(":")
        if (parts.size == 2) {
            java.time.LocalTime.of(parts[0].trim().toInt(), parts[1].trim().toInt())
        } else {
            null
        }
    } catch (e: Exception) {
        null
    }
