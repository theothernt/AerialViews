package com.neilturner.aerialviews.services

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.rtsp.RtspMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.neilturner.aerialviews.models.enums.AerialMediaSource
import com.neilturner.aerialviews.models.enums.SchemeType
import com.neilturner.aerialviews.models.music.MusicTrack
import com.neilturner.aerialviews.models.prefs.WebDavMediaPrefs
import com.neilturner.aerialviews.models.prefs.WebDavMediaPrefs2
import com.neilturner.aerialviews.providers.immich.ImmichDataSourceFactory
import com.neilturner.aerialviews.providers.ncmemories.NCMemoriesDataSourceFactory
import com.neilturner.aerialviews.providers.samba.SambaDataSourceFactory
import com.neilturner.aerialviews.providers.webdav.WebDavDataSourceFactory
import com.neilturner.aerialviews.providers.webdav.WebDavHostParser
import com.neilturner.aerialviews.providers.webdav.defaultPortFor
import timber.log.Timber

/**
 * Audio-only player construction, extracted from the view layer so `:core` can play background
 * music without depending on `:playback`.
 */
object AudioPlayerFactory {
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

    private fun getWebDavValidateSslFromUri(uri: Uri): Boolean {
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
}
