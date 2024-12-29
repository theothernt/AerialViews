package com.neilturner.aerialviews.ui.core

import android.content.Context
import android.os.Build
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.util.EventLogger
import androidx.media3.ui.AspectRatioFrameLayout
import com.neilturner.aerialviews.models.enums.AerialMediaSource
import com.neilturner.aerialviews.models.enums.ImmichAuthType
import com.neilturner.aerialviews.models.enums.LimitLongerVideos
import com.neilturner.aerialviews.models.enums.VideoScale
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.models.prefs.ImmichMediaPrefs
import com.neilturner.aerialviews.models.videos.AerialMedia
import com.neilturner.aerialviews.services.CustomRendererFactory
import com.neilturner.aerialviews.services.SambaDataSourceFactory
import com.neilturner.aerialviews.services.WebDavDataSourceFactory
import com.neilturner.aerialviews.utils.WindowHelper
import timber.log.Timber
import java.util.concurrent.TimeUnit
import kotlin.math.ceil
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds

object VideoPlayerHelper {
    private const val TEN_SECONDS = 10 * 1000

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
    fun getResizeMode(scale: VideoScale?): Int =
        if (scale == VideoScale.SCALE_TO_FIT_WITH_CROPPING) {
            AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        } else {
            AspectRatioFrameLayout.RESIZE_MODE_FIT
        }

    @OptIn(UnstableApi::class)
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

        if (!prefs.muteVideos) player.volume = prefs.videoVolume.toFloat() / 100 else player.volume = 0f

