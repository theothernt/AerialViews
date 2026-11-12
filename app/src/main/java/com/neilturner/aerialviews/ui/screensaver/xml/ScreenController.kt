package com.neilturner.aerialviews.ui.screensaver.xml

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isVisible
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.data.PlaylistCacheRepository
import com.neilturner.aerialviews.databinding.AerialActivityBinding
import com.neilturner.aerialviews.databinding.ImageViewBinding
import com.neilturner.aerialviews.databinding.OverlayViewBinding
import com.neilturner.aerialviews.databinding.VideoViewBinding
import com.neilturner.aerialviews.models.LoadingStatus
import com.neilturner.aerialviews.models.MediaPlaylist
import com.neilturner.aerialviews.models.OverlayIds
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
import com.neilturner.aerialviews.ui.core.ExtractedVideoMetadata
import com.neilturner.aerialviews.ui.core.ImagePlayerView
import com.neilturner.aerialviews.ui.core.ImagePlayerView.OnImagePlayerEventListener
import com.neilturner.aerialviews.ui.core.MetadataResolver
import com.neilturner.aerialviews.ui.core.PlaybackCallbacks
import com.neilturner.aerialviews.ui.core.ScheduledBlackoutWindow
import com.neilturner.aerialviews.ui.core.VideoPlayerView
import com.neilturner.aerialviews.ui.core.VideoPlayerView.OnVideoPlayerEventListener
import com.neilturner.aerialviews.ui.core.applyVideoMetadataToMedia
import com.neilturner.aerialviews.ui.core.formatVideoMetadataForLog
import com.neilturner.aerialviews.ui.helpers.ColourHelper
import com.neilturner.aerialviews.ui.helpers.FontHelper
import com.neilturner.aerialviews.ui.helpers.GradientHelper
import com.neilturner.aerialviews.ui.helpers.NotificationHelper
import com.neilturner.aerialviews.ui.helpers.OverlayOpacityHelper
import com.neilturner.aerialviews.ui.helpers.PermissionHelper
import com.neilturner.aerialviews.ui.helpers.RefreshRateHelper
import com.neilturner.aerialviews.ui.helpers.SlotHelper
import com.neilturner.aerialviews.ui.helpers.WindowHelper
import com.neilturner.aerialviews.ui.overlays.EnabledOverlays
import com.neilturner.aerialviews.ui.overlays.compose.LoadingIndicator
import com.neilturner.aerialviews.ui.overlays.compose.ProgressBarOverlay
import com.neilturner.aerialviews.ui.overlays.compose.ScreensaverOverlays
import com.neilturner.aerialviews.ui.overlays.compose.overlayCorners
import com.neilturner.aerialviews.ui.overlays.compose.shouldFadeRow
import com.neilturner.aerialviews.ui.overlays.state.MessageOverlayState
import com.neilturner.aerialviews.ui.overlays.state.OverlayEventBridge
import com.neilturner.aerialviews.ui.overlays.state.OverlayStateStore
import com.neilturner.aerialviews.ui.overlays.state.OverlayUiState
import com.neilturner.aerialviews.ui.overlays.xml.MessageOverlay
import com.neilturner.aerialviews.ui.overlays.xml.MetadataOverlay
import com.neilturner.aerialviews.ui.overlays.xml.NowPlayingOverlay
import com.neilturner.aerialviews.ui.overlays.xml.OverlayHelper
import com.neilturner.aerialviews.ui.overlays.xml.WeatherForecastOverlay
import com.neilturner.aerialviews.ui.overlays.xml.WeatherNowOverlay
import com.neilturner.aerialviews.ui.theme.AerialTheme
import com.neilturner.aerialviews.utils.DreamTiming
import com.neilturner.aerialviews.utils.FirebaseHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import me.kosert.flowbus.GlobalBus
import timber.log.Timber
import kotlin.math.abs
import kotlin.time.Duration.Companion.milliseconds

