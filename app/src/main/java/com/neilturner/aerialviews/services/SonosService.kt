package com.neilturner.aerialviews.services

import com.neilturner.aerialviews.models.prefs.SonosPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import me.kosert.flowbus.GlobalBus
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber
import java.util.concurrent.TimeUnit

class SonosService {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val client =
        OkHttpClient.Builder()
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(3, TimeUnit.SECONDS)
            .build()
    private var lastEvent: MusicEvent? = null

    fun start() {
        val ip = SonosPrefs.ipAddress
        val interval = SonosPrefs.pollInterval.coerceAtLeast(1).toLong()
        Timber.i("SonosService starting, ip=$ip interval=${interval}s")
        scope.launch {
            while (isActive) {
                try {
                    val event = fetchCurrentTrack(ip)
                    postIfChanged(event)
                } catch (e: Exception) {
                    Timber.w("SonosService fetch error: ${e.message}")
                    postIfChanged(MusicEvent())
                }
                delay(interval * 1000L)
            }
        }
    }

    fun stop() {
        Timber.i("SonosService stopping")
        scope.cancel()
    }

    private fun fetchCurrentTrack(ip: String): MusicEvent {
        if (!isPlaying(ip)) {
            Timber.i("SonosService: not playing, clearing overlay")
            return MusicEvent()
        }
        val soapEnvelope = """<?xml version="1.0"?><s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/"><s:Body><u:GetPositionInfo xmlns:u="urn:schemas-upnp-org:service:AVTransport:1"><InstanceID>0</InstanceID></u:GetPositionInfo></s:Body></s:Envelope>"""
        val request =
            Request.Builder()
                .url("http://$ip:1400/MediaRenderer/AVTransport/Control")
                .post(soapEnvelope.toRequestBody("text/xml; charset=\"utf-8\"".toMediaType()))
                .addHeader("SOAPACTION", "\"urn:schemas-upnp-org:service:AVTransport:1#GetPositionInfo\"")
                .build()
        val body = client.newCall(request).execute().use { it.body?.string() ?: "" }
        if (body.isBlank()) return MusicEvent()
        return parseTrackResponse(body, ip)
    }

    private fun isPlaying(ip: String): Boolean {
        val soapEnvelope = """<?xml version="1.0"?><s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/"><s:Body><u:GetTransportInfo xmlns:u="urn:schemas-upnp-org:service:AVTransport:1"><InstanceID>0</InstanceID></u:GetTransportInfo></s:Body></s:Envelope>"""
        val request =
            Request.Builder()
                .url("http://$ip:1400/MediaRenderer/AVTransport/Control")
                .post(soapEnvelope.toRequestBody("text/xml; charset=\"utf-8\"".toMediaType()))
                .addHeader("SOAPACTION", "\"urn:schemas-upnp-org:service:AVTransport:1#GetTransportInfo\"")
                .build()
        val body = client.newCall(request).execute().use { it.body?.string() ?: "" }
        val state = extractXmlTag(body, "CurrentTransportState") ?: ""
        Timber.i("SonosService transport state: '$state'")
        return state == "PLAYING"
    }

    private fun parseTrackResponse(xml: String, ip: String): MusicEvent {
        val didlEncoded = extractXmlTag(xml, "TrackMetaData") ?: return MusicEvent()
        if (didlEncoded.isBlank()) return MusicEvent()
        val didl = unescapeXmlEntities(didlEncoded)
        val title = extractXmlTag(didl, "dc:title") ?: ""
        val artist = extractXmlTag(didl, "upnp:artist") ?: extractXmlTag(didl, "dc:creator") ?: ""
        Timber.i("SonosService parsed: artist='$artist' title='$title'")
        if (title.isBlank() && artist.isBlank()) return MusicEvent()
        val rawArtUri = extractXmlTag(didl, "upnp:albumArtURI")?.let { unescapeXmlEntities(it) } ?: ""
        val albumArtUri =
            when {
                rawArtUri.isBlank() -> ""
                rawArtUri.startsWith("http") -> rawArtUri
                else -> "http://$ip:1400$rawArtUri"
            }
        Timber.i("SonosService albumArtUri: raw='$rawArtUri' resolved='$albumArtUri'")
        return MusicEvent(artist = artist, song = title, albumArtUri = albumArtUri)
    }

    private fun extractXmlTag(
        xml: String,
        tag: String,
    ): String? {
        val start = xml.indexOf("<$tag>").takeIf { it >= 0 } ?: xml.indexOf("<$tag ").takeIf { it >= 0 } ?: return null
        val contentStart = xml.indexOf('>', start) + 1
        val end = xml.indexOf("</$tag>", contentStart).takeIf { it >= 0 } ?: return null
        return xml.substring(contentStart, end)
    }

    private fun unescapeXmlEntities(text: String): String =
        text
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&amp;", "&")

    private fun postIfChanged(event: MusicEvent) {
        if (event == lastEvent) return
        lastEvent = event
        Timber.i("SonosService posting: $event")
        GlobalBus.post(event)
    }
}
