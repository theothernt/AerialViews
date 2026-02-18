package com.neilturner.aerialviews.ui.overlays.state

import com.neilturner.aerialviews.models.enums.DescriptionManifestType
import com.neilturner.aerialviews.models.enums.OverlayType
import com.neilturner.aerialviews.services.MusicEvent
import com.neilturner.aerialviews.services.weather.WeatherEvent
import com.neilturner.aerialviews.ui.overlays.ProgressState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class OverlayStateStore {
    private val _uiState = MutableStateFlow(OverlayUiState())
    val uiState: StateFlow<OverlayUiState> = _uiState.asStateFlow()

    fun setLocation(
        text: String,
        poi: Map<Int, String>,
        descriptionManifestType: DescriptionManifestType,
    ) {
        _uiState.update {
            it.copy(
                location =
                    MetadataOverlayState(
                        text = text,
                        poi = poi,
                        descriptionManifestType = descriptionManifestType,
                    ),
            )
        }
    }

    fun setMessage(
        type: OverlayType,
        state: MessageOverlayState,
    ) {
        _uiState.update {
            it.copy(message = it.message.toMutableMap().apply { put(type, state) })
        }
    }

    fun setNowPlaying(event: MusicEvent) {
        _uiState.update { it.copy(nowPlaying = NowPlayingOverlayState(event)) }
    }

    fun setWeather(event: WeatherEvent) {
        _uiState.update { it.copy(weather = WeatherOverlayState(event)) }
    }

    fun setProgress(
        state: ProgressState,
        position: Long = 0,
        duration: Long = 0,
    ) {
        _uiState.update {
            it.copy(
                progress =
                    ProgressOverlayState(
                        state = state,
                        position = position,
                        duration = duration,
                    ),
            )
        }
    }

    fun resetForNextMedia() {
        _uiState.update {
            it.copy(
                location = MetadataOverlayState(),
                progress = ProgressOverlayState(),
            )
        }
    }
}
