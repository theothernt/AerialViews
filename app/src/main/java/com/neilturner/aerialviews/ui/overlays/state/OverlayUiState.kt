package com.neilturner.aerialviews.ui.overlays.state

import com.neilturner.aerialviews.models.enums.MetadataType
import com.neilturner.aerialviews.models.enums.OverlayType
import com.neilturner.aerialviews.services.MessageEvent
import com.neilturner.aerialviews.services.MusicEvent
import com.neilturner.aerialviews.services.weather.WeatherEvent
import com.neilturner.aerialviews.ui.overlays.ProgressState

data class OverlayUiState(
    val location: MetadataOverlayState = MetadataOverlayState(),
    val location2: MetadataOverlayState = MetadataOverlayState(),
    val message: Map<OverlayType, MessageOverlayState> = emptyMap(),
    val nowPlaying: NowPlayingOverlayState = NowPlayingOverlayState(),
    val weather: WeatherOverlayState = WeatherOverlayState(),
    val progress: ProgressOverlayState = ProgressOverlayState(),
)

data class MetadataOverlayState(
    val text: String = "",
    val poi: Map<Int, String> = emptyMap(),
    val metadataType: MetadataType = MetadataType.STATIC,
)

data class MessageOverlayState(
    val text: String = "",
    val duration: Int? = null,
    val textSize: Int? = null,
    val textWeight: Int? = null,
)

data class NowPlayingOverlayState(
    val event: MusicEvent = MusicEvent(),
)

data class WeatherOverlayState(
    val event: WeatherEvent = WeatherEvent(),
)

data class ProgressOverlayState(
    val state: ProgressState = ProgressState.RESET,
    val position: Long = 0,
    val duration: Long = 0,
)

fun MessageEvent.toState(): MessageOverlayState =
    MessageOverlayState(
        text = text,
        duration = duration,
        textSize = textSize,
        textWeight = textWeight,
    )
