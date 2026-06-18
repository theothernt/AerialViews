package com.neilturner.aerialviews.ui.overlays.compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.neilturner.aerialviews.models.enums.DateType
import com.neilturner.aerialviews.models.enums.OverlayType
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.ui.overlays.state.OverlayUiState
import kotlinx.coroutines.delay
import kotlin.math.pow

@Composable
fun OverlayLayout(
    state: OverlayUiState,
    loadingComplete: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val prefs = GeneralPrefs
    val autoHideValue = prefs.overlayAutoHide.toLongOrNull() ?: -1L
    val revealTimeout = prefs.overlayRevealTimeout.toLongOrNull() ?: 4L
    val fadeDuration = prefs.overlayFadeInDuration.toLongOrNull() ?: 600L
    val fadeOutDuration = prefs.overlayFadeOutDuration.toLongOrNull() ?: 600L

    val bottomLeft1 = prefs.slotBottomLeft1 ?: OverlayType.EMPTY
    val bottomLeft2 = prefs.slotBottomLeft2 ?: OverlayType.EMPTY
    val bottomRight1 = prefs.slotBottomRight1 ?: OverlayType.EMPTY
    val bottomRight2 = prefs.slotBottomRight2 ?: OverlayType.EMPTY
    val topLeft1 = prefs.slotTopLeft1 ?: OverlayType.EMPTY
    val topLeft2 = prefs.slotTopLeft2 ?: OverlayType.EMPTY
    val topRight1 = prefs.slotTopRight1 ?: OverlayType.EMPTY
    val topRight2 = prefs.slotTopRight2 ?: OverlayType.EMPTY

    val hasBottomLeft = bottomLeft1 != OverlayType.EMPTY || bottomLeft2 != OverlayType.EMPTY
    val hasBottomRight = bottomRight1 != OverlayType.EMPTY || bottomRight2 != OverlayType.EMPTY
    val hasTopLeft = topLeft1 != OverlayType.EMPTY || topLeft2 != OverlayType.EMPTY
    val hasTopRight = topRight1 != OverlayType.EMPTY || topRight2 != OverlayType.EMPTY

    val hasAnyOverlay = hasBottomLeft || hasBottomRight || hasTopLeft || hasTopRight

    // Determine which corners fade based on fade corners selection
    val fadeCorners = prefs.overlayFadeCornersSelection
    val bottomLeftFades = fadeCorners.contains("BOTTOM_LEFT") && hasBottomLeft
    val bottomRightFades = fadeCorners.contains("BOTTOM_RIGHT") && hasBottomRight
    val topLeftFades = fadeCorners.contains("TOP_LEFT") && hasTopLeft
    val topRightFades = fadeCorners.contains("TOP_RIGHT") && hasTopRight
    val bottomFades = bottomLeftFades || bottomRightFades
    val topFades = topLeftFades || topRightFades

    // Visibility state for each region - start hidden until loading completes
    var bottomLeftVisible by remember { mutableStateOf(false) }
    var bottomRightVisible by remember { mutableStateOf(false) }
    var topLeftVisible by remember { mutableStateOf(false) }
    var topRightVisible by remember { mutableStateOf(false) }
    var canShowOverlays by remember { mutableStateOf(false) }

    // Show overlays once loading completes, then apply auto-hide logic
    LaunchedEffect(loadingComplete, autoHideValue) {
        if (!loadingComplete) {
            bottomLeftVisible = false
            bottomRightVisible = false
            topLeftVisible = false
            topRightVisible = false
            return@LaunchedEffect
        }

        when {
            autoHideValue == -1L -> {
                // Always visible
                bottomLeftVisible = true
                bottomRightVisible = true
                topLeftVisible = true
                topRightVisible = true
                canShowOverlays = true
            }
            autoHideValue == 0L -> {
                // Always hidden
                bottomLeftVisible = false
                bottomRightVisible = false
                topLeftVisible = false
                topRightVisible = false
                canShowOverlays = true
            }
            autoHideValue > 0L -> {
                // Show initially, hide after delay
                bottomLeftVisible = true
                bottomRightVisible = true
                topLeftVisible = true
                topRightVisible = true
                delay(autoHideValue * 1000)
                // Hide after delay (only corners that fade)
                bottomLeftVisible = !bottomLeftFades
                bottomRightVisible = !bottomRightFades
                topLeftVisible = !topLeftFades
                topRightVisible = !topRightFades
                canShowOverlays = true
            }
        }
    }

    // Gradient visibility
    val showTopGradient = prefs.showTopGradient
    val showBottomGradient = prefs.showBottomGradient
    var topGradientVisible by remember { mutableStateOf(false) }
    var bottomGradientVisible by remember { mutableStateOf(false) }

    LaunchedEffect(topLeftVisible, topRightVisible) {
        topGradientVisible = showTopGradient && (topLeftVisible || topRightVisible)
    }
    LaunchedEffect(bottomLeftVisible, bottomRightVisible) {
        bottomGradientVisible = showBottomGradient && (bottomLeftVisible || bottomRightVisible)
    }

    Box(modifier = modifier) {
        // Bottom gradient
        if (showBottomGradient) {
            AnimatedVisibility(
                visible = bottomGradientVisible,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(200.dp),
                enter = fadeIn(animationSpec = androidx.compose.animation.core.tween(fadeDuration.toInt())),
                exit = fadeOut(animationSpec = androidx.compose.animation.core.tween(fadeOutDuration.toInt())),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = easeInGradientColors().reversed(),
                            ),
                        ),
                )
            }
        }

        // Top gradient
        if (showTopGradient) {
            AnimatedVisibility(
                visible = topGradientVisible,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(200.dp),
                enter = fadeIn(animationSpec = androidx.compose.animation.core.tween(fadeDuration.toInt())),
                exit = fadeOut(animationSpec = androidx.compose.animation.core.tween(fadeOutDuration.toInt())),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = easeInGradientColors(),
                            ),
                        ),
                )
            }
        }

        // Bottom Left
        if (hasBottomLeft) {
            AnimatedVisibility(
                visible = bottomLeftVisible,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 32.dp, bottom = 26.dp),
                enter = fadeIn(animationSpec = androidx.compose.animation.core.tween(fadeDuration.toInt())),
                exit = fadeOut(animationSpec = androidx.compose.animation.core.tween(fadeOutDuration.toInt())),
            ) {
                Column {
                    if (bottomLeft2 != OverlayType.EMPTY) {
                        OverlayContent(type = bottomLeft2, state = state)
                    }
                    if (bottomLeft1 != OverlayType.EMPTY) {
                        OverlayContent(type = bottomLeft1, state = state)
                    }
                }
            }
        }

        // Bottom Right
        if (hasBottomRight) {
            AnimatedVisibility(
                visible = bottomRightVisible,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 32.dp, bottom = 26.dp),
                enter = fadeIn(animationSpec = androidx.compose.animation.core.tween(fadeDuration.toInt())),
                exit = fadeOut(animationSpec = androidx.compose.animation.core.tween(fadeOutDuration.toInt())),
            ) {
                Column(horizontalAlignment = Alignment.End) {
                    if (bottomRight2 != OverlayType.EMPTY) {
                        OverlayContent(type = bottomRight2, state = state)
                    }
                    if (bottomRight1 != OverlayType.EMPTY) {
                        OverlayContent(type = bottomRight1, state = state)
                    }
                }
            }
        }

        // Top Left
        if (hasTopLeft) {
            AnimatedVisibility(
                visible = topLeftVisible,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 32.dp, top = 26.dp),
                enter = fadeIn(animationSpec = androidx.compose.animation.core.tween(fadeDuration.toInt())),
                exit = fadeOut(animationSpec = androidx.compose.animation.core.tween(fadeOutDuration.toInt())),
            ) {
                Column {
                    if (topLeft2 != OverlayType.EMPTY) {
                        OverlayContent(type = topLeft2, state = state)
                    }
                    if (topLeft1 != OverlayType.EMPTY) {
                        OverlayContent(type = topLeft1, state = state)
                    }
                }
            }
        }

        // Top Right
        if (hasTopRight) {
            AnimatedVisibility(
                visible = topRightVisible,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 32.dp, top = 26.dp),
                enter = fadeIn(animationSpec = androidx.compose.animation.core.tween(fadeDuration.toInt())),
                exit = fadeOut(animationSpec = androidx.compose.animation.core.tween(fadeOutDuration.toInt())),
            ) {
                Column(horizontalAlignment = Alignment.End) {
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

// Cubic ease-in gradient matching GradientHelper.smoothBackgroundAlt
private fun easeInGradientColors(): List<Color> {
    val equation: (Double) -> Double = { x ->
        if (x < 0.5) 4 * x * x * x else 1 - (-2 * x + 2).pow(3.0) / 2
    }

    val colors = mutableListOf<Color>()
    val min = 0.3
    val max = 1.0
    val steps = 20.0

    var i = min
    while (i <= max) {
        val alpha = (1 - equation(i)).toFloat()
        colors.add(Color(0f, 0f, 0f, alpha))
        i += max / steps
    }

    return colors
}
