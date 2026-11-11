package com.neilturner.aerialviews.screensaver.compose

import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.neilturner.aerialviews.models.enums.AerialMediaType
import com.neilturner.aerialviews.ui.core.ExtractedVideoMetadata
import com.neilturner.aerialviews.ui.core.ImagePlayerView
import com.neilturner.aerialviews.ui.core.PlaybackCallbacks
import com.neilturner.aerialviews.ui.core.ScreensaverController
import com.neilturner.aerialviews.ui.core.ScreensaverState
import com.neilturner.aerialviews.ui.core.VideoPlayerView

/**
 * Hosts the existing playback views without modifying them.
 *
 * Both views stay in the hierarchy so the video codec can stay warm between items, matching the
 * legacy renderer. Only visibility changes as the controller loads another item; no animation is
 * applied to the player surface itself.
 */
@Suppress("ktlint:standard:function-naming")
@Composable
internal fun ComposePlaybackLayer(
    controller: ScreensaverController,
    state: ScreensaverState,
    hostStarted: Boolean,
    modifier: Modifier = Modifier,
) {
    val currentState by rememberUpdatedState(state)
    val videoPlayer = remember { mutableStateOf<VideoPlayerView?>(null) }
    val imagePlayer = remember { mutableStateOf<ImagePlayerView?>(null) }
    var renderedGeneration by remember { mutableLongStateOf(-1L) }
    var pausedAt by remember { mutableLongStateOf(0L) }

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
                                    controller.onMediaVisible()
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
                                    controller.onMediaVisible()
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
    }

    LaunchedEffect(state.itemGeneration, videoPlayer.value, imagePlayer.value) {
        val media = state.media ?: return@LaunchedEffect
        if (renderedGeneration == state.itemGeneration) return@LaunchedEffect

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
}
