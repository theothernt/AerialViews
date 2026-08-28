package com.neilturner.aerialviews.ui.core

import android.content.Context
import android.os.Build
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.rtsp.RtspMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector.Parameters
import androidx.media3.exoplayer.util.EventLogger
import androidx.media3.ui.AspectRatioFrameLayout
import com.neilturner.aerialviews.models.enums.AerialMediaSource
import com.neilturner.aerialviews.models.enums.LimitLongerVideos
import com.neilturner.aerialviews.models.enums.SchemeType
import com.neilturner.aerialviews.models.enums.VideoScale
import com.neilturner.aerialviews.models.music.MusicTrack
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.models.prefs.WebDavMediaPrefs
import com.neilturner.aerialviews.models.prefs.WebDavMediaPrefs2
import com.neilturner.aerialviews.models.videos.AerialMedia
import com.neilturner.aerialviews.providers.immich.ImmichDataSourceFactory
import com.neilturner.aerialviews.providers.ncmemories.NCMemoriesDataSourceFactory
import com.neilturner.aerialviews.providers.samba.SambaDataSourceFactory
import com.neilturner.aerialviews.providers.webdav.WebDavDataSourceFactory
import com.neilturner.aerialviews.providers.webdav.WebDavHostParser
import com.neilturner.aerialviews.providers.webdav.defaultPortFor
import com.neilturner.aerialviews.services.philips.CustomRendererFactory
import timber.log.Timber
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
    fun buildAudioPlayer(context: Context): ExoPlayer {
        val loadControl =
            DefaultLoadControl
                .Builder()
                .setBufferDurationsMs(
                    10_000,
                    20_000,
                    3_000,
                    5_000,
                ).build()

        return ExoPlayer
            .Builder(context)
            .setLoadControl(loadControl)
            .build()
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
                    if (prefs.reduceBufferMemory) 2_000 else 10_000,
                    if (prefs.reduceBufferMemory) 10_000 else 20_000,
                    if (prefs.reduceBufferMemory) 500 else 3_000,
                    if (prefs.reduceBufferMemory) 1_000 else 5_000,
                ).setTargetBufferBytes(C.LENGTH_UNSET)
                .build()

        val player =
            ExoPlayer
                .Builder(context)
                .setTrackSelector(trackSelector)
                .setLoadControl(loadControl)
                .setRenderersFactory(rendererFactory)
                .build()

        if (prefs.enableLogCapture) {
            player.addAnalyticsListener(EventLogger())
            player.addAnalyticsListener(PlaybackDiagnosticsListener(context))
        }

        if (prefs.playsVideoAudio) {
            player.volume =
                prefs.videoVolume.toFloat() / 100
        } else {
            player.volume = 0f
        }

        // https://medium.com/androiddevelopers/prep-your-tv-app-for-android-12-9a859d9bb967
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && prefs.refreshRateSwitching) {
            player.videoChangeFrameRateStrategy = C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_OFF
        }

        player.setPlaybackSpeed(prefs.playbackSpeed.toFloat())
        return player
    }

    @OptIn(UnstableApi::class)
    fun setupMediaSource(
        context: Context,
        player: ExoPlayer,
        media: AerialMedia,
    ) {
        player.setMediaSource(createMediaSource(context, MediaItem.fromUri(media.uri), media.source))
    }

    @OptIn(UnstableApi::class)
    fun createAudioMediaSource(
        context: Context,
        track: MusicTrack,
    ) = createMediaSource(context, MediaItem.fromUri(track.uri), track.source)

    @OptIn(UnstableApi::class)
    private fun createMediaSource(
        context: Context,
        mediaItem: MediaItem,
        source: AerialMediaSource,
    ) = when (source) {
        AerialMediaSource.SAMBA -> {
            ProgressiveMediaSource
                .Factory(SambaDataSourceFactory())
                .createMediaSource(mediaItem)
        }

        AerialMediaSource.RTSP -> {
            RtspMediaSource
                .Factory()
                .setDebugLoggingEnabled(true)
                .setForceUseRtpTcp(true)
                .createMediaSource(mediaItem)
        }

        AerialMediaSource.IMMICH -> {
            Timber.d("Setting up Immich media source with URI: ${mediaItem.localConfiguration?.uri}")
            ProgressiveMediaSource
                .Factory(ImmichDataSourceFactory())
                .createMediaSource(mediaItem)
        }

        AerialMediaSource.NCMEMORIES -> {
            Timber.d("Setting up Nextcloud Memories media source with URI: ${mediaItem.localConfiguration?.uri}")
            ProgressiveMediaSource
                .Factory(NCMemoriesDataSourceFactory())
                .createMediaSource(mediaItem)
        }

        AerialMediaSource.WEBDAV -> {
            val validateSsl = getWebDavValidateSslFromUri(mediaItem.localConfiguration!!.uri)
            ProgressiveMediaSource
                .Factory(WebDavDataSourceFactory(validateSsl))
                .createMediaSource(mediaItem)
        }

        else -> {
            val dataSourceFactory = DefaultDataSource.Factory(context)
            ProgressiveMediaSource
                .Factory(dataSourceFactory)
                .createMediaSource(mediaItem)
        }
    }

    fun calculatePlaybackParameters(
        player: ExoPlayer,
        prefs: GeneralPrefs,
        type: AerialMediaSource,
    ): Pair<Long, Long> {
        val maxVideoLength = prefs.maxVideoLength.toLong() * 1000
        val isLengthLimited = maxVideoLength >= TEN_SECONDS
        val isShortVideo = player.duration in 1..<maxVideoLength

        if (type == AerialMediaSource.RTSP) {
            Timber.i("Calculating RTSP stream length...")
            val duration = if (isLengthLimited) maxVideoLength else 0
            return Pair(0, duration)
        }

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
            return Pair(0, duration)
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

        val message1 =
            "Video length ${duration.milliseconds}, $numOfSegments segments of ${length.milliseconds}\n"
        val message2 =
            "Chose segment $randomSegment, ${segmentStart.milliseconds} - ${segmentEnd.milliseconds}"
        Timber.i("$message1$message2")

        return Pair(segmentStart, segmentEnd)
    }

    private fun getWebDavValidateSslFromUri(uri: android.net.Uri): Boolean {
        val host = uri.host?.lowercase() ?: return true
        val port = if (uri.port == -1) null else uri.port

        if (WebDavMediaPrefs.hostName.isNotBlank()) {
            val parsed = runCatching { WebDavHostParser.parse(WebDavMediaPrefs.hostName) }.getOrNull()
            if (parsed != null && parsed.host.equals(host, ignoreCase = true)) {
                val prefPort = parsed.port ?: defaultPortFor(WebDavMediaPrefs.scheme ?: SchemeType.HTTP)
                if (port == null || port == prefPort) return WebDavMediaPrefs.validateSsl
            }
        }

        if (WebDavMediaPrefs2.hostName.isNotBlank()) {
            val parsed = runCatching { WebDavHostParser.parse(WebDavMediaPrefs2.hostName) }.getOrNull()
            if (parsed != null && parsed.host.equals(host, ignoreCase = true)) {
                val prefPort = parsed.port ?: defaultPortFor(WebDavMediaPrefs2.scheme ?: SchemeType.HTTP)
                if (port == null || port == prefPort) return WebDavMediaPrefs2.validateSsl
            }
        }

        return true
    }

    private fun calculateLoopingVideo(
        duration: Long,
        maxLength: Long,
    ): Pair<Long, Long> {
        if (duration <= 0 || maxLength < TEN_SECONDS) {
            Timber.e("Invalid duration or video length: duration=$duration, maxLength=$maxLength%")
            return Pair(0, duration)
        }
        val loopCount = ceil(maxLength / duration.toDouble()).toInt()
        val targetDuration = duration * loopCount
        Timber.i(
            "Looping $loopCount times (video is ${duration.milliseconds}, total is ${targetDuration.milliseconds}, limit is ${maxLength.milliseconds})",
        )
        return Pair(0, targetDuration)
    }
}
