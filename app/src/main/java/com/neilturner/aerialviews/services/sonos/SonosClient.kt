package com.neilturner.aerialviews.services.sonos

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber
import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

// Blocking client for the Sonos local (UPnP/SOAP) API exposed on port 1400.
// All calls perform network I/O and must run on a background thread.
object SonosClient {
    const val PORT = 1400

    const val STATE_PLAYING = "PLAYING"
    const val STATE_PAUSED = "PAUSED_PLAYBACK"
    const val STATE_STOPPED = "STOPPED"

    private const val AV_TRANSPORT = "urn:schemas-upnp-org:service:AVTransport:1"
    private const val MAX_ALBUM_ART_SIZE = 512

    data class DeviceInfo(
        val name: String,
        val model: String,
    )

    data class Track(
        val title: String,
        val artist: String,
        val albumArtUri: String,
    )

    sealed interface ProbeResult {
        data class Unreachable(
            val message: String,
        ) : ProbeResult

        data object NotSonos : ProbeResult

        data class Error(
            val message: String,
        ) : ProbeResult

        data class Connected(
            val device: DeviceInfo,
            val transportState: String,
            val track: Track?,
        ) : ProbeResult
    }

    private val xmlMediaType = "text/xml; charset=\"utf-8\"".toMediaType()

    private val client =
        OkHttpClient
            .Builder()
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(3, TimeUnit.SECONDS)
            .build()

    // Returns null if the device doesn't identify itself as a Sonos player
    fun deviceInfo(ip: String): DeviceInfo? {
        val xml = get("http://$ip:$PORT/xml/device_description.xml")
        if (!xml.contains("ZonePlayer") && !xml.contains("Sonos")) return null
        return DeviceInfo(
            name = extractTag(xml, "friendlyName") ?: ip,
            model = extractTag(xml, "modelName").orEmpty(),
        )
    }

    fun transportState(ip: String): String = extractTag(soapAction(ip, "GetTransportInfo"), "CurrentTransportState").orEmpty()

    fun isPlaying(ip: String): Boolean = transportState(ip) == STATE_PLAYING

    // Returns null when there is no track metadata available
    fun currentTrack(ip: String): Track? {
        val xml = soapAction(ip, "GetPositionInfo")
        // Track metadata is a DIDL-Lite document, XML-escaped inside the SOAP response
        val didl = extractTag(xml, "TrackMetaData")?.let(::unescapeXml) ?: return null
        val title = extractTag(didl, "dc:title").orEmpty()
        val artist = (extractTag(didl, "upnp:artist") ?: extractTag(didl, "dc:creator")).orEmpty()
        if (title.isBlank() && artist.isBlank()) return null

        val rawArtUri = extractTag(didl, "upnp:albumArtURI")?.let(::unescapeXml).orEmpty()
        val albumArtUri =
            when {
                rawArtUri.isBlank() -> ""
                rawArtUri.startsWith("http") -> rawArtUri
                else -> "http://$ip:$PORT$rawArtUri"
            }
        return Track(title = title, artist = artist, albumArtUri = albumArtUri)
    }

    // Downloads and decodes album art, downsampled so its largest side is at most MAX_ALBUM_ART_SIZE
    fun fetchAlbumArt(uri: String): Bitmap? =
        try {
            val bytes =
                client.newCall(Request.Builder().url(uri).build()).execute().use { response ->
                    if (!response.isSuccessful) {
                        Timber.w("SonosClient: album art request failed with HTTP ${response.code}")
                        return null
                    }
                    response.body.bytes()
                }

            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            var sampleSize = 1
            val largestSide = maxOf(bounds.outWidth, bounds.outHeight)
            while (largestSide / sampleSize > MAX_ALBUM_ART_SIZE) sampleSize *= 2

            val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        } catch (e: Exception) {
            Timber.w("SonosClient: album art load failed for $uri - ${e.message}")
            null
        }

    // One-shot diagnostic used by the settings screen
    fun probe(ip: String): ProbeResult {
        checkReachable(ip)?.let { return ProbeResult.Unreachable(it) }
        return try {
            val device = deviceInfo(ip) ?: return ProbeResult.NotSonos
            val state = transportState(ip).ifBlank { "UNKNOWN" }
            val track = if (state == STATE_PLAYING) currentTrack(ip) else null
            ProbeResult.Connected(device, state, track)
        } catch (e: Exception) {
            ProbeResult.Error(e.message ?: e.javaClass.simpleName)
        }
    }

    // Returns null if the Sonos port is reachable, otherwise a description of the failure
    private fun checkReachable(ip: String): String? =
        try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(ip, PORT), 4000)
            }
            null
        } catch (e: ConnectException) {
            "Cannot reach $ip:$PORT — check:\n• WiFi isolation (AP isolation) on your router\n• IP address is correct\n• Sonos is powered on"
        } catch (e: SocketTimeoutException) {
            "Timeout connecting to $ip:$PORT — device unreachable or blocked by router"
        } catch (e: Exception) {
            "Connection failed: ${e.message}"
        }

    private fun soapAction(
        ip: String,
        action: String,
    ): String {
        val envelope =
            """<?xml version="1.0"?><s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/"><s:Body><u:$action xmlns:u="$AV_TRANSPORT"><InstanceID>0</InstanceID></u:$action></s:Body></s:Envelope>"""
        val request =
            Request
                .Builder()
                .url("http://$ip:$PORT/MediaRenderer/AVTransport/Control")
                .post(envelope.toRequestBody(xmlMediaType))
                .addHeader("SOAPACTION", "\"$AV_TRANSPORT#$action\"")
                .build()
        return execute(request)
    }

    private fun get(url: String): String = execute(Request.Builder().url(url).build())

    private fun execute(request: Request): String = client.newCall(request).execute().use { it.body.string() }

    private fun extractTag(
        xml: String,
        tag: String,
    ): String? {
        val start =
            xml.indexOf("<$tag>").takeIf { it >= 0 }
                ?: xml.indexOf("<$tag ").takeIf { it >= 0 }
                ?: return null
        val contentStart = xml.indexOf('>', start) + 1
        val end = xml.indexOf("</$tag>", contentStart).takeIf { it >= 0 } ?: return null
        return xml.substring(contentStart, end).trim().takeIf { it.isNotEmpty() }
    }

    private fun unescapeXml(text: String): String =
        text
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&amp;", "&")
}
