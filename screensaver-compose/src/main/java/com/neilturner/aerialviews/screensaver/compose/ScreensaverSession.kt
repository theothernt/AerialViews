package com.neilturner.aerialviews.screensaver.compose

import android.content.Context
import com.neilturner.aerialviews.models.MediaPlaylist
import com.neilturner.aerialviews.models.enums.MetadataType
import com.neilturner.aerialviews.models.enums.OverlayType
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.services.KtorServer
import com.neilturner.aerialviews.services.MediaService
import com.neilturner.aerialviews.services.NowPlayingService
import com.neilturner.aerialviews.services.weather.WeatherService
import com.neilturner.aerialviews.ui.core.ScreensaverConfig
import com.neilturner.aerialviews.ui.core.ScreensaverController
import com.neilturner.aerialviews.ui.overlays.state.OverlayEventBridge
import com.neilturner.aerialviews.ui.overlays.state.toState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * The Compose path's session bootstrap.
 *
 * It fetches the same playlist the legacy controller uses, starts the UI-agnostic controller and
 * feeds the shared overlay state from the existing event bridge, metadata and service sources. The
 * controller keeps its own coroutine scope; this session's scope covers the playlist fetch and
 * metadata updates. It deliberately uses neither the ViewModel nor a host lifecycle scope.
 */
internal class ScreensaverSession(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val controller =
        ScreensaverController(
            scope = scope,
            config =
                ScreensaverConfig(
                    loopUntilSkipped = GeneralPrefs.loopUntilSkipped,
                    alternateTextPosition = GeneralPrefs.alternateTextPosition,
                ),
        )

    private val overlayEventBridge = OverlayEventBridge(controller.overlayStateStore)
    private var nowPlayingService: NowPlayingService? = null
    private var weatherService: WeatherService? = null
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
                    MediaService(appContext).fetchMedia()
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
                )
            }
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
    }
}
