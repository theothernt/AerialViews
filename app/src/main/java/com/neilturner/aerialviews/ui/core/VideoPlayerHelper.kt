package com.neilturner.aerialviews.ui.core

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.util.EventLogger
import com.neilturner.aerialviews.models.enums.VideoScale
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.services.CustomRendererFactory
import timber.log.Timber
import kotlin.math.ceil
import kotlin.math.roundToLong
import kotlin.time.Duration.Companion.milliseconds

object VideoPlayerHelper {

    private val enableTunneling = GeneralPrefs.enableTunneling
    private val useRefreshRateSwitching = GeneralPrefs.refreshRateSwitching
    private val philipsDolbyVisionFix = GeneralPrefs.philipsDolbyVisionFix
    private val fallbackDecoders = GeneralPrefs.allowFallbackDecoders
    private val extraLogging = GeneralPrefs.enablePlaybackLogging
    private val videoVolume = GeneralPrefs.videoVolume.toFloat() / 100
    private val videoScale = if (GeneralPrefs.videoScale == VideoScale.SCALE_TO_FIT) 1 else 2
    private var playbackSpeed = GeneralPrefs.playbackSpeed
    private val muteVideo = GeneralPrefs.muteVideos

    @SuppressLint("UnsafeOptInUsageError")
    fun buildPlayer(context: Context): ExoPlayer {
        val parametersBuilder = DefaultTrackSelector.Parameters.Builder(context)

        if (enableTunneling) {
            parametersBuilder
                .setTunnelingEnabled(true)
        }

        val trackSelector = DefaultTrackSelector(context)
        trackSelector.parameters = parametersBuilder.build()

        var rendererFactory = DefaultRenderersFactory(context)
        if (fallbackDecoders) {
            rendererFactory.setEnableDecoderFallback(true)
        }
        if (philipsDolbyVisionFix) {
            rendererFactory = CustomRendererFactory(context)
        }

        val player =
            ExoPlayer
                .Builder(context)
                .setTrackSelector(trackSelector)
                .setRenderersFactory(rendererFactory)
                .build()

        if (extraLogging) {
            player.addAnalyticsListener(EventLogger())
        }

        if (!muteVideo) {
            player.volume = videoVolume
        } else {
            player.volume = 0f
        }

        // https://medium.com/androiddevelopers/prep-your-tv-app-for-android-12-9a859d9bb967
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && useRefreshRateSwitching) {
            Timber.i("Android 12, enabling refresh rate switching")
            player.videoChangeFrameRateStrategy = C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_OFF
        }

        player.videoScalingMode = videoScale
        player.setPlaybackSpeed(playbackSpeed.toFloat())
        return player
    }

    fun calculateDelay(player: ExoPlayer,
                            playbackSpeed: String,
                               maxVideoLength: Int,
                               isSegmentedVideo: Boolean,
                               allowLongerVideos: Boolean,
                               segmentStart: Long,
                               segmentEnd: Long,
                               loopShortVideos: Boolean,
                               loopCount: Int): Long {
        // 10 seconds is the min. video length
        val tenSeconds = 10 * 1000

        // If max length disabled, play full video
        if (maxVideoLength < tenSeconds) {
            return calculateEndOfVideo(player.duration, player.currentPosition, playbackSpeed, isSegmentedVideo, segmentStart)
        }

        // Play a part/segment of a video only
        if (isSegmentedVideo) {
            val position = if (player.currentPosition < segmentStart) 0 else player.currentPosition - segmentStart
            return calculateEndOfVideo(segmentEnd - segmentStart, position, playbackSpeed, isSegmentedVideo, segmentStart)
        }

        // Check if we need to loop the video
        if (loopShortVideos &&
            player.duration < maxVideoLength
        ) {
            val (isLooping, duration) = calculateLoopingVideo(maxVideoLength, player.duration.toInt())
            if (isLooping) {
                player.repeatMode = Player.REPEAT_MODE_ALL
            }
            val position = (loopCount * player.duration) + player.currentPosition
            return calculateEndOfVideo(duration, position, playbackSpeed, isSegmentedVideo, segmentStart)
        }

        // Limit the duration of the video, or not
        if (maxVideoLength in tenSeconds until player.duration &&
            !allowLongerVideos
        ) {
            Timber.i("Limiting duration (video is ${player.duration.milliseconds}, limit is ${maxVideoLength.milliseconds})")
            return calculateEndOfVideo(maxVideoLength.toLong(), player.currentPosition, playbackSpeed, isSegmentedVideo, segmentStart)
        }
        Timber.i("Ignoring limit (video is ${player.duration.milliseconds}, limit is ${maxVideoLength.milliseconds})")
        return calculateEndOfVideo(player.duration, player.currentPosition, playbackSpeed, isSegmentedVideo, segmentStart)
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

    private fun calculateLoopingVideo(maxVideoLength: Int,
                                      duration: Int): Pair<Boolean, Long> {
        val loopCount = ceil(maxVideoLength / duration.toDouble()).toInt()
        val targetDuration = duration * loopCount
        Timber.i("Looping $loopCount times (video is ${duration.milliseconds}, limit is ${maxVideoLength.milliseconds})")
        return Pair(loopCount > 1, targetDuration.toLong())
    }

    fun calculateSegments(maxVideoLength: Int, duration: Int): Triple<Boolean, Long, Long> {
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
}