enum class BlackOutSource {
    NONE,
    USER,
    SLEEP_TIMER,
    SCHEDULED,
}

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
    private val progressBarLocation = GeneralPrefs.progressBarLocation ?: ProgressBarLocation.DISABLED
    private val progressBarOpacity = GeneralPrefs.progressBarOpacity.toFloat() / 100

    // Which engine draws the overlays, and the state the Compose layer observes
    private val useComposeOverlays = GeneralPrefs.useComposeScreensaver
    private val enabledOverlays = EnabledOverlays.types(context)
    private val slotPrefs = SlotHelper.slotPrefs(context)
    private val fadeCorners = GeneralPrefs.overlayFadeCornersSelection
    private val composeAlternate = MutableStateFlow(false)
    private val composeFadeAlpha = MutableStateFlow(overlayVisibleAlpha)
    private val composeLoadingText = MutableStateFlow("")
    private val composeLoadingSpinner = MutableStateFlow(true)
    private val backgroundVideosColour = ColourHelper.colourFromString(GeneralPrefs.backgroundVideos)

    private var canShowOverlays = false
    private var alternate = false
    private var previousItem = false
    private var explicitSkip = false
    private var canSkip = false
    private var isPaused = false
    private var loopUntilSkipped = GeneralPrefs.loopUntilSkipped
    private var pauseStartTime: Long = 0
    private var sleepTimerJob: Job? = null
    private val metadataJobs = mutableMapOf<OverlayType, Job>()
    private var currentMedia: AerialMedia? = null
    private var forcedMuteVideo = false
    private var userMutedVideo = !GeneralPrefs.playsVideoAudio
    private val cacheRepository = PlaylistCacheRepository(context)
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

    var blackOutMode = false
        private set
    var blackOutSource: BlackOutSource = BlackOutSource.NONE
        private set
    private var scheduledBlackoutJob: Job? = null
    private var wasInScheduledBlackoutWindow: Boolean? = null

    init {
        // Which engine this session built. Without it a beta crash report or a "the clock looks
        // wrong" report cannot be attributed to the Compose path, which is the point of shipping
        // the toggle to beta at all. Logged unconditionally: the logging window in FirebaseHelper
        // closed on 2026-11-01, and an attribution tag that never fires cannot do its job.
        FirebaseHelper.analyticsUserProperty(
            name = "use_compose_screensaver",
            value = useComposeOverlays.toString(),
            alwaysLog = true,
        )
        FirebaseHelper.crashlyticsLogKeys(
            key = "use_compose_screensaver",
            value = useComposeOverlays,
            alwaysLog = true,
        )

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

        if (useComposeOverlays) {
            installComposeLoading()
        }

        // Setup overlays and set initial positions
        overlayHelper = OverlayHelper(context, GeneralPrefs)
        val overlayIds =
            if (useComposeOverlays) {
                installComposeOverlays()
                OverlayIds(emptyList(), emptyList(), emptyList(), emptyList())
            } else {
                overlayHelper.buildOverlaysAndIds(overlayViewBinding)
            }
        this.bottomLeftIds = overlayIds.bottomLeftIds
        this.bottomRightIds = overlayIds.bottomRightIds
        this.topLeftIds = overlayIds.topLeftIds
        this.topRightIds = overlayIds.topRightIds
        bindOverlayState()
        overlayEventBridge.start()

        // Setup progress bar
        if (useComposeOverlays) {
            // The Compose layer draws the bar, so the View stays out of the way entirely.
            progressBarView.visibility = View.GONE
        } else if (progressBarLocation != ProgressBarLocation.DISABLED) {
            val gravity = if (progressBarLocation == ProgressBarLocation.TOP) Gravity.TOP else Gravity.BOTTOM
            (progressBarView.layoutParams as FrameLayout.LayoutParams).gravity = gravity

            progressBarView.alpha = progressBarOpacity
            Timber.i("Progress bar: $progressBarOpacity, $progressBarLocation")

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

            // Asked of the slot preferences, not of instantiated Views: with the Compose overlay
            // layer there are no MessageOverlay Views, and the API would silently never start.
            if (enabledOverlays.any { it in MESSAGE_OVERLAYS } && GeneralPrefs.messageApiEnabled) {
                KtorServer.start(context) { messageEvent ->
                    GlobalBus.post(messageEvent)
                }
            }

            // Build playlist and start screensaver
            val mediaResult =
                MediaService(context).fetchMedia { status ->
                    mainScope.launch {
                        onLoadingStatus(status)
                    }
                }
            playlist = mediaResult.mediaPlaylist
            DreamTiming.mark("playlist.resolved", "size=${playlist.size};cached=${mediaResult.isFromCache}")
            if (playlist.size > 0) {
                Timber.i("Playlist size: ${playlist.size}")
                var startPlayback = true
                if (mediaResult.isFromCache) {
                    Timber.i("Playlist restored from cache - delaying ${CACHE_RESUME_DELAY}ms before starting playback")
                    delay(CACHE_RESUME_DELAY.milliseconds)
                    // stop() cancels mainScope, so a stopped session cannot be observed here;
                    // only a blackout entered while waiting should defer playback. Music, weather
                    // and scheduled blackout must still be set up.
                    startPlayback = !blackOutMode
                    if (!startPlayback) {
                        Timber.i("Blackout entered during cache resume delay, deferring playback start")
                    }
                }
                if (startPlayback) {
                    DreamTiming.mark("playback.start", "cached=${mediaResult.isFromCache}")
                    loadNextItem()
                    scheduleSleepTimer()
                }
                scheduleScheduledBlackout()
            } else {
                showLoadingError()
            }

            // Setup music service
            setupMusicPlayer(mediaResult.musicPlaylist, mediaResult.musicResumeIndex)

            // Setup weather service
            val hasWeatherNowOverlay = OverlayType.WEATHER1 in enabledOverlays
            val hasForecastOverlay = OverlayType.WEATHER2 in enabledOverlays
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
                delay((minutes * 60_000L).milliseconds)
                if (!blackOutMode) {
                    Timber.i("Sleep timer finished - toggling blackout mode")
                    toggleBlackOutMode(BlackOutSource.SLEEP_TIMER)
                }
            }
    }

    private fun scheduleScheduledBlackout() {
        scheduledBlackoutJob?.cancel()
        wasInScheduledBlackoutWindow = null
        if (!GeneralPrefs.scheduledBlackoutEnabled) {
            Timber.i("Scheduled blackout disabled")
            return
        }
        Timber.i("Scheduling blackout check ticker")
        scheduledBlackoutJob =
            mainScope.launch {
                while (true) {
                    checkScheduledBlackout()
                    delay(15_000L.milliseconds)
                }
            }
    }

    private fun checkScheduledBlackout() {
        if (!GeneralPrefs.scheduledBlackoutEnabled) return

        val startTime = parseLocalTime(GeneralPrefs.scheduledBlackoutStart) ?: return
        val endTime = parseLocalTime(GeneralPrefs.scheduledBlackoutEnd) ?: return
        val now = java.time.LocalTime.now()

        val isNowInWindow = ScheduledBlackoutWindow.contains(startTime, endTime, now)

        if (wasInScheduledBlackoutWindow == null) {
            wasInScheduledBlackoutWindow = isNowInWindow
            if (isNowInWindow && !blackOutMode) {
                Timber.i("Initial check: inside scheduled blackout window ($startTime to $endTime)")
                enterBlackOutMode(BlackOutSource.SCHEDULED)
            }
        } else if (isNowInWindow != wasInScheduledBlackoutWindow) {
            wasInScheduledBlackoutWindow = isNowInWindow
            if (isNowInWindow && !blackOutMode) {
                Timber.i("Scheduled blackout window started ($startTime to $endTime)")
                enterBlackOutMode(BlackOutSource.SCHEDULED)
            } else if (!isNowInWindow && blackOutMode && blackOutSource == BlackOutSource.SCHEDULED) {
                Timber.i("Scheduled blackout window ended ($startTime to $endTime)")
                exitBlackOutMode()
            }
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

    private fun setVideoForcedMute(enabled: Boolean) {
        forcedMuteVideo = enabled
        videoPlayer.setMuteState(forcedMuteVideo, userMutedVideo)
    }

    private fun setupMusicPlayer(
        musicPlaylist: MusicPlaylist?,
        resumeIndex: Int = 0,
    ) {
        val backgroundMusicSelected = GeneralPrefs.playsBackgroundMusic

        if (!backgroundMusicSelected) {
            Timber.i("MusicPlayer: background music not selected, skipping")
            setVideoForcedMute(false)
            return
        }

        if (musicPlaylist == null || musicPlaylist.size == 0) {
            Timber.i("MusicPlayer: no music playlist available, skipping")
            setVideoForcedMute(false)
            return
        }

        setVideoForcedMute(true)

        musicPlayer = MusicPlayer(context, musicPlaylist)
        musicPlayer?.onMediaItemChanged = { saveMusicTrackPosition() }
        musicPlayer?.onPlaybackFailed = { handleMusicPlaybackFailed() }
        musicPlayer?.createPlayer()
        musicPlayer?.load(resumeIndex)
        if (blackOutMode) {
            musicPlayer?.pause()
            Timber.i("MusicPlayer: not starting while blackout is active")
        } else {
            musicPlayer?.play()
            Timber.i("MusicPlayer: playing ${musicPlaylist.size} tracks")
        }
    }

    /**
     * Background music could not recover from a playback error. Release it and stay silent: the
     * user asked for background music, so video audio is not restored over the top of it.
     */
    private fun handleMusicPlaybackFailed() {
        Timber.i("MusicPlayer: playback abandoned, staying silent")
        musicPlayer?.release()
        musicPlayer = null
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

        // The value used for this item, before the flip below - the same corner order the XML
        // path hands to assignOverlaysAndIds()
        composeAlternate.value = alternate

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

        canShowOverlays = false
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
        // Seed the Compose layer from the same mode; hideOverlays()/showOverlays() drive the
        // transitions after this, exactly as they drive the View alphas.
        composeFadeAlpha.value =
            when (overlayVisibilityMode) {
                "ALWAYS_VISIBLE", "HIDE_AFTER_DELAY" -> overlayVisibleAlpha
                else -> 0f
            }

        // The View predicates in the switch below cannot see the Compose layer, so its gradient
        // alphas are restored from the same mode here. Without this a gradient faded out by
        // hideOverlays() would never come back.
        if (useComposeOverlays) {
            setComposeGradientAlpha(
                alpha = if (overlayVisibilityMode == "ALWAYS_VISIBLE" || overlayVisibilityMode == "HIDE_AFTER_DELAY") 1f else 0f,
                animate = false,
            )
        }

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
                canShowOverlays = true
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
                canShowOverlays = true
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
                            animator.withEndAction { canShowOverlays = true }
                        }
                        animator.start()
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

                if (!blackOutMode) {
                    val wasExplicitSkip = explicitSkip
                    val loadPreviousItem = previousItem
                    explicitSkip = false
                    previousItem = false

                    if (wasExplicitSkip) {
                        loadNextItem(loadPreviousItem)
                    } else if (loopUntilSkipped && currentMedia != null) {
                        replayCurrentItem()
                    } else {
                        loadNextItem(false)
                    }
                } else {
                    explicitSkip = false
                    previousItem = false
                }
            }.start()
    }

    private fun replayCurrentItem() {
        val media = currentMedia
        if (media != null) {
            loadItem(media)
        } else {
            loadNextItem()
        }
    }

    private fun showLoadingError() {
        setLoadingText(resources.getString(R.string.loading_error))
        setLoadingSpinnerVisible(false)
    }

    private fun hideOverlays(delay: Long = 0L) {
        if (useComposeOverlays) {
            setComposeFadeAlpha(0f, delay)
            fadeComposeGradients(visible = false, delay = delay)
            canShowOverlays = true
            return
        }

        val overlaysToFade = overlayHelper.getOverlaysToFade()

        if (overlaysToFade.isEmpty()) {
            canShowOverlays = true
            return
        }

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
        if (!canSkip) return

        // Are overlays already visible
        if (!canShowOverlays) return

        val overlaysToFade = overlayHelper.getOverlaysToFade()

        if (useComposeOverlays) {
            canShowOverlays = false
            setComposeFadeAlpha(overlayVisibleAlpha)
            fadeComposeGradients(visible = true)
            mainScope.launch {
                delay(overlayRevealTimeout * 1000)
                setComposeFadeAlpha(0f)
                fadeComposeGradients(visible = false)
                canShowOverlays = true
            }
            return
        }

        if (overlaysToFade.isEmpty()) return

        canShowOverlays = false
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

    private fun loadNextItem(previous: Boolean = false) {
        if (!this::playlist.isInitialized || playlist.size == 0) {
            Timber.w("ScreenController: Cannot load an item from an empty playlist")
            showLoadingError()
            return
        }

        val media =
            if (previous) {
                playlist.previousItem()
            } else {
                playlist.nextItem()
            }

        if (media == null) {
            Timber.e("ScreenController: Playlist could not supply an item")
            showLoadingError()
            return
        }

        loadItem(media)
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
        musicPlayer?.pause()
        musicPlayer?.release()
        sleepTimerJob?.cancel()
        scheduledBlackoutJob?.cancel()
        metadataJobs.values.forEach { it.cancel() }
        metadataJobs.clear()
        mainScope.cancel()
    }

    fun skipItem(previous: Boolean = false) {
        explicitSkip = true
        previousItem = previous
        fadeOutCurrentItem()
    }

    fun toggleBlackOutMode(source: BlackOutSource = BlackOutSource.USER) {
        if (!this::playlist.isInitialized || playlist.size == 0) {
            return
        }

        if (!blackOutMode) {
            enterBlackOutMode(source)
        } else if (
            blackOutSource != BlackOutSource.SCHEDULED || source == BlackOutSource.SCHEDULED
        ) {
            exitBlackOutMode()
        }
    }

    /**
     * Enters blackout immediately, including during initial media preparation. The normal
     * fade-out path intentionally requires a fully displayed item, which made scheduled
     * blackout ineffective when the first item was still loading.
     */
    private fun enterBlackOutMode(source: BlackOutSource) {
        blackOutMode = true
        blackOutSource = source
        sleepTimerJob?.cancel()
        canSkip = false

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
        musicPlayer?.pause()
    }

    private fun exitBlackOutMode() {
        blackOutMode = false
        blackOutSource = BlackOutSource.NONE
        loadingView.setBackgroundColor(ColourHelper.colourFromString(GeneralPrefs.backgroundLoading))
        overlayView.visibility = View.VISIBLE
        progressBarView.visibility =
            if (useComposeOverlays || progressBarLocation == ProgressBarLocation.DISABLED) View.GONE else View.VISIBLE
        brightnessView.visibility =
            if (GeneralPrefs.videoBrightness == "100") View.GONE else View.VISIBLE
        notificationContainer.visibility = View.VISIBLE
        loadNextItem()
        musicPlayer?.resume()
        scheduleSleepTimer()
    }

    fun nextTrack() {
        val music = musicPlayer
        if (music != null && music.hasMusic()) {
            music.nextTrack()
            saveMusicTrackPosition()
        } else {
            nowPlayingService?.nextTrack()
        }
    }

    fun previousTrack() {
        val music = musicPlayer
        if (music != null && music.hasMusic()) {
            music.previousTrack()
            saveMusicTrackPosition()
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
        loopUntilSkipped = !loopUntilSkipped
        // Persist the toggle so the player and the next launch agree on the current state
        GeneralPrefs.loopUntilSkipped = loopUntilSkipped
        videoPlayer.refreshFinishTimer()
        val message =
            if (loopUntilSkipped) {
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

        mainScope.launch {
            delay(ERROR_DELAY.milliseconds)
            if (loadingView.isVisible) {
                loadNextItem()
            } else {
                explicitSkip = true
                fadeOutCurrentItem()
            }
        }
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
        fadeOutCurrentItem()
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

        // The Compose bar animates itself from state.progress; driving the View as well would
        // animate a second, hidden bar.
        if (!useComposeOverlays) {
            progressBarView.render(state.progress)
        }
    }

    private fun currentVideoPosition(): Long = if (this::videoPlayer.isInitialized) videoPlayer.currentPosition.toLong() else 0L

    /** Fades the gradient Views along with the Compose overlay layer. */
    private fun fadeComposeGradients(
        visible: Boolean,
        delay: Long = 0L,
    ) {
        setComposeGradientAlpha(if (visible) 1f else 0f, animate = true, delay = delay)
    }

    /**
     * Sets the gradient alphas for the Compose overlay layer.
     *
     * The XML path asks OverlayHelper, which inspects the instantiated overlay Views to decide
     * whether a row has anything to fade and whether anything stays visible. There are no such
     * Views in Compose mode, so the same questions are answered from the slot preferences. A row
     * is only managed when its overlays fade: with a persistent overlay the gradient has to stay,
     * so it is left alone at whatever alpha it already had.
     */
    private fun setComposeGradientAlpha(
        alpha: Float,
        animate: Boolean,
        delay: Long = 0L,
    ) {
        val corners = overlayCorners(slotPrefs, alternate)

        if (GeneralPrefs.showTopGradient && shouldFadeRow(listOf(corners.topStart, corners.topEnd), fadeCorners)) {
            applyGradientAlpha(gradientTopView, alpha, animate, delay)
        }

        if (GeneralPrefs.showBottomGradient && shouldFadeRow(listOf(corners.bottomStart, corners.bottomEnd), fadeCorners)) {
            applyGradientAlpha(gradientBottomView, alpha, animate, delay)
        }
    }

    private fun applyGradientAlpha(
        view: View,
        alpha: Float,
        animate: Boolean,
        delay: Long,
    ) {
        if (!animate) {
            view.animate().cancel()
            view.alpha = alpha
            return
        }

        view
            .animate()
            .alpha(alpha)
            .setStartDelay(delay)
            .setDuration(if (alpha >= 1f) overlayFadeIn else overlayFadeOut)
            .start()
    }

    /**
     * Applies a playlist fetch status to the loading indicator.
     *
     * A named function rather than a body inlined into the MediaService callback, so the loading
     * state can be driven from a unit test without running a fetch.
     */
    internal fun onLoadingStatus(status: LoadingStatus) {
        setLoadingText(
            when (status) {
                LoadingStatus.RESUMING -> resources.getString(R.string.loading_resuming)
                LoadingStatus.BUILDING -> resources.getString(R.string.loading_building)
                LoadingStatus.LOADING -> resources.getString(R.string.loading_title)
            },
        )
        setLoadingSpinnerVisible(true)
    }

    /** Whether the Compose loading indicator is showing. Used by the loading indicator test. */
    internal val composeLoadingIndicatorVisible: Boolean
        get() = composeLoadingSpinner.value

    private fun setLoadingText(text: String) {
        if (!useComposeOverlays) {
            loadingText.text = text
        }
        composeLoadingText.value = text
    }

    private fun setLoadingSpinnerVisible(visible: Boolean) {
        // installComposeLoading() hides the XML spinner and label, and the Compose indicator
        // replaces them. Re-showing the View here would draw a second spinner beside it.
        if (!useComposeOverlays) {
            loadingSpinner.visibility = if (visible) View.VISIBLE else View.GONE
        }
        composeLoadingSpinner.value = visible
    }

    /**
     * The Compose counterpart of loading_view.xml's spinner and status text.
     *
     * Added inside the existing loading_container so its visibility, margins and fade out keep
     * working unchanged: the View animations still drive the parent, and the Compose content goes
     * with it.
     */
    private fun installComposeLoading() {
        loadingSpinner.visibility = View.GONE
        loadingText.visibility = View.GONE

        val sizeSp = GeneralPrefs.loadingTextSize.toFloat()
        val weight = GeneralPrefs.loadingTextWeight
        val composeView =
            ComposeView(context).apply {
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
                layoutParams =
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    )
                setContent {
                    val text by composeLoadingText.collectAsStateWithLifecycle()
                    val spinner by composeLoadingSpinner.collectAsStateWithLifecycle()

                    AerialTheme {
                        LoadingIndicator(
                            text = text,
                            spinnerVisible = spinner,
                            sizeSp = sizeSp,
                            weight = weight,
                        )
                    }
                }
            }

        (loadingContainer as? ViewGroup)?.addView(composeView)
    }

    /**
     * Hosts the Compose overlay layer inside overlay_view.
     *
     * Not at the root of aerial_activity: a FrameLayout draws children in order, so appending it
     * there would put the overlays above the brightness dimmer and above the notification toasts.
     * Living under overlayView also means blackout mode hides them, as it hides the XML overlays.
     */
    private fun installComposeOverlays() {
        val composeView =
            ComposeView(context).apply {
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
                layoutParams =
                    ConstraintLayout
                        .LayoutParams(
                            ConstraintLayout.LayoutParams.MATCH_CONSTRAINT,
                            ConstraintLayout.LayoutParams.MATCH_CONSTRAINT,
                        ).apply {
                            topToTop = ConstraintLayout.LayoutParams.PARENT_ID
                            bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
                            startToStart = ConstraintLayout.LayoutParams.PARENT_ID
                            endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
                        }
                setContent {
                    val state by overlayStateStore.uiState.collectAsStateWithLifecycle()
                    val alternatePosition by composeAlternate.collectAsStateWithLifecycle()
                    val fade by composeFadeAlpha.collectAsStateWithLifecycle()

                    AerialTheme {
                        Box(modifier = Modifier.fillMaxSize()) {
                            ScreensaverOverlays(
                                state = state,
                                slots = slotPrefs,
                                alternate = alternatePosition,
                                visibleAlpha = overlayVisibleAlpha,
                                videoPosition = ::currentVideoPosition,
                                fadeAlpha = fade,
                                fadeDurationMillis = overlayFadeOut.toInt(),
                                fadeCorners = fadeCorners,
                            )

                            // Outside ScreensaverOverlays so the bar tracks playback rather than
                            // the overlay fade, and hidden with the layer during blackout.
                            ProgressBarOverlay(
                                state = state.progress,
                                location = progressBarLocation,
                                opacity = progressBarOpacity,
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .align(
                                            if (progressBarLocation == ProgressBarLocation.TOP) {
                                                Alignment.TopCenter
                                            } else {
                                                Alignment.BottomCenter
                                            },
                                        ),
                            )
                        }
                    }
                }
            }

        overlayViewBinding.layout.addView(composeView)

        // The Compose layer replaces the four XML corners. The gradients stay Views so their own
        // fade behaviour keeps working while the overlays move to Compose.
        with(overlayViewBinding) {
            flowTopLeft.visibility = View.GONE
            flowTopRight.visibility = View.GONE
            flowBottomLeft.visibility = View.GONE
            flowBottomRight.visibility = View.GONE
        }
    }

    private fun setComposeFadeAlpha(
        alpha: Float,
        delayMillis: Long = 0L,
    ) {
        if (!useComposeOverlays) return
        if (delayMillis <= 0L) {
            composeFadeAlpha.value = alpha
        } else {
            mainScope.launch {
                delay(delayMillis.milliseconds)
                composeFadeAlpha.value = alpha
            }
        }
    }

    companion object {
        const val LOADING_FADE_OUT: Long = 300 // Fade out loading text
        const val LOADING_DELAY: Long = 400 // Delay before fading out loading view
        const val ERROR_DELAY: Long = 1000 // Delay before loading next item, after error
        const val CACHE_RESUME_DELAY: Long = 1000 // Delay before starting playback when restoring from cache

        private val MESSAGE_OVERLAYS =
            setOf(OverlayType.MESSAGE1, OverlayType.MESSAGE2, OverlayType.MESSAGE3, OverlayType.MESSAGE4)
    }
}
