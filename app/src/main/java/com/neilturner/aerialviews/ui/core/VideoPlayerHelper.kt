package com.neilturner.aerialviews.ui.core

import android.content.Context
import android.os.Build
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector.Parameters
import androidx.media3.exoplayer.util.EventLogger
import androidx.media3.ui.AspectRatioFrameLayout
import com.neilturner.aerialviews.models.enums.AerialMediaSource
import com.neilturner.aerialviews.models.enums.ImmichAuthType
import com.neilturner.aerialviews.models.enums.LimitLongerVideos
import com.neilturner.aerialviews.models.enums.VideoScale
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.models.prefs.ImmichMediaPrefs
import com.neilturner.aerialviews.models.videos.AerialMedia
import com.neilturner.aerialviews.providers.samba.SambaDataSourceFactory
import com.neilturner.aerialviews.providers.webdav.WebDavDataSourceFactory
import com.neilturner.aerialviews.services.philips.CustomRendererFactory
import timber.log.Timber
import java.util.concurrent.TimeUnit
import kotlin.math.ceil
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds

object VideoPlayerHelper {
    private const val TEN_SECONDS = 10 * 1000

    fun toggleAudioTrack(
        player: ExoPlayer,
        disableAudio: Boolean,
    ) {
        player.trackSelectionParameters =
            player.trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, disableAudio)
                .build()
    }

    fun disableTextTrack(player: ExoPlayer) {
        player.trackSelectionParameters =
            player.trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
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
        val parametersBuilder = Parameters.Builder()

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

        val loadControl =
            DefaultLoadControl
                .Builder()
                .setBufferDurationsMs(
                    10_000, // Minimum buffer duration
                    20_000, // Maximum buffer duration
                    3_000, // Buffer before initial playback
                    5_000, // Buffer after rebuffering
                ).build()

        val player =
            ExoPlayer
                .Builder(context)
                .setTrackSelector(trackSelector)
                .setLoadControl(loadControl)
                .setRenderersFactory(rendererFactory)
                .build()

        if (prefs.enablePlaybackLogging) {
            player.addAnalyticsListener(EventLogger())
        }

        if (!prefs.muteVideos) player.volume = prefs.videoVolume.toFloat() / 100 else player.volume = 0f

        // https://medium.com/androiddevelopers/prep-your-tv-app-for-android-12-9a859d9bb967
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && prefs.refreshRateSwitching) {
            player.videoChangeFrameRateStrategy = C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_OFF
        }

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
                    val duration =
                        if (maxVideoLength >= player.duration) {
                            Timber.i("Using video duration as limit (shorter than max!)")
                            player.duration
                        } else {
                            Timber.i("Using user limit")
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

    private fun calculateRandomStartPosition(
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
        Timber.i("Start at ${randomPosition.milliseconds} ($percent%, from 0%-%$range)")

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

        val length = duration.floorDiv(numOfSegments)
        val randomSegment = (1..numOfSegments).random()
        val segmentStart = (randomSegment - 1) * length
        val segmentEnd = randomSegment * length

        val message1 = "Video length ${duration.milliseconds}, $numOfSegments segments of ${length.milliseconds}\n"
        val message2 = "Chose segment $randomSegment, ${segmentStart.milliseconds} - ${segmentEnd.milliseconds}"
        Timber.i("$message1$message2")

        return Pair(segmentStart, segmentEnd)
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
        Timber.i(
            "Looping $loopCount times (video is ${duration.milliseconds}, total is ${targetDuration.milliseconds}, limit is ${maxLength.milliseconds})",
        )
        return Pair(0, targetDuration)
    }
}
