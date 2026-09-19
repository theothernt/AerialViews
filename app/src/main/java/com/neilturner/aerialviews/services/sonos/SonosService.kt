package com.neilturner.aerialviews.services.sonos

import com.neilturner.aerialviews.services.MusicEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import me.kosert.flowbus.GlobalBus
import timber.log.Timber
import kotlin.time.Duration.Companion.seconds

// Polls a Sonos speaker on the local network and broadcasts the current
// track (including album art) as a MusicEvent, like NowPlayingService does
class SonosService(
    private val ip: String,
    pollIntervalSeconds: Int,
) {
    private val pollInterval = pollIntervalSeconds.coerceAtLeast(1).seconds
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var lastTrack: SonosClient.Track? = null

    fun start() {
        Timber.i("SonosService starting, ip=$ip interval=$pollInterval")
        scope.launch {
            while (isActive) {
                poll()
                delay(pollInterval)
            }
        }
    }

    fun stop() {
        Timber.i("SonosService stopping")
        scope.cancel()
    }

    private fun poll() {
        val track =
            try {
                if (SonosClient.isPlaying(ip)) {
                    SonosClient.currentTrack(ip)
                } else {
                    Timber.i("SonosService: not playing")
                    null
                }
            } catch (e: Exception) {
                Timber.w("SonosService fetch error: ${e.message}")
                null
            }

        if (track == lastTrack) return
        lastTrack = track
        broadcast(track)
    }

    private fun broadcast(track: SonosClient.Track?) {
        val event =
            track?.let {
                val albumArt = it.albumArtUri.takeIf { uri -> uri.isNotBlank() }?.let(SonosClient::fetchAlbumArt)
                MusicEvent(artist = it.artist, song = it.title, albumArt = albumArt)
            } ?: MusicEvent()

        Timber.i("SonosService posting: artist='${event.artist}' song='${event.song}' albumArt=${event.albumArt != null}")
        GlobalBus.post(event)
    }
}
