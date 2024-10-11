package com.neilturner.aerialviews.ui.core

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.util.EventLogger
import com.neilturner.aerialviews.models.enums.LimitLongerVideos
import com.neilturner.aerialviews.models.enums.VideoScale
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.services.CustomRendererFactory
import com.neilturner.aerialviews.utils.WindowHelper
import timber.log.Timber
import kotlin.math.ceil
import kotlin.math.roundToLong
import kotlin.time.Duration.Companion.milliseconds

object VideoPlayerHelper {
    @SuppressLint("UnsafeOptInUsageError")
    fun buildPlayer(
        context: Context,
        prefs: GeneralPrefs,
    ): ExoPlayer {
        val parametersBuilder = DefaultTrackSelector.Parameters.Builder(context)

        if (prefs.enableTunneling) {
            parametersBuilder
                .setTunnelingEnabled(true)
        }

        val trackSelector = DefaultTrackSelector(context)
        trackSelector.parameters = parametersBuilder.build()

        var rendererFactory = DefaultRenderersFactory(context)
        if (prefs.allowFallbackDecoders) {
            rendererFactory.setEnableDecoderFallback(true)
        }
        if (prefs.philipsDolbyVisionFix) {
            rendererFactory = CustomRendererFactory(context)
        }

        val player =
            ExoPlayer
                .Builder(context)
                .setTrackSelector(trackSelector)
                .setRenderersFactory(rendererFactory)
                .build()

        if (prefs.enablePlaybackLogging) {
            player.addAnalyticsListener(EventLogger())
        }

        if (!prefs.muteVideos) {
            player.volume = prefs.videoVolume.toFloat() / 100
        } else {
            player.volume = 0f
        }

        // https://medium.com/androiddevelopers/prep-your-tv-app-for-android-12-9a859d9bb967
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && prefs.refreshRateSwitching) {
            Timber.i("Android 12, enabling refresh rate switching")
            player.videoChangeFrameRateStrategy = C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_OFF
        }

        val videoScale = if (GeneralPrefs.videoScale == VideoScale.SCALE_TO_FIT) 1 else 2
        player.videoScalingMode = videoScale

        player.setPlaybackSpeed(prefs.playbackSpeed.toFloat())
        return player
    }

    fun calculateDelay(
        player: ExoPlayer,
        prefs: GeneralPrefs,
        isSegmentedVideo: Boolean,
        segmentStart: Long,
        segmentEnd: Long,
        loopCount: Int,
    ): Long {
        // 10 seconds is the min. video length
        val tenSeconds = 10 * 1000
        val maxVideoLength = prefs.maxVideoLength.toInt() * 1000
        val allowLongerVideos = prefs.limitLongerVideos == LimitLongerVideos.IGNORE

        // If max length disabled, play full video
        if (maxVideoLength < tenSeconds) {
            return calculateEndOfVideo(player.duration, player.currentPosition, prefs.playbackSpeed, isSegmentedVideo, segmentStart)
        }

        // Play a part/segment of a video only
        if (isSegmentedVideo) {
            val position = if (player.currentPosition < segmentStart) 0 else player.currentPosition - segmentStart
            return calculateEndOfVideo(segmentEnd - segmentStart, position, prefs.playbackSpeed, isSegmentedVideo, segmentStart)
        }

        // Check if we need to loop the video
        if (prefs.loopShortVideos &&
            player.duration < maxVideoLength
        ) {
            val (isLooping, duration) = calculateLoopingVideo(maxVideoLength, player.duration.toInt())
            if (isLooping) {
                player.repeatMode = Player.REPEAT_MODE_ALL
            }
            val position = (loopCount * player.duration) + player.currentPosition
            return calculateEndOfVideo(duration, position, prefs.playbackSpeed, isSegmentedVideo, segmentStart)
        }

        // Limit the duration of the video, or not
        if (maxVideoLength in tenSeconds until player.duration &&
            !allowLongerVideos
        ) {
            Timber.i("Limiting duration (video is ${player.duration.milliseconds}, limit is ${maxVideoLength.milliseconds})")
            return calculateEndOfVideo(maxVideoLength.toLong(), player.currentPosition, prefs.playbackSpeed, isSegmentedVideo, segmentStart)
        }
        Timber.i("Ignoring limit (video is ${player.duration.milliseconds}, limit is ${maxVideoLength.milliseconds})")
        return calculateEndOfVideo(player.duration, player.currentPosition, prefs.playbackSpeed, isSegmentedVideo, segmentStart)
    }

    private fun calculateEndOfVideo(
        duration: Long,
        position: Long,
        playbackSpeed: String,
        isSegmentedVideo: Boolean,
        segmentStart: Long,
    ): Long {
        // Adjust the duration based on the playback speed
        // Take into account the current player position in case of speed changes during playback
        val delay = (((duration - position) / playbackSpeed.toFloat()).roundToLong() - GeneralPrefs.mediaFadeOutDuration.toLong())
        val actualPosition = if (isSegmentedVideo) position + segmentStart else position
        Timber.i("Delay: ${delay.milliseconds} (Duration: ${duration.milliseconds}, Position: ${actualPosition.milliseconds})")
        return if (delay < 0) 0 else delay
    }

    private fun calculateLoopingVideo(
        maxVideoLength: Int,
        duration: Int,
    ): Pair<Boolean, Long> {
        val loopCount = ceil(maxVideoLength / duration.toDouble()).toInt()
        val targetDuration = duration * loopCount
        Timber.i("Looping $loopCount times (video is ${duration.milliseconds}, limit is ${maxVideoLength.milliseconds})")
        return Pair(loopCount > 1, targetDuration.toLong())
    }

    fun calculateSegments(
        maxVideoLength: Int,
        duration: Int,
    ): Triple<Boolean, Long, Long> {
        // 10 seconds is the min. video length
        val tenSeconds = 10 * 1000
        if (maxVideoLength < tenSeconds) {
            return Triple(false, 0L, 0L)
        }
        val segments = duration / maxVideoLength
        if (segments < 2) {
            return Triple(false, 0L, 0L)
        }
        val length = duration.floorDiv(segments).toLong()
        val random = (1..segments).random()
        val segmentStart = (random - 1) * length
        val segmentEnd = random * length

        val message1 = "Segment chosen: ${segmentStart.milliseconds} - ${segmentEnd.milliseconds}"
        val message2 = "(video is ${duration.milliseconds}, Segments: $segments)"
        Timber.i("$message1 $message2")
        return Triple(true, segmentStart, segmentEnd)
    }

    @OptIn(UnstableApi::class)
    fun setRefreshRate(
        player: ExoPlayer,
        context: Context,
    ) {
        val frameRate = player.videoFormat?.frameRate

        if (frameRate == null || frameRate == 0f) {
            Timber.i("Unable to get video frame rate...")
            return
        }

        Timber.i("${frameRate}fps video, setting refresh rate if needed...")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            WindowHelper.setLegacyRefreshRate(context, frameRate)
        }
    }
}
