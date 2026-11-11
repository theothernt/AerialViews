package com.neilturner.aerialviews.screensaver.compose

import android.content.Context
import com.neilturner.aerialviews.data.PlaylistCacheRepository
import com.neilturner.aerialviews.models.LoadingStatus
import com.neilturner.aerialviews.models.MediaPlaylist
import com.neilturner.aerialviews.models.enums.MetadataType
import com.neilturner.aerialviews.models.enums.OverlayType
import com.neilturner.aerialviews.models.music.MusicPlaylist
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.services.KtorServer
import com.neilturner.aerialviews.services.MediaService
import com.neilturner.aerialviews.services.MusicPlayer
import com.neilturner.aerialviews.services.NowPlayingService
import com.neilturner.aerialviews.services.weather.WeatherService
import com.neilturner.aerialviews.ui.core.MusicPlayback
import com.neilturner.aerialviews.ui.core.ScreensaverConfig
import com.neilturner.aerialviews.ui.core.ScreensaverController
import com.neilturner.aerialviews.ui.overlays.state.OverlayEventBridge
import com.neilturner.aerialviews.ui.overlays.state.toState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import timber.log.Timber
import java.time.LocalTime

/**
 * The Compose path's session bootstrap.
 *
 * It fetches the same playlist the legacy controller uses, starts the UI-agnostic controller and
 * feeds the shared overlay state from the existing event bridge, metadata and service sources. The
 * controller keeps its own coroutine scope; this session's scope covers the playlist fetch,
 * metadata updates and music-position persistence.
 */
