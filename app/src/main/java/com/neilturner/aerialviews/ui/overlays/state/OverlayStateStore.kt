package com.neilturner.aerialviews.ui.overlays.state

import com.neilturner.aerialviews.models.enums.MetadataType
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

    fun setMetadata(
        type: OverlayType,
        text: String,
        poi: Map<Int, String>,
        metadataType: MetadataType,
    ) {
        _uiState.update {
            it.copy(
                metadata =
                    it.metadata.toMutableMap().apply {
                        put(
                            type,
                            MetadataOverlayState(
                                text = text,
                                poi = poi,
                                metadataType = metadataType,
                            ),
                        )
                    },
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
                metadata = emptyMap(),
                progress = ProgressOverlayState(),
            )
        }
    }
}