        // https://medium.com/androiddevelopers/prep-your-tv-app-for-android-12-9a859d9bb967
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && prefs.refreshRateSwitching) {
            Timber.i("Android 12+, enabling refresh rate switching")
            player.videoChangeFrameRateStrategy = C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_OFF
        }

        // By default, set repeat mode to on, but only used for looping short videos
        player.repeatMode == ExoPlayer.REPEAT_MODE_ALL

        player.setPlaybackSpeed(prefs.playbackSpeed.toFloat())
        return player
    }

    @OptIn(UnstableApi::class)
    fun setupMediaSource(
        player: ExoPlayer,
        media: AerialMedia,
    ) {
        val mediaItem = MediaItem.fromUri(media.uri)
        when (media.source) {
            AerialMediaSource.SAMBA -> {
                val mediaSource =
                    ProgressiveMediaSource
                        .Factory(SambaDataSourceFactory())
                        .createMediaSource(mediaItem)
                player.setMediaSource(mediaSource)
            }
            AerialMediaSource.IMMICH -> {
                val dataSourceFactory =
                    DefaultHttpDataSource
                        .Factory()
                        .setAllowCrossProtocolRedirects(true)
                        .setConnectTimeoutMs(TimeUnit.SECONDS.toMillis(30).toInt())
                        .setReadTimeoutMs(TimeUnit.SECONDS.toMillis(30).toInt())

                // Add necessary headers for Immich
                if (ImmichMediaPrefs.authType == ImmichAuthType.API_KEY) {
                    dataSourceFactory.setDefaultRequestProperties(
                        mapOf("X-API-Key" to ImmichMediaPrefs.apiKey),
                    )
                }

                // If SSL validation is disabled, we need to set the appropriate flags
                if (!ImmichMediaPrefs.validateSsl) {
                    System.setProperty("javax.net.ssl.trustAll", "true")
                }

                val mediaSource =
                    ProgressiveMediaSource
                        .Factory(dataSourceFactory)
                        .createMediaSource(mediaItem)

                player.setMediaSource(mediaSource)
                Timber.d("Setting up Immich media source with URI: ${media.uri}")
            }
            AerialMediaSource.WEBDAV -> {
                val mediaSource =
                    ProgressiveMediaSource
                        .Factory(WebDavDataSourceFactory())
                        .createMediaSource(mediaItem)
                player.setMediaSource(mediaSource)
            }
            else -> {
                player.setMediaItem(mediaItem)
            }
        }
    }

    fun calculatePlaybackParameters(
        player: ExoPlayer,
        prefs: GeneralPrefs,
    ): Pair<Long, Long> {
        val maxVideoLength = prefs.maxVideoLength.toLong() * 1000
        val isLengthLimited = maxVideoLength >= TEN_SECONDS
        val isShortVideo = player.duration < maxVideoLength

        // methods should return start + duration without
        // a) animation timing or b) playbackspeed

        if (!isLengthLimited && prefs.randomStartPosition) {
            Timber.i("Calculating random start position...")
            val range = GeneralPrefs.randomStartPositionRange.toInt()
            return calculateRandomStartPosition(player.duration, range)
        }

        if (isShortVideo && isLengthLimited && prefs.loopShortVideos) {
            Timber.i("Calculating looping short video...")
            return calculateLoopingVideo(player.duration, maxVideoLength)
        }

        if (!isShortVideo && isLengthLimited) {
            when (prefs.limitLongerVideos) {
                LimitLongerVideos.LIMIT -> {
                    Timber.i("Calculating long video type... obey limit, play until time limit")
                    val duration = if (maxVideoLength >= player.duration) {
                        Timber.i("Using video duration as limit (shorter than max!)")
                        player.duration
                    } else {
                        Timber.i("Using max length as limit")
                        maxVideoLength
                    }
                    return Pair(0, duration)
                }
                LimitLongerVideos.SEGMENT -> {
                    Timber.i("Calculating long video type... play random segment")
                    return calculateRandomSegment(player.duration, maxVideoLength)
                }
                else -> {
                    Timber.i("Calculating long video type... ignore limit, play full video")
                    return Pair(0, player.duration)
                }
            }
        }

        // Use normal start + end/duration
        Timber.i("Calculating normal video type...")
        return Pair(0, player.duration)
    }

    fun calculateRandomStartPosition(
        duration: Long,
        range: Int,
    ): Pair<Long, Long> {
        if (duration <= 0 || range < 5) {
            Timber.e("Invalid duration or range: duration=$duration, range=$range%")
            return Pair(0, 0)
        }
        val seekPosition = (duration * range / 100.0).toLong()
        val randomPosition = Random.nextLong(seekPosition)

        val percent = (randomPosition.toFloat() / duration.toFloat() * 100).toInt()
        Timber.i("Seeking to ${randomPosition.milliseconds} ($percent%, from 0%-%$range)")

        return Pair(randomPosition, duration)
    }

    private fun calculateRandomSegment(
        duration: Long,
        maxLength: Long,
    ): Pair<Long, Long> {
        if (duration <= 0 || maxLength < TEN_SECONDS) {
            Timber.e("Invalid duration or max length: duration=$duration, maxLength=$maxLength%")
        }

        val numOfSegments = duration / maxLength
        if (numOfSegments < 2) {
            Timber.i("Video too short for segments")
            return Pair(0, duration)
        }

        val length = duration.floorDiv(numOfSegments).toLong()
        val randomSegment = (1..numOfSegments).random()
        val segmentStart = (randomSegment - 1) * length
        val segmentEnd = randomSegment * length

        val message1 = "Video length ${duration.milliseconds}, $numOfSegments segments of ${length.milliseconds}\n"
        val message2 = "Chose segment ${randomSegment}, ${segmentStart.milliseconds} - ${segmentEnd.milliseconds}"
        Timber.i("$message1$message2")

        return Pair(segmentStart, segmentEnd)
    }

    fun calculateDelay(
        video: VideoInfo,
        player: ExoPlayer,
        prefs: GeneralPrefs,
    ): Long {
        // TODO
        // account for 0 or less than 0 values ?!

        val loopShortVideos = prefs.loopShortVideos
        val allowLongerVideos = prefs.limitLongerVideos == LimitLongerVideos.IGNORE
        val maxVideoLength = prefs.maxVideoLength.toLong() * 1000
        val duration = player.duration // remove
        val position = player.currentPosition // remove

        // 10 seconds is the min. video length
        val tenSeconds = 10 * 1000

        // If max length disabled, play full video
        if (maxVideoLength < tenSeconds) {
            return calculateEndOfVideo(position, duration, video, prefs)
        }

        // Play a part/segment of a video only
        if (video.isSegmented) {
            val segmentPosition = if (position < video.segmentStart) 0 else position - video.segmentStart
            val segmentDuration = video.segmentEnd - video.segmentStart
            return calculateEndOfVideo(segmentPosition, segmentDuration, video, prefs)
        }

        // Check if we need to loop the video
        if (loopShortVideos &&
            duration < maxVideoLength
        ) {
//            val (isLooping, loopingDuration) = calculateLoopingVideo(maxVideoLength, player)
//            var targetDuration =
//                if (isLooping) {
//                    // player.repeatMode = Player.REPEAT_MODE_ALL
//                    loopingDuration
//                } else {
//                    duration
//                }
            return calculateEndOfVideo(position, 0, video, prefs)
        }

        // Limit the duration of the video, or not
        if (maxVideoLength in tenSeconds until duration &&
            !allowLongerVideos
        ) {
            Timber.i("Limiting duration (video is ${duration.milliseconds}, limit is ${maxVideoLength.milliseconds})")
            return calculateEndOfVideo(position, maxVideoLength, video, prefs)
        }
        Timber.i("Ignoring limit (video is ${duration.milliseconds}, limit is ${maxVideoLength.milliseconds})")
        return calculateEndOfVideo(position, duration, video, prefs)
    }

    private fun calculateEndOfVideo(
        position: Long,
        duration: Long,
        video: VideoInfo,
        prefs: GeneralPrefs,
    ): Long {
        // TODO
        // account for 0 or less than 0 values ?!

        // Adjust the duration based on the playback speed
        // Take into account the current player position in case of speed changes during playback
        val delay = ((duration - position) / prefs.playbackSpeed.toDouble().toLong() - prefs.mediaFadeOutDuration.toLong())
        val actualPosition = if (video.isSegmented) position + video.segmentStart else position
        Timber.i("Delay: ${delay.milliseconds} (duration: ${duration.milliseconds}, position: ${actualPosition.milliseconds})")
        return if (delay < 0) 0 else delay
    }

    private fun calculateLoopingVideo(
        duration: Long,
        maxLength: Long,
    ): Pair<Long, Long> {
        if (duration <= 0 || maxLength < TEN_SECONDS) {
            Timber.e("Invalid duration or max length: duration=$duration, maxLength=$maxLength%")
            return Pair(0, 0)
        }
        val loopCount = ceil(maxLength / duration.toDouble()).toInt()
        val targetDuration = duration * loopCount
        Timber.i("Looping $loopCount times (video is ${duration.milliseconds}, total is ${targetDuration.milliseconds}, limit is ${maxLength.milliseconds})")
        return Pair(0, targetDuration.toLong())
    }
}