internal class ScreensaverSession(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val cacheRepository by lazy { PlaylistCacheRepository(appContext) }

    private val _loadingStatus = MutableStateFlow(LoadingStatus.LOADING)
    val loadingStatus: StateFlow<LoadingStatus> = _loadingStatus.asStateFlow()

    val controller =
        ScreensaverController(
            scope = scope,
            config =
                ScreensaverConfig(
                    sleepTimerMinutes = GeneralPrefs.sleepTimer.toLongOrNull() ?: 0L,
                    scheduledBlackoutEnabled = GeneralPrefs.scheduledBlackoutEnabled,
                    scheduledBlackoutStart = parseLocalTime(GeneralPrefs.scheduledBlackoutStart),
                    scheduledBlackoutEnd = parseLocalTime(GeneralPrefs.scheduledBlackoutEnd),
                    loopUntilSkipped = GeneralPrefs.loopUntilSkipped,
                    alternateTextPosition = GeneralPrefs.alternateTextPosition,
                    backgroundMusicEnabled = GeneralPrefs.playsBackgroundMusic,
                ),
        )

    private val overlayEventBridge = OverlayEventBridge(controller.overlayStateStore)
    private var nowPlayingService: NowPlayingService? = null
    private var weatherService: WeatherService? = null
    private var musicPlayer: MusicPlayer? = null
    private var messageApiStarted = false

    private var started = false
    private var released = false

    fun start() {
        if (started || released) return
        started = true

        overlayEventBridge.start()
        startOverlaySources()
        startMetadataUpdates()

        scope.launch {
            val result =
                try {
                    MediaService(appContext).fetchMedia { status -> _loadingStatus.value = status }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.e(e, "Compose screensaver playlist fetch failed")
                    null
                }

            if (released) return@launch

            if (result == null) {
                controller.start(MediaPlaylist(emptyList()))
            } else {
                controller.start(
                    playlist = result.mediaPlaylist,
                    fromCache = result.isFromCache,
                    music = createMusicPlayback(result.musicPlaylist),
                    musicResumeIndex = result.musicResumeIndex,
                )
            }
        }
    }

    private fun createMusicPlayback(musicPlaylist: MusicPlaylist?): MusicPlayback? {
        if (!GeneralPrefs.playsBackgroundMusic || musicPlaylist == null || musicPlaylist.size == 0) {
            Timber.i("MusicPlayer: no background music playlist available, skipping")
            return null
        }

        val player = MusicPlayer(appContext, musicPlaylist)
        player.onMediaItemChanged = {
            val index = player.getCurrentTrackIndex()
            controller.onMusicTrackChanged(index)
            saveMusicTrackIndex(index)
        }
        player.onPlaybackFailed = {
            controller.onMusicPlaybackFailed()
            musicPlayer = null
        }
        musicPlayer = player
        return ComposeMusicPlayback(player)
    }

    private fun saveMusicTrackIndex(index: Int) {
        if (!GeneralPrefs.playlistCache) return
        scope.launch(Dispatchers.IO) {
            runCatching { cacheRepository.saveMusicTrackIndex(index) }
                .onFailure { Timber.e(it, "Compose screensaver could not save music position") }
        }
    }

    private fun startOverlaySources() {
        val slots =
            setOfNotNull(
                GeneralPrefs.slotBottomLeft1,
                GeneralPrefs.slotBottomLeft2,
                GeneralPrefs.slotBottomRight1,
                GeneralPrefs.slotBottomRight2,
                GeneralPrefs.slotTopLeft1,
                GeneralPrefs.slotTopLeft2,
                GeneralPrefs.slotTopRight1,
                GeneralPrefs.slotTopRight2,
            )

        if (slots.any { it == OverlayType.MUSIC1 || it == OverlayType.MUSIC2 }) {
            nowPlayingService = NowPlayingService(appContext)
        }

        val hasWeatherNow = OverlayType.WEATHER1 in slots
        val hasForecast = OverlayType.WEATHER2 in slots
        if (hasWeatherNow || hasForecast) {
            weatherService =
                WeatherService(appContext).apply {
                    startUpdates(
                        fetchCurrentWeather = hasWeatherNow,
                        fetchForecast = hasForecast,
                    )
                }
        }

        val hasMessage =
            slots.any {
                it == OverlayType.MESSAGE1 ||
                    it == OverlayType.MESSAGE2 ||
                    it == OverlayType.MESSAGE3 ||
                    it == OverlayType.MESSAGE4
            }
        if (hasMessage && GeneralPrefs.messageApiEnabled) {
            KtorServer.start(appContext) { event ->
                controller.overlayStateStore.setMessage(event.type, event.toState())
            }
            messageApiStarted = true
        }
    }

    private fun startMetadataUpdates() {
        val metadataSlots =
            listOf(
                OverlayType.METADATA1,
                OverlayType.METADATA2,
                OverlayType.METADATA3,
                OverlayType.METADATA4,
            )

        scope.launch {
            controller.state
                .map { it.itemGeneration to it.media }
                .distinctUntilChanged()
                .collect { (_, media) ->
                    val metadata = media?.metadata ?: return@collect
                    val pointsOfInterest = metadata.pointsOfInterest
                    val metadataType =
                        if (pointsOfInterest.size > 1) MetadataType.DYNAMIC else MetadataType.STATIC
                    metadataSlots.forEach { slot ->
                        controller.overlayStateStore.setMetadata(
                            type = slot,
                            text = metadata.shortDescription,
                            poi = pointsOfInterest,
                            metadataType = metadataType,
                        )
                    }
                }
        }
    }

    fun release() {
        if (released) return
        released = true
        started = true

        // Preserve the legacy cache behavior while the player is still alive.
        val musicIndex = musicPlayer?.getCurrentTrackIndex() ?: 0
        if (GeneralPrefs.playlistCache) {
            runCatching {
                runBlocking(Dispatchers.IO) {
                    cacheRepository.saveMusicTrackIndex(musicIndex)
                }
            }.onFailure { Timber.e(it, "Compose screensaver could not save music position on release") }
        }

        scope.cancel()
        overlayEventBridge.stop()
        nowPlayingService?.stop()
        nowPlayingService = null
        weatherService?.stop()
        weatherService = null
        if (messageApiStarted) {
            KtorServer.stop()
            messageApiStarted = false
        }
        controller.release()
        musicPlayer = null
    }
}

private fun parseLocalTime(timeStr: String): LocalTime? =
    try {
        val parts = timeStr.split(":")
        if (parts.size == 2) {
            LocalTime.of(parts[0].trim().toInt(), parts[1].trim().toInt())
        } else {
            null
        }
    } catch (e: Exception) {
        Timber.w(e, "Invalid scheduled blackout time: $timeStr")
        null
    }
