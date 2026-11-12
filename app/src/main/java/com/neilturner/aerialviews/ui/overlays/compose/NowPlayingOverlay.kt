package com.neilturner.aerialviews.ui.overlays.compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.neilturner.aerialviews.models.enums.NowPlayingFormat
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.ui.overlays.compose.components.OverlayText
import com.neilturner.aerialviews.ui.overlays.compose.components.overlayTextStyle
import com.neilturner.aerialviews.ui.overlays.state.NowPlayingOverlayState
import com.neilturner.aerialviews.ui.overlays.utils.NowPlayingFormatter

private const val NOW_PLAYING_FADE_MILLIS = 300
private const val ALBUM_ART_SCALE = 2.5f
private val albumArtGap = 12.dp

/**
 * Now playing line, with optional album art sized relative to the text so it scales with the
 * font size the user picked.
 */
@Composable
fun NowPlayingOverlay(
    state: NowPlayingOverlayState,
    format: NowPlayingFormat?,
    sizeSp: Float,
    weight: String,
) {
    val event = state.event
    val text =
        remember(event, format) {
            NowPlayingFormatter.format(
                artist = event.artist,
                song = event.song,
                format = format,
                shortenTrackName = GeneralPrefs.nowPlayingShortenTrackName,
            )
        }

    val style = overlayTextStyle(sizeSp, weight)
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val albumArtSize =
        with(density) {
            measurer
                .measure(AnnotatedString("Ag"), style)
                .size.height
                .toFloat()
                .toDp() * ALBUM_ART_SCALE
        }
    val albumArt = event.albumArt

    AnimatedVisibility(
        visible = text.isNotBlank(),
        enter = fadeIn(tween(durationMillis = NOW_PLAYING_FADE_MILLIS)),
        exit = fadeOut(tween(durationMillis = NOW_PLAYING_FADE_MILLIS)),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (albumArt != null) {
                Image(
                    bitmap = albumArt.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(albumArtSize),
                )
            }

            OverlayText(
                text = text,
                sizeSp = sizeSp,
                weight = weight,
                modifier = if (albumArt != null) Modifier.padding(start = albumArtGap) else Modifier,
            )
        }
    }
}
