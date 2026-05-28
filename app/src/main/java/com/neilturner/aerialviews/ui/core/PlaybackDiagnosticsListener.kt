package com.neilturner.aerialviews.ui.core

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.annotation.OptIn
import androidx.media3.common.Format
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DecoderCounters
import androidx.media3.exoplayer.analytics.AnalyticsListener
import timber.log.Timber
import java.util.concurrent.TimeUnit

@OptIn(UnstableApi::class)
class PlaybackDiagnosticsListener(private val context: Context) : AnalyticsListener {

    private var startTimeMs: Long = 0
    private var totalBufferingTimeMs: Long = 0
    private var lastBufferingStartTimeMs: Long = 0
    private var videoDecoderName: String = "Unknown"
    private var audioDecoderName: String = "Unknown"
    private var totalBytesLoaded: Long = 0
    private var lastBandwidthEstimate: Long = 0

    private var videoDecoderCounters: DecoderCounters? = null
    private var audioDecoderCounters: DecoderCounters? = null

    override fun onVideoEnabled(eventTime: AnalyticsListener.EventTime, decoderCounters: DecoderCounters) {
        videoDecoderCounters = decoderCounters
    }

    override fun onAudioEnabled(eventTime: AnalyticsListener.EventTime, decoderCounters: DecoderCounters) {
        audioDecoderCounters = decoderCounters
    }

    override fun onPlaybackStateChanged(eventTime: AnalyticsListener.EventTime, state: Int) {
        when (state) {
            AnalyticsListener.STATE_READY -> {
                if (startTimeMs == 0L) startTimeMs = System.currentTimeMillis()
                if (lastBufferingStartTimeMs != 0L) {
                    totalBufferingTimeMs += (System.currentTimeMillis() - lastBufferingStartTimeMs)
                    lastBufferingStartTimeMs = 0
                }
                Timber.d("Playback State: READY (Total Buffering: ${totalBufferingTimeMs}ms)")
            }
            AnalyticsListener.STATE_BUFFERING -> {
                lastBufferingStartTimeMs = System.currentTimeMillis()
                Timber.d("Playback State: BUFFERING (Network: ${getNetworkType()})")
            }
            AnalyticsListener.STATE_ENDED -> {
                logSessionSummary()
            }
        }
    }

    override fun onVideoDecoderInitialized(
        eventTime: AnalyticsListener.EventTime,
        decoderName: String,
        initializedTimestampMs: Long,
        initializationDurationMs: Long
    ) {
        videoDecoderName = decoderName
        Timber.i("Video Decoder Initialized: $decoderName (took ${initializationDurationMs}ms)")
    }

    override fun onAudioDecoderInitialized(
        eventTime: AnalyticsListener.EventTime,
        decoderName: String,
        initializedTimestampMs: Long,
        initializationDurationMs: Long
    ) {
        audioDecoderName = decoderName
        Timber.i("Audio Decoder Initialized: $decoderName (took ${initializationDurationMs}ms)")
    }

    override fun onDroppedVideoFrames(
        eventTime: AnalyticsListener.EventTime,
        droppedFrames: Int,
        elapsedMs: Long
    ) {
        if (droppedFrames > 5) {
            Timber.w("Dropped $droppedFrames frames in ${elapsedMs}ms (Potential performance issue!)")
        }
    }

    override fun onAudioUnderrun(
        eventTime: AnalyticsListener.EventTime,
        bufferSize: Int,
        bufferSizeMs: Long,
        elapsedSinceLastFeedMs: Long
    ) {
        Timber.w("Audio Underrun: bufferSizeMs=${bufferSizeMs}ms, elapsedSinceLastFeed=${elapsedSinceLastFeedMs}ms")
    }

    override fun onBandwidthEstimate(
        eventTime: AnalyticsListener.EventTime,
        totalBytesTransferred: Long,
        totalTransferTimeMs: Long,
        bitrateEstimate: Long
    ) {
        lastBandwidthEstimate = bitrateEstimate
        totalBytesLoaded = totalBytesTransferred
    }

    override fun onPlayerError(eventTime: AnalyticsListener.EventTime, error: PlaybackException) {
        Timber.e(error, "Playback Error: ${error.errorCodeName} (Network: ${getNetworkType()})")
        logSessionSummary()
    }

    override fun onVideoInputFormatChanged(
        eventTime: AnalyticsListener.EventTime,
        format: Format,
        decoderReuseEvaluation: androidx.media3.exoplayer.DecoderReuseEvaluation?
    ) {
        Timber.i("Video Format: ${format.sampleMimeType}, ${format.width}x${format.height}, ${format.frameRate}fps, Bitrate: ${format.bitrate}")
    }

    private fun logSessionSummary() {
        val totalTimeMs = if (startTimeMs > 0) System.currentTimeMillis() - startTimeMs else 0
        val playTimeMs = totalTimeMs - totalBufferingTimeMs
        
        val summary = StringBuilder().apply {
            append("\n--- Playback Session Summary ---\n")
            append("Total Duration: ${formatTime(totalTimeMs)}\n")
            append("Actual Play Time: ${formatTime(playTimeMs)}\n")
            append("Total Buffering: ${formatTime(totalBufferingTimeMs)}\n")
            append("Average Bandwidth: ${lastBandwidthEstimate / 1000} Kbps\n")
            append("Video Decoder: $videoDecoderName\n")
            append("Audio Decoder: $audioDecoderName\n")
            
            videoDecoderCounters?.let { counters ->
                counters.ensureUpdated()
                append("Video Frames: Rendered=${counters.renderedOutputBufferCount}, Dropped=${counters.droppedBufferCount}, Skipped=${counters.skippedOutputBufferCount}\n")
            }
            audioDecoderCounters?.let { counters ->
                counters.ensureUpdated()
                append("Audio Buffers: Rendered=${counters.renderedOutputBufferCount}, Skipped=${counters.skippedOutputBufferCount}\n")
            }
            append("--------------------------------\n")
        }.toString()
        
        Timber.i(summary)
    }

    private fun formatTime(ms: Long): String {
        return String.format("%02d:%02d:%02d", 
            TimeUnit.MILLISECONDS.toHours(ms),
            TimeUnit.MILLISECONDS.toMinutes(ms) % 60,
            TimeUnit.MILLISECONDS.toSeconds(ms) % 60)
    }

    private fun getNetworkType(): String {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return "Disconnected"
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return "Unknown"
        return when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular"
            else -> "Other"
        }
    }
}
