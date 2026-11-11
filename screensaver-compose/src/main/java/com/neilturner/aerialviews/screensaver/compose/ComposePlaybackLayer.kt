package com.neilturner.aerialviews.screensaver.compose

import android.view.View
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.viewinterop.AndroidView
import com.neilturner.aerialviews.models.enums.AerialMediaType
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.ui.core.ExtractedVideoMetadata
import com.neilturner.aerialviews.ui.core.ImagePlayerView
import com.neilturner.aerialviews.ui.core.PlaybackCallbacks
import com.neilturner.aerialviews.ui.core.ScreensaverController
import com.neilturner.aerialviews.ui.core.ScreensaverState
import com.neilturner.aerialviews.ui.core.VideoPlayerView

/**
 * Hosts the existing playback views and drives the controller's transition protocol.
 *
 * Both views stay in the hierarchy so the video codec can stay warm between items, matching the
 * legacy renderer. Fades are applied to the black loading overlay, never to the SurfaceView.
 */
@Suppress("ktlint:standard:function-naming")
@Composable
internal fun ComposePlaybackLayer(
    controller: ScreensaverController,
    state: ScreensaverState,
    hostStarted: Boolean,
    modifier: Modifier = Modifier,
    onActionsReady: (ComposePlaybackControls?) -> Unit = {},
    overlayContent: @Composable BoxScope.() -> Unit = {},
    loadingContent: @Composable BoxScope.() -> Unit = {},
) {
    val currentState by rememberUpdatedState(state)
    val videoPlayer = remember { mutableStateOf<VideoPlayerView?>(null) }
    val imagePlayer = remember { mutableStateOf<ImagePlayerView?>(null) }
    var renderedGeneration by remember { mutableLongStateOf(-1L) }
    var pausedAt by remember { mutableLongStateOf(0L) }
    var userPaused by remember { mutableStateOf(false) }
    var userPausedAt by remember { mutableLongStateOf(0L) }
    var userMuted by remember { mutableStateOf(!GeneralPrefs.playsVideoAudio) }
    var mediaReady by remember { mutableStateOf(false) }
    val loadingAlpha = remember { Animatable(1f) }
    val fadeInDuration =
        remember {
            GeneralPrefs.mediaFadeInDuration
                .toLongOrNull()
                ?.coerceAtLeast(0)
                ?.toInt() ?: 0
        }
    val fadeOutDuration =
        remember {
            GeneralPrefs.mediaFadeOutDuration
                .toLongOrNull()
                ?.coerceAtLeast(0)
                ?.toInt() ?: 0
        }

    val controls =
        remember {
            object : ComposePlaybackControls {
                override fun togglePause() {
                    val video = videoPlayer.value
                    val image = imagePlayer.value
                    if (userPaused) {
                        val pausedFor = (System.currentTimeMillis() - userPausedAt).coerceAtLeast(0L)
                        if (currentState.media?.type == AerialMediaType.VIDEO) {
                            video?.resume()
                        } else {
                            image?.resumeTimer(pausedFor)
                        }
                        userPaused = false
                        userPausedAt = 0L
                    } else {
                        userPausedAt = System.currentTimeMillis()
                        userPaused = true
                        if (currentState.media?.type == AerialMediaType.VIDEO) {
                            video?.pause()
                        } else {
                            image?.pauseTimer()
                        }
                    }
                }

                override fun toggleMute() {
                    val video = videoPlayer.value ?: return
                    video.toggleMute()
                    userMuted = video.mutedState
                    video.setMuteState(currentState.music.forcedVideoMute, userMuted)
                }

                override fun increaseSpeed() {
                    if (currentState.media?.type == AerialMediaType.VIDEO && !currentState.blackout.active) {
                        videoPlayer.value?.increaseSpeed()
                    }
                }

                override fun decreaseSpeed() {
                    if (currentState.media?.type == AerialMediaType.VIDEO && !currentState.blackout.active) {
                        videoPlayer.value?.decreaseSpeed()
                    }
                }

                override fun seekForward() {
                    if (currentState.media?.type == AerialMediaType.VIDEO && !currentState.blackout.active) {
                        videoPlayer.value?.seekForward()
                    }
                }

                override fun seekBackward() {
                    if (currentState.media?.type == AerialMediaType.VIDEO && !currentState.blackout.active) {
                        videoPlayer.value?.seekBackward()
                    }
                }

                override fun restartVideo() {
                    if (currentState.media?.type != AerialMediaType.VIDEO || currentState.blackout.active) return
                    if (userPaused) {
                        val pausedFor = (System.currentTimeMillis() - userPausedAt).coerceAtLeast(0L)
                        videoPlayer.value?.resume()
                        imagePlayer.value?.resumeTimer(pausedFor)
                        userPaused = false
                        userPausedAt = 0L
                    }
                    videoPlayer.value?.restart()
                }

                override fun refreshFinishTimer() {
                    videoPlayer.value?.refreshFinishTimer()
                }

                override fun increaseBrightness() = Unit

                override fun decreaseBrightness() = Unit
            }
        }

    DisposableEffect(controls) {
        onActionsReady(controls)
        onDispose { onActionsReady(null) }
    }

    Box(modifier = modifier) {
        AndroidView(
            factory = { context ->
                VideoPlayerView(context).apply {
                    setOnPlayerListener(
                        object : VideoPlayerView.OnVideoPlayerEventListener {
                            override fun onVideoAlmostFinished() {
                                if (PlaybackCallbacks.videoEventAppliesTo(currentState.media?.type)) {
                                    controller.onItemFinished()
                                }
                            }

                            override fun onVideoError() {
                                if (PlaybackCallbacks.videoEventAppliesTo(currentState.media?.type)) {
                                    controller.onPlaybackError()
                                }
                            }

                            override fun onVideoPrepared() {
                                if (PlaybackCallbacks.videoEventAppliesTo(currentState.media?.type)) {
                                    mediaReady = true
                                }
                            }

                            override fun onVideoPlaybackSpeedChanged() = Unit

                            override fun onVideoMetadataExtracted(metadata: ExtractedVideoMetadata) = Unit
                        },
                    )
                    videoPlayer.value = this
                }
            },
            update = { view ->
                view.visibility =
                    if (currentState.media?.type == AerialMediaType.VIDEO) {
                        View.VISIBLE
                    } else {
                        View.INVISIBLE
                    }
            },
            onRelease = { view ->
                view.release()
                if (videoPlayer.value === view) videoPlayer.value = null
            },
            modifier = Modifier.fillMaxSize(),
        )

        AndroidView(
            factory = { context ->
                ImagePlayerView(context).apply {
                    setOnPlayerListener(
                        object : ImagePlayerView.OnImagePlayerEventListener {
                            override fun onImageFinished() {
                                if (currentState.media?.type == AerialMediaType.IMAGE) {
                                    controller.onItemFinished()
                                }
                            }

                            override fun onImageError() {
                                if (currentState.media?.type == AerialMediaType.IMAGE) {
                                    controller.onPlaybackError()
                                }
                            }

                            override fun onImagePrepared() {
                                if (currentState.media?.type == AerialMediaType.IMAGE) {
                                    mediaReady = true
                                }
                            }
                        },
                    )
                    imagePlayer.value = this
                }
            },
            update = { view ->
                view.visibility =
                    if (currentState.media?.type == AerialMediaType.IMAGE) {
                        View.VISIBLE
                    } else {
                        View.INVISIBLE
                    }
            },
            onRelease = { view ->
                view.release()
                if (imagePlayer.value === view) imagePlayer.value = null
            },
            modifier = Modifier.fillMaxSize(),
        )

        // Overlays sit above the players but below the loading layer, matching aerial_activity.xml.
        overlayContent()

        // Fade only this layer: the player SurfaceView must not receive an animated alpha.
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = loadingAlpha.value }
                    .background(Color.Black),
        ) {
            loadingContent()
        }
    }

    LaunchedEffect(state.itemGeneration, videoPlayer.value, imagePlayer.value) {
        val media = state.media ?: return@LaunchedEffect
        if (renderedGeneration == state.itemGeneration) return@LaunchedEffect

        mediaReady = false
        userPaused = false
        userPausedAt = 0L
        loadingAlpha.snapTo(1f)

        when (media.type) {
            AerialMediaType.VIDEO -> {
                val player = videoPlayer.value ?: return@LaunchedEffect
                imagePlayer.value?.stop()
                player.setVideo(media)
                player.start()
                renderedGeneration = state.itemGeneration
            }

            AerialMediaType.IMAGE -> {
                val player = imagePlayer.value ?: return@LaunchedEffect
                videoPlayer.value?.suspendPlayback()
                player.setImage(media)
                renderedGeneration = state.itemGeneration
            }
        }
    }

    LaunchedEffect(state.itemGeneration, mediaReady) {
        if (!mediaReady) return@LaunchedEffect

        loadingAlpha.animateTo(
            targetValue = 0f,
            animationSpec = tween(durationMillis = fadeInDuration, easing = LinearEasing),
        )
        controller.onMediaVisible()
    }

    LaunchedEffect(state.transition?.id) {
        val transition = state.transition ?: return@LaunchedEffect
        mediaReady = false

        if (state.media?.type == AerialMediaType.VIDEO) {
            videoPlayer.value?.fadeOutAudio(fadeOutDuration.toLong())
        }

        loadingAlpha.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = fadeOutDuration, easing = LinearEasing),
        )
        imagePlayer.value?.stop()
        controller.onTransitionCompleted(transition.id)
    }

    LaunchedEffect(hostStarted, videoPlayer.value, imagePlayer.value) {
        val video = videoPlayer.value
        val image = imagePlayer.value

        if (!hostStarted) {
            if (pausedAt == 0L) pausedAt = System.currentTimeMillis()
            video?.pause()
            image?.pauseTimer()
        } else if (pausedAt > 0L) {
            val pausedFor = System.currentTimeMillis() - pausedAt
            pausedAt = 0L
            video?.resume()
            image?.resumeTimer(pausedFor)
        }
    }

    LaunchedEffect(state.music.forcedVideoMute, userMuted) {
        videoPlayer.value?.setMuteState(state.music.forcedVideoMute, userMuted)
    }

    LaunchedEffect(state.blackout.active) {
        if (state.blackout.active) {
            videoPlayer.value?.stop()
            imagePlayer.value?.stop()
        }
    }

    LaunchedEffect(state.error) {
        if (state.error != null) {
            loadingAlpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 200, easing = LinearEasing),
            )
        }
    }
}
