@file:Suppress("ktlint:standard:function-naming")

package com.neilturner.aerialviews.screensaver.compose

import android.content.res.ColorStateList
import android.widget.ProgressBar
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.neilturner.aerialviews.models.LoadingStatus
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.ui.core.ScreensaverError
import com.neilturner.aerialviews.ui.core.ScreensaverPhase
import com.neilturner.aerialviews.ui.core.ScreensaverState

private const val LOADING_HORIZONTAL_PADDING_DP = 32
private const val LOADING_BOTTOM_PADDING_DP = 26
private const val LOADING_SPINNER_SIZE_DP = 24
private const val LOADING_SPINNER_GAP_DP = 12
private const val DEFAULT_LOADING_TEXT_SIZE_SP = 18f
private const val DEFAULT_LOADING_TEXT_WEIGHT = 300

/**
 * The legacy loading view's status content. It sits inside the loading-coloured layer that fades
 * with the media, so an empty playlist or a provider failure remains readable instead of leaving a
 * blank screen.
 */
@Composable
internal fun ComposeStatusContent(
    state: ScreensaverState,
    loadingStatus: LoadingStatus,
) {
    if (state.blackout.active || !GeneralPrefs.showLoadingText) return

    val context = LocalContext.current
    val error = state.error
    val text =
        when {
            error == ScreensaverError.EMPTY_PLAYLIST ||
                error == ScreensaverError.ITEM_UNAVAILABLE -> {
                context.getString(R.string.loading_error)
            }

            error == ScreensaverError.PLAYBACK_ERROR -> {
                context.getString(R.string.playback_error)
            }

            state.phase == ScreensaverPhase.IDLE || state.phase == ScreensaverPhase.LOADING -> {
                when (loadingStatus) {
                    LoadingStatus.RESUMING -> context.getString(R.string.loading_resuming)
                    LoadingStatus.BUILDING -> context.getString(R.string.loading_building)
                    LoadingStatus.LOADING -> context.getString(R.string.loading_title)
                }
            }

            else -> {
                return
            }
        }

    Box(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier =
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(
                        start = LOADING_HORIZONTAL_PADDING_DP.dp,
                        end = LOADING_HORIZONTAL_PADDING_DP.dp,
                        bottom = LOADING_BOTTOM_PADDING_DP.dp,
                    ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(LOADING_SPINNER_GAP_DP.dp),
        ) {
            if (error == null) {
                LoadingSpinner()
            }
            BasicText(
                text = text,
                style =
                    TextStyle(
                        color = Color.White,
                        fontSize =
                            (GeneralPrefs.loadingTextSize.toFloatOrNull() ?: DEFAULT_LOADING_TEXT_SIZE_SP)
                                .sp,
                        fontWeight =
                            ScreensaverFonts.weight(
                                GeneralPrefs.loadingTextWeight.toIntOrNull() ?: DEFAULT_LOADING_TEXT_WEIGHT,
                            ),
                        fontFamily = ScreensaverFonts.family(GeneralPrefs.fontTypeface),
                        shadow = Shadow(Color.Black, Offset(1f, 1f), blurRadius = 1f),
                    ),
            )
        }
    }
}

@Composable
private fun LoadingSpinner() {
    AndroidView(
        modifier = Modifier.size(LOADING_SPINNER_SIZE_DP.dp),
        factory = { context ->
            ProgressBar(context).apply {
                isIndeterminate = true
                indeterminateTintList = ColorStateList.valueOf(android.graphics.Color.WHITE)
            }
        },
    )
}
