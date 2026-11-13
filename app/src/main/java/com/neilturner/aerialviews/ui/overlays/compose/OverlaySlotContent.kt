package com.neilturner.aerialviews.ui.overlays.compose

import androidx.compose.runtime.Composable
import com.neilturner.aerialviews.models.enums.OverlayType
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.ui.overlays.state.MetadataOverlayState
import com.neilturner.aerialviews.ui.overlays.state.OverlayUiState

/**
 * Renders one slot's overlay.
 *
 * The slot decides which preferences apply: every overlay type has its own size and weight, so
 * the lookup is by type here rather than inside each leaf composable.
 */
@Composable
fun OverlaySlotContent(
    type: OverlayType,
    state: OverlayUiState,
    videoPosition: () -> Long,
) {
    when (type) {
        OverlayType.EMPTY -> {
            // Nothing is assigned to this slot
        }

        OverlayType.CLOCK -> {
            ClockOverlay(GeneralPrefs.clockSize.toFloat(), GeneralPrefs.clockWeight)
        }

        OverlayType.DATE -> {
            DateOverlay(GeneralPrefs.dateSize.toFloat(), GeneralPrefs.dateWeight)
        }

        OverlayType.METADATA1 -> {
            MetadataOverlay(
                state = state.metadata[type] ?: MetadataOverlayState(),
                sizeSp = GeneralPrefs.overlayMetadata1Size.toFloat(),
                weight = GeneralPrefs.overlayMetadata1Weight,
                videoPosition = videoPosition,
                fadingOutMedia = state.fadingOutMedia,
            )
        }

        OverlayType.METADATA2 -> {
            MetadataOverlay(
                state = state.metadata[type] ?: MetadataOverlayState(),
                sizeSp = GeneralPrefs.overlayMetadata2Size.toFloat(),
                weight = GeneralPrefs.overlayMetadata2Weight,
                videoPosition = videoPosition,
                fadingOutMedia = state.fadingOutMedia,
            )
        }

        OverlayType.METADATA3 -> {
            MetadataOverlay(
                state = state.metadata[type] ?: MetadataOverlayState(),
                sizeSp = GeneralPrefs.overlayMetadata3Size.toFloat(),
                weight = GeneralPrefs.overlayMetadata3Weight,
                videoPosition = videoPosition,
                fadingOutMedia = state.fadingOutMedia,
            )
        }

        OverlayType.METADATA4 -> {
            MetadataOverlay(
                state = state.metadata[type] ?: MetadataOverlayState(),
                sizeSp = GeneralPrefs.overlayMetadata4Size.toFloat(),
                weight = GeneralPrefs.overlayMetadata4Weight,
                videoPosition = videoPosition,
                fadingOutMedia = state.fadingOutMedia,
            )
        }

        OverlayType.MESSAGE1,
        OverlayType.MESSAGE2,
        OverlayType.MESSAGE3,
        OverlayType.MESSAGE4,
        -> {
            // The slot's configured text, as OverlayHelper gives each View overlay.
            val configured =
                when (type) {
                    OverlayType.MESSAGE1 -> GeneralPrefs.messageLine1
                    OverlayType.MESSAGE2 -> GeneralPrefs.messageLine2
                    OverlayType.MESSAGE3 -> GeneralPrefs.messageLine3
                    else -> GeneralPrefs.messageLine4
                }
            MessageOverlay(state = state.message[type], fallbackText = configured)
        }

        OverlayType.COUNTDOWN -> {
            CountdownOverlay(
                sizeSp = GeneralPrefs.countdownSize.toFloat(),
                weight = GeneralPrefs.countdownWeight,
            )
        }

        OverlayType.MUSIC1 -> {
            NowPlayingOverlay(
                state = state.nowPlaying,
                format = GeneralPrefs.nowPlayingLine1,
                sizeSp = GeneralPrefs.nowPlayingSize1.toFloat(),
                weight = GeneralPrefs.nowPlayingWeight1,
            )
        }

        OverlayType.MUSIC2 -> {
            NowPlayingOverlay(
                state = state.nowPlaying,
                format = GeneralPrefs.nowPlayingLine2,
                sizeSp = GeneralPrefs.nowPlayingSize2.toFloat(),
                weight = GeneralPrefs.nowPlayingWeight2,
            )
        }

        OverlayType.WEATHER1 -> {
            WeatherNowOverlay(
                state = state.weather,
                layout = GeneralPrefs.weatherLine1Layout,
                sizeSp = GeneralPrefs.weatherLine1Size.toFloat(),
                weight = GeneralPrefs.weatherLine1Weight,
            )
        }

        OverlayType.WEATHER2 -> {
            WeatherForecastOverlay(
                state = state.forecast,
                sizeSp = GeneralPrefs.weatherLine2Size.toFloat(),
                weight = GeneralPrefs.weatherLine2Weight,
            )
        }
    }
}
