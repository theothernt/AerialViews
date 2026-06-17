package com.neilturner.aerialviews.ui.overlays.compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.neilturner.aerialviews.models.enums.NowPlayingFormat
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.services.MusicEvent
import com.neilturner.aerialviews.ui.overlays.state.NowPlayingOverlayState
import com.neilturner.aerialviews.ui.overlays.utils.TrackNameShortener

@Composable
fun NowPlayingOverlayComposable(
    state: NowPlayingOverlayState,
    format: NowPlayingFormat,
    modifier: Modifier = Modifier,
) {
    val trackInfo = state.event
    val text = formatNowPlaying(trackInfo, format)
    val isVisible = text.isNotBlank()

    AnimatedVisibility(
        visible = isVisible,
        modifier = modifier,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        Text(
            text = text,
            style = OverlayTextStyle,
        )
    }
}

private fun formatNowPlaying(
    trackInfo: MusicEvent,
    format: NowPlayingFormat,
): String {
    val (artist, song) = trackInfo
    val processedSong = if (GeneralPrefs.nowPlayingShortenTrackName) {
        TrackNameShortener.shortenTrackName(song)
    } else {
        song
    }

    return when (format) {
        NowPlayingFormat.SONG_ARTIST -> {
            if (processedSong.isNotBlank() && artist.isNotBlank()) {
                "$processedSong · $artist"
            } else {
                processedSong.takeIf { it.isNotBlank() } ?: artist
            }
        }

        NowPlayingFormat.ARTIST_SONG -> {
            if (artist.isNotBlank() && processedSong.isNotBlank()) {
                "$artist · $processedSong"
            } else {
                artist.takeIf { it.isNotBlank() } ?: processedSong
            }
        }

        NowPlayingFormat.ARTIST -> artist
        NowPlayingFormat.SONG -> processedSong
        else -> ""
    }
}
