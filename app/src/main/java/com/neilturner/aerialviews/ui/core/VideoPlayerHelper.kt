package com.neilturner.aerialviews.ui.core

import android.content.Context
import android.os.Build
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.util.EventLogger
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.services.CustomRendererFactory
import com.neilturner.aerialviews.utils.WindowHelper
import timber.log.Timber

object VideoPlayerHelper {
    fun setRefreshRate(
        context: Context,
        framerate: Float?,
    ) {
        if (framerate == null || framerate == 0f) {
            Timber.i("Unable to get video frame rate...")
            return
        }

        Timber.i("${framerate}fps video, setting refresh rate if needed...")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            WindowHelper.setLegacyRefreshRate(context, framerate)
        }
    }

    fun disableAudioTrack(player: ExoPlayer) {
        player.trackSelectionParameters =
            player.trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
                .build()
    }

    @OptIn(UnstableApi::class)
    fun buildPlayer(context: Context): ExoPlayer {
        val parametersBuilder = DefaultTrackSelector.Parameters.Builder(context)

        if (GeneralPrefs.enableTunneling) {
            parametersBuilder
                .setTunnelingEnabled(true)
        }

        val trackSelector = DefaultTrackSelector(context)
        trackSelector.parameters = parametersBuilder.build()

        var rendererFactory = DefaultRenderersFactory(context)
        if (GeneralPrefs.allowFallbackDecoders) {
            rendererFactory.setEnableDecoderFallback(true)
        }
        if (GeneralPrefs.philipsDolbyVisionFix) {
            rendererFactory = CustomRendererFactory(context)
        }

        val player =
            ExoPlayer
                .Builder(context)
                .setTrackSelector(trackSelector)
                .setRenderersFactory(rendererFactory)
                .build()

        if (GeneralPrefs.enablePlaybackLogging) {
            player.addAnalyticsListener(EventLogger())
        }

        if (!GeneralPrefs.muteVideos) player.volume = GeneralPrefs.videoVolume.toFloat() / 100 else player.volume = 0f

        // https://medium.com/androiddevelopers/prep-your-tv-app-for-android-12-9a859d9bb967
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && GeneralPrefs.refreshRateSwitching) {
            Timber.i("Android 12+, enabling refresh rate switching")
            player.videoChangeFrameRateStrategy = C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_OFF
        }

        player.setPlaybackSpeed(GeneralPrefs.playbackSpeed.toFloat())
        return player
    }
}
