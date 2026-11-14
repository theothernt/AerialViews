package com.neilturner.aerialviews.ui.overlays.compose

import android.graphics.Bitmap
import android.util.TypedValue
import android.widget.TextView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.neilturner.aerialviews.models.enums.NowPlayingFormat
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.ui.helpers.FontHelper
import com.neilturner.aerialviews.ui.overlays.compose.components.OverlayText
import com.neilturner.aerialviews.ui.overlays.state.NowPlayingOverlayState
import com.neilturner.aerialviews.ui.overlays.utils.NowPlayingFormatter

internal const val NOW_PLAYING_FADE_MILLIS = 300
private const val ALBUM_ART_SCALE = 2.5f
private val albumArtGap = 12.dp

/** The line and its art as one unit, so a track change swaps both at the same moment. */
private data class NowPlayingContent(
    val text: String,
    val albumArt: Bitmap?,
)

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
    fade: OverlayFade = remember { OverlayFade(NOW_PLAYING_FADE_MILLIS) },
) {
    val event = state.event
    val target =
        remember(event, format) {
            NowPlayingContent(
                text =
                    NowPlayingFormatter.format(
                        artist = event.artist,
                        song = event.song,
                        format = format,
                        shortenTrackName = GeneralPrefs.nowPlayingShortenTrackName,
                    ),
                albumArt = event.albumArt,
            )
        }

    var displayed by remember { mutableStateOf(target) }

    // The View version fades the line out, swaps track and art while it is invisible, then fades it
    // back in - 300ms each way, not a swap at full brightness.
    LaunchedEffect(target) {
        if (target == displayed) return@LaunchedEffect
        // Nothing on screen to fade: the View only fades out a line that is still visible, and
        // snaps to alpha 0 while it is gone, so appearing from blank must not spend 300ms fading
        // out an empty line first - which is every track change, since the blank event arrives
        // before the new one.
        if (displayed.text.isNotBlank()) fade.fadeOut() else fade.snapHidden()
        displayed = target
        if (target.text.isNotBlank()) fade.fadeIn()
    }

    val density = LocalDensity.current
    val context = LocalContext.current

    // Same calculation as calculateIconSize() in the View version: the font's ascent-to-descent
    // span at the full text size. A Compose paragraph's height is a few px larger, which made the
    // art (and the whole line) taller than the View version.
    val albumArtSize =
        remember(context, sizeSp, weight, density) {
            val paint =
                TextView(context)
                    .apply {
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
                        typeface = FontHelper.getTypeface(context, GeneralPrefs.fontTypeface, weight)
                    }.paint
            val metrics = paint.fontMetrics
            val textHeightPx = metrics.descent - metrics.ascent
            with(density) { (textHeightPx * ALBUM_ART_SCALE).toInt().toDp() }
        }
    val albumArt = displayed.albumArt

    AnimatedVisibility(
        visible = displayed.text.isNotBlank(),
        enter = fadeIn(tween(durationMillis = NOW_PLAYING_FADE_MILLIS)),
        exit = fadeOut(tween(durationMillis = NOW_PLAYING_FADE_MILLIS)),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.alpha(fade.alpha.value),
        ) {
            if (albumArt != null) {
                Image(
                    bitmap = albumArt.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(albumArtSize),
                )
            }

            OverlayText(
                text = displayed.text,
                sizeSp = sizeSp,
                weight = weight,
                modifier = if (albumArt != null) Modifier.padding(start = albumArtGap) else Modifier,
            )
        }
    }
}
