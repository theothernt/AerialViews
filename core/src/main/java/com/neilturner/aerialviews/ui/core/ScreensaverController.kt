package com.neilturner.aerialviews.ui.core

import com.neilturner.aerialviews.models.MediaPlaylist
import com.neilturner.aerialviews.models.videos.AerialMedia
import com.neilturner.aerialviews.services.MusicEvent
import com.neilturner.aerialviews.ui.overlays.state.OverlayStateStore
import com.neilturner.aerialviews.ui.overlays.state.OverlayUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.LocalTime

/** The navigation contract the controller needs from a playlist. */
interface PlaylistNavigator {
    val size: Int
    val currentPosition: Int

    fun nextItem(): AerialMedia?

    fun previousItem(): AerialMedia?
}

class MediaPlaylistNavigator(
    private val playlist: MediaPlaylist,
) : PlaylistNavigator {
    override val size: Int get() = playlist.size
    override val currentPosition: Int get() = playlist.currentPosition

    override fun nextItem(): AerialMedia? = playlist.nextItem()

    override fun previousItem(): AerialMedia? = playlist.previousItem()
}

/**
 * The playback operations owned by the controller.
 *
 * `MusicPlayer` can be adapted to this interface; the controller deliberately does not depend on
 * the Android player so its policy can be tested with a fake.
 */
interface MusicPlayback {
    fun load(startTrackIndex: Int)

    fun play()

    fun pause()

    fun resume()

    fun nextTrack()

    fun previousTrack()

    fun release()

    fun hasMusic(): Boolean

    fun currentTrackIndex(): Int
}

/** The notification-listener transport controls used when no background music is selected. */
interface ExternalTrackControl {
    fun nextTrack()

    fun previousTrack()
}

data class ScreensaverConfig(
    val cacheResumeDelayMs: Long = 1_000,
    val errorRetryDelayMs: Long = 1_000,
    val scheduledBlackoutCheckIntervalMs: Long = 15_000,
    val sleepTimerMinutes: Long = 0,
    val scheduledBlackoutEnabled: Boolean = false,
    val scheduledBlackoutStart: LocalTime? = null,
    val scheduledBlackoutEnd: LocalTime? = null,
    val loopUntilSkipped: Boolean = false,
    val alternateTextPosition: Boolean = true,
    val backgroundMusicEnabled: Boolean = false,
)

enum class ScreensaverPhase {
    IDLE,
    LOADING,
    PLAYING,
    TRANSITIONING,
    BLACKOUT,
    ERROR,
    STOPPED,
}

enum class ScreensaverError {
    EMPTY_PLAYLIST,
    ITEM_UNAVAILABLE,
    PLAYBACK_ERROR,
}

enum class TransitionReason {
    ITEM_FINISHED,
    USER_SKIP,
    PLAYBACK_ERROR,
}

data class TransitionRequest(
    val id: Long,
    val reason: TransitionReason,
    val previous: Boolean = false,
)

data class BlackoutState(
    val active: Boolean = false,
    val source: BlackOutSource = BlackOutSource.NONE,
)

enum class MusicMode {
    DISABLED,
    PLAYING,
    PAUSED,
    FAILED,
}

data class MusicState(
    val mode: MusicMode = MusicMode.DISABLED,
    val forcedVideoMute: Boolean = false,
    val trackIndex: Int = 0,
)

/**
 * Playback-facing state. Overlay content lives in [OverlayStateStore] and is exposed separately;
 * this avoids a second copy of the metadata/weather/message model.
 */
data class ScreensaverState(
    val phase: ScreensaverPhase = ScreensaverPhase.IDLE,
    val media: AerialMedia? = null,
    val playlistPosition: Int = -1,
    val playlistSize: Int = 0,
    val itemGeneration: Long = 0,
    val canSkip: Boolean = false,
    val canShowOverlays: Boolean = false,
    /** Whether the current item's overlay columns are mirrored by the renderer. */
    val alternateOverlays: Boolean = false,
    val loopUntilSkipped: Boolean = false,
    val transition: TransitionRequest? = null,
    val blackout: BlackoutState = BlackoutState(),
    val music: MusicState = MusicState(),
    val error: ScreensaverError? = null,
)

/**
 * Owns the screensaver decisions that are independent of a view.
 *
 * Renderers implement the transition protocol:
 *
 * 1. Observe [state] for a new [ScreensaverState.media] or [ScreensaverState.transition].
 * 2. After the media's fade-in completes, call [onMediaVisible].
 * 3. When the video/image player reports its item finished, call [onItemFinished].
 * 4. When the fade-out requested by [ScreensaverState.transition] finishes, call
 *    [onTransitionCompleted] with that request's id.
 *
 * The controller does not start fades, load media or run ExoPlayer/Coil. The players keep their
 * device-specific finish timers and report completion through step 3.
 */
