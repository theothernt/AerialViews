package com.neilturner.aerialviews.ui.overlays.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.neilturner.aerialviews.models.enums.DateType
import com.neilturner.aerialviews.models.enums.OverlayType
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.ui.overlays.state.OverlayUiState

@Composable
fun OverlayLayout(
    state: OverlayUiState,
    modifier: Modifier = Modifier,
) {
    val prefs = GeneralPrefs

    val bottomLeft1 = prefs.slotBottomLeft1 ?: OverlayType.EMPTY
    val bottomLeft2 = prefs.slotBottomLeft2 ?: OverlayType.EMPTY
    val bottomRight1 = prefs.slotBottomRight1 ?: OverlayType.EMPTY
    val bottomRight2 = prefs.slotBottomRight2 ?: OverlayType.EMPTY
    val topLeft1 = prefs.slotTopLeft1 ?: OverlayType.EMPTY
    val topLeft2 = prefs.slotTopLeft2 ?: OverlayType.EMPTY
    val topRight1 = prefs.slotTopRight1 ?: OverlayType.EMPTY
    val topRight2 = prefs.slotTopRight2 ?: OverlayType.EMPTY

    Box(modifier = modifier) {
        // Bottom Left
        if (bottomLeft1 != OverlayType.EMPTY || bottomLeft2 != OverlayType.EMPTY) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 32.dp, bottom = 26.dp),
            ) {
                if (bottomLeft2 != OverlayType.EMPTY) {
                    OverlayContent(type = bottomLeft2, state = state)
                }
                if (bottomLeft1 != OverlayType.EMPTY) {
                    OverlayContent(type = bottomLeft1, state = state)
                }
            }
        }

        // Bottom Right
        if (bottomRight1 != OverlayType.EMPTY || bottomRight2 != OverlayType.EMPTY) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 32.dp, bottom = 26.dp),
                horizontalAlignment = Alignment.End,
            ) {
                if (bottomRight2 != OverlayType.EMPTY) {
                    OverlayContent(type = bottomRight2, state = state)
                }
                if (bottomRight1 != OverlayType.EMPTY) {
                    OverlayContent(type = bottomRight1, state = state)
                }
            }
        }

        // Top Left
        if (topLeft1 != OverlayType.EMPTY || topLeft2 != OverlayType.EMPTY) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 32.dp, top = 26.dp),
            ) {
                if (topLeft2 != OverlayType.EMPTY) {
                    OverlayContent(type = topLeft2, state = state)
                }
                if (topLeft1 != OverlayType.EMPTY) {
                    OverlayContent(type = topLeft1, state = state)
                }
            }
        }

        // Top Right
        if (topRight1 != OverlayType.EMPTY || topRight2 != OverlayType.EMPTY) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 32.dp, top = 26.dp),
                horizontalAlignment = Alignment.End,
            ) {
                if (topRight2 != OverlayType.EMPTY) {
                    OverlayContent(type = topRight2, state = state)
                }
                if (topRight1 != OverlayType.EMPTY) {
                    OverlayContent(type = topRight1, state = state)
                }
            }
        }
    }
}

@Composable
private fun OverlayContent(
    type: OverlayType,
    state: OverlayUiState,
) {
    when (type) {
        OverlayType.CLOCK -> ClockOverlayComposable(
            format = GeneralPrefs.clockFormat,
        )

        OverlayType.DATE -> DateOverlayComposable(
            context = LocalContext.current,
            type = GeneralPrefs.dateFormat ?: DateType.COMPACT,
            custom = GeneralPrefs.dateCustom,
        )

        OverlayType.METADATA1,
        OverlayType.METADATA2,
        OverlayType.METADATA3,
        OverlayType.METADATA4 -> {
            val metadataState = state.metadata[type]
            if (metadataState != null) {
                MetadataOverlayComposable(
                    state = metadataState,
                    currentPositionMs = 0L,
                )
            }
        }

        OverlayType.WEATHER1 -> WeatherNowOverlayComposable(
            state = state.weather,
            layout = GeneralPrefs.weatherLine1Layout,
        )

        OverlayType.WEATHER2 -> WeatherForecastOverlayComposable(
            state = state.forecast,
        )

        OverlayType.MUSIC1 -> NowPlayingOverlayComposable(
            state = state.nowPlaying,
            format = GeneralPrefs.nowPlayingLine1
                ?: com.neilturner.aerialviews.models.enums.NowPlayingFormat.SONG_ARTIST,
        )

        OverlayType.MUSIC2 -> NowPlayingOverlayComposable(
            state = state.nowPlaying,
            format = GeneralPrefs.nowPlayingLine2
                ?: com.neilturner.aerialviews.models.enums.NowPlayingFormat.DISABLED,
        )

        OverlayType.MESSAGE1,
        OverlayType.MESSAGE2,
        OverlayType.MESSAGE3,
        OverlayType.MESSAGE4 -> {
            val messageState = state.message[type]
            if (messageState != null) {
                MessageOverlayComposable(state = messageState)
            }
        }

        OverlayType.COUNTDOWN -> CountdownOverlayComposable()

        else -> { /* EMPTY or unknown — render nothing */ }
    }
}