class ScreensaverController(
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val config: ScreensaverConfig = ScreensaverConfig(),
    private val overlayStateStore: OverlayStateStore = OverlayStateStore(),
    private val externalTrackControl: ExternalTrackControl? = null,
    private val now: () -> LocalTime = { LocalTime.now() },
) {
    private val controllerJob = SupervisorJob(scope.coroutineContext[Job])
    private val controllerScope = CoroutineScope(scope.coroutineContext + controllerJob)

    private val _state =
        MutableStateFlow(
            ScreensaverState(loopUntilSkipped = config.loopUntilSkipped),
        )
    val state: StateFlow<ScreensaverState> = _state.asStateFlow()

    val overlayState: StateFlow<OverlayUiState> = overlayStateStore.uiState

    private var playlist: PlaylistNavigator? = null
    private var music: MusicPlayback? = null
    private var started = false
    private var released = false
    private var transitionCounter = 0L
    private var itemGeneration = 0L
    private var alternate = false
    private var startupJob: Job? = null
    private var sleepTimerJob: Job? = null
    private var scheduledBlackoutJob: Job? = null
    private var errorJob: Job? = null
    private var wasInScheduledBlackoutWindow: Boolean? = null

    fun start(
        playlist: PlaylistNavigator,
        fromCache: Boolean = false,
        music: MusicPlayback? = null,
        musicResumeIndex: Int = 0,
    ) {
        if (started || released) return
        started = true
        this.playlist = playlist
        this.music = music
        _state.update {
            it.copy(
                playlistSize = playlist.size,
                loopUntilSkipped = config.loopUntilSkipped,
            )
        }

        if (playlist.size <= 0) {
            showError(ScreensaverError.EMPTY_PLAYLIST)
            setupMusic(music, musicResumeIndex)
            return
        }

        startupJob =
            controllerScope.launch {
                if (fromCache && config.cacheResumeDelayMs > 0) {
                    delay(config.cacheResumeDelayMs)
                }
                if (released) return@launch

                if (!_state.value.blackout.active) {
                    loadNext()
                    scheduleSleepTimer()
                }
                scheduleScheduledBlackout()
                setupMusic(music, musicResumeIndex)
            }
    }

    fun start(
        playlist: MediaPlaylist,
        fromCache: Boolean = false,
        music: MusicPlayback? = null,
        musicResumeIndex: Int = 0,
    ) = start(MediaPlaylistNavigator(playlist), fromCache, music, musicResumeIndex)

    /**
     * Called by the renderer once the current item is fully visible and its fade-in has finished.
     */
    fun onMediaVisible() {
        if (released) return
        val current = _state.value
        if (current.media == null) return
        if (current.phase != ScreensaverPhase.LOADING && current.phase != ScreensaverPhase.PLAYING) return

        _state.update {
            it.copy(
                phase = ScreensaverPhase.PLAYING,
                canSkip = true,
                error = null,
            )
        }
    }

    /**
     * Called by the renderer while overlay reveal/hide animations are in flight, so user reveals
     * cannot start a second animation on top of the first.
     */
    fun onOverlaysBusy() {
        if (released) return
        _state.update {
            if (it.phase == ScreensaverPhase.LOADING || it.phase == ScreensaverPhase.PLAYING) {
                it.copy(canShowOverlays = false)
            } else {
                it
            }
        }
    }

    /** Called by the renderer when the current overlay animation state is available to reveal. */
    fun onOverlaysReady() {
        if (released) return
        _state.update {
            if (it.phase == ScreensaverPhase.LOADING || it.phase == ScreensaverPhase.PLAYING) {
                it.copy(canShowOverlays = true)
            } else {
                it
            }
        }
    }

    /**
     * Called after the renderer has completed the fade-out in [ScreensaverState.transition].
     * Passing the request id prevents a stale animation callback from advancing a newer item.
     */
    fun onTransitionCompleted(requestId: Long? = null) {
        if (released) return
        val request = _state.value.transition ?: return
        if (requestId != null && requestId != request.id) return

        _state.update { it.copy(transition = null) }

        if (_state.value.blackout.active) {
            _state.update { it.copy(phase = ScreensaverPhase.BLACKOUT) }
            return
        }

        when (request.reason) {
            TransitionReason.ITEM_FINISHED -> {
                if (_state.value.loopUntilSkipped) {
                    replayCurrentItem()
                } else {
                    loadNext()
                }
            }

            TransitionReason.USER_SKIP -> {
                loadNext(request.previous)
            }

            TransitionReason.PLAYBACK_ERROR -> {
                loadNext()
            }
        }
    }

    /** The player has reached its finish point; the renderer now fades the item out. */
    fun onItemFinished() {
        requestTransition(TransitionReason.ITEM_FINISHED)
    }

    fun skip(previous: Boolean = false) {
        requestTransition(TransitionReason.USER_SKIP, previous = previous)
    }

    fun onPlaybackError() {
        if (released) return
        val current = _state.value
        if (current.blackout.active || current.transition != null) return

        val wasVisible = current.canSkip
        _state.update {
            it.copy(
                phase = ScreensaverPhase.ERROR,
                error = ScreensaverError.PLAYBACK_ERROR,
                canShowOverlays = false,
            )
        }

        errorJob?.cancel()
        errorJob =
            controllerScope.launch {
                delay(config.errorRetryDelayMs)
                if (released || _state.value.blackout.active) return@launch
                if (_state.value.transition != null) return@launch

                if (wasVisible && _state.value.canSkip) {
                    requestTransition(TransitionReason.PLAYBACK_ERROR)
                } else {
                    loadNext()
                }
            }
    }

    /** The renderer/player has started a new track. */
    fun onMusicTrackChanged(index: Int) {
        _state.update { it.copy(music = it.music.copy(trackIndex = index)) }
    }

    /** The music player exhausted its recovery policy and is now silent. */
    fun onMusicPlaybackFailed() {
        val failed = music ?: return
        music = null
        runCatching { failed.release() }
        _state.update {
            it.copy(music = it.music.copy(mode = MusicMode.FAILED, forcedVideoMute = true))
        }
    }

    fun onNowPlaying(event: MusicEvent) {
        overlayStateStore.setNowPlaying(event)
    }

    fun nextTrack() {
        val current = music
        if (current != null && current.hasMusic()) {
            runCatching { current.nextTrack() }
            onMusicTrackChanged(current.currentTrackIndex())
        } else {
            externalTrackControl?.nextTrack()
        }
    }

    fun previousTrack() {
        val current = music
        if (current != null && current.hasMusic()) {
            runCatching { current.previousTrack() }
            onMusicTrackChanged(current.currentTrackIndex())
        } else {
            externalTrackControl?.previousTrack()
        }
    }

    fun toggleBlackout(source: BlackOutSource = BlackOutSource.USER) {
        if (released) return
        val current = _state.value
        if (playlist == null || current.playlistSize <= 0) return

        if (!current.blackout.active) {
            enterBlackout(source)
        } else if (current.blackout.source != BlackOutSource.SCHEDULED || source == BlackOutSource.SCHEDULED) {
            exitBlackout()
        }
    }

    /** Toggles looping and returns the new value so the renderer can refresh the player timer. */
    fun toggleLoopUntilSkipped(): Boolean {
        var enabled = false
        _state.update {
            enabled = !it.loopUntilSkipped
            it.copy(loopUntilSkipped = enabled)
        }
        return enabled
    }

    fun release() {
        if (released) return
        released = true

        startupJob?.cancel()
        sleepTimerJob?.cancel()
        scheduledBlackoutJob?.cancel()
        errorJob?.cancel()
        controllerJob.cancel()

        val currentMusic = music
        music = null
        if (currentMusic != null) {
            runCatching { currentMusic.pause() }
            runCatching { currentMusic.release() }
        }

        _state.update {
            it.copy(
                phase = ScreensaverPhase.STOPPED,
                canSkip = false,
                canShowOverlays = false,
                transition = null,
            )
        }
    }

    private fun loadNext(previous: Boolean = false) {
        if (released) return
        val navigator = playlist
        if (navigator == null || navigator.size <= 0) {
            showError(ScreensaverError.EMPTY_PLAYLIST)
            return
        }

        val media =
            try {
                if (previous) navigator.previousItem() else navigator.nextItem()
            } catch (e: Exception) {
                Timber.e(e, "ScreensaverController: playlist navigation failed")
                null
            }

        if (media == null) {
            showError(ScreensaverError.ITEM_UNAVAILABLE)
            return
        }

        loadMedia(media, navigator)
    }

    private fun replayCurrentItem() {
        val media = _state.value.media ?: return loadNext()
        val navigator = playlist ?: return loadNext()
        loadMedia(media, navigator)
    }

    private fun loadMedia(
        media: AerialMedia,
        navigator: PlaylistNavigator,
    ) {
        itemGeneration++
        val currentAlternate = alternate
        alternate = if (config.alternateTextPosition) !alternate else false

        overlayStateStore.resetForNextMedia()
        _state.update {
            it.copy(
                phase = ScreensaverPhase.LOADING,
                media = media,
                playlistPosition = navigator.currentPosition,
                playlistSize = navigator.size,
                itemGeneration = itemGeneration,
                canSkip = false,
                canShowOverlays = false,
                alternateOverlays = currentAlternate,
                transition = null,
                error = null,
            )
        }
    }

    private fun requestTransition(
        reason: TransitionReason,
        previous: Boolean = false,
    ) {
        if (released) return
        val current = _state.value
        if (current.media == null || current.blackout.active || current.transition != null) return
        if (!current.canSkip) return

        transitionCounter++
        _state.update {
            it.copy(
                phase = ScreensaverPhase.TRANSITIONING,
                canSkip = false,
                canShowOverlays = false,
                transition = TransitionRequest(transitionCounter, reason, previous),
            )
        }
    }

    private fun showError(error: ScreensaverError) {
        _state.update {
            it.copy(
                phase = ScreensaverPhase.ERROR,
                error = error,
                canSkip = false,
                canShowOverlays = false,
                transition = null,
            )
        }
    }

    private fun setupMusic(
        music: MusicPlayback?,
        resumeIndex: Int,
    ) {
        if (!config.backgroundMusicEnabled || music == null || !music.hasMusic()) {
            this.music = null
            _state.update { it.copy(music = MusicState()) }
            return
        }

        this.music = music
        _state.update {
            it.copy(
                music =
                    MusicState(
                        mode = if (it.blackout.active) MusicMode.PAUSED else MusicMode.PLAYING,
                        forcedVideoMute = true,
                        trackIndex = music.currentTrackIndex(),
                    ),
            )
        }

        try {
            music.load(resumeIndex)
            if (_state.value.blackout.active) {
                music.pause()
            } else {
                music.play()
            }
        } catch (e: Exception) {
            Timber.e(e, "ScreensaverController: background music failed to start")
            onMusicPlaybackFailed()
        }
    }

    private fun enterBlackout(source: BlackOutSource) {
        sleepTimerJob?.cancel()
        _state.update {
            it.copy(
                phase = ScreensaverPhase.BLACKOUT,
                blackout = BlackoutState(active = true, source = source),
                canSkip = false,
                canShowOverlays = false,
                transition = null,
            )
        }

        val currentMusic = music
        if (currentMusic != null && _state.value.music.mode == MusicMode.PLAYING) {
            runCatching { currentMusic.pause() }
            _state.update { it.copy(music = it.music.copy(mode = MusicMode.PAUSED)) }
        }
    }

    private fun exitBlackout() {
        _state.update {
            it.copy(
                blackout = BlackoutState(),
                phase = ScreensaverPhase.LOADING,
            )
        }

        loadNext()

        val currentMusic = music
        if (currentMusic != null && _state.value.music.mode == MusicMode.PAUSED) {
            runCatching { currentMusic.resume() }
            _state.update { it.copy(music = it.music.copy(mode = MusicMode.PLAYING)) }
        }

        scheduleSleepTimer()
    }

    private fun scheduleSleepTimer() {
        sleepTimerJob?.cancel()
        val minutes = config.sleepTimerMinutes
        if (minutes <= 0) return
        if (_state.value.blackout.active) return

        sleepTimerJob =
            controllerScope.launch {
                delay(minutes * 60_000)
                if (released || _state.value.blackout.active) return@launch
                enterBlackout(BlackOutSource.SLEEP_TIMER)
            }
    }

    private fun scheduleScheduledBlackout() {
        scheduledBlackoutJob?.cancel()
        wasInScheduledBlackoutWindow = null
        if (!config.scheduledBlackoutEnabled || config.scheduledBlackoutStart == null || config.scheduledBlackoutEnd == null) {
            return
        }

        scheduledBlackoutJob =
            controllerScope.launch {
                val interval = config.scheduledBlackoutCheckIntervalMs.coerceAtLeast(1)
                while (isActive) {
                    checkScheduledBlackout()
                    delay(interval)
                }
            }
    }

    private fun checkScheduledBlackout() {
        val start = config.scheduledBlackoutStart ?: return
        val end = config.scheduledBlackoutEnd ?: return
        val isNowInWindow = ScheduledBlackoutWindow.contains(start, end, now())
        val previous = wasInScheduledBlackoutWindow
        wasInScheduledBlackoutWindow = isNowInWindow

        if (previous == null) {
            if (isNowInWindow && !_state.value.blackout.active) {
                enterBlackout(BlackOutSource.SCHEDULED)
            }
            return
        }

        if (previous == isNowInWindow) return

        if (isNowInWindow && !_state.value.blackout.active) {
            enterBlackout(BlackOutSource.SCHEDULED)
        } else if (!isNowInWindow &&
            _state.value.blackout.active &&
            _state.value.blackout.source == BlackOutSource.SCHEDULED
        ) {
            exitBlackout()
        }
    }
}
