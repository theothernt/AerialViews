package com.neilturner.aerialviews.services.sonos

import android.content.Context
import android.net.wifi.WifiManager
import androidx.core.content.getSystemService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.net.URL

// Finds Sonos speakers on the local network using SSDP (UPnP discovery)
object SonosDiscovery {
    data class SonosDevice(
        val ip: String,
        val name: String,
        val model: String,
    )

    fun discoverFlow(
        context: Context,
        timeoutSeconds: Int = 4,
    ): Flow<SonosDevice> =
        channelFlow {
            val wifiManager = context.applicationContext.getSystemService<WifiManager>()
            val lock = wifiManager?.createMulticastLock("SonosDiscovery")
            lock?.setReferenceCounted(true)
            lock?.acquire()
            try {
                val multicastGroup = InetAddress.getByName("239.255.255.250")
                val msearch =
                    "M-SEARCH * HTTP/1.1\r\n" +
                        "HOST: 239.255.255.250:1900\r\n" +
                        "MAN: \"ssdp:discover\"\r\n" +
                        "MX: 3\r\n" +
                        "ST: urn:schemas-upnp-org:device:ZonePlayer:1\r\n" +
                        "\r\n"
                val seenIps = mutableSetOf<String>()

                DatagramSocket().use { socket ->
                    socket.soTimeout = 1000
                    val data = msearch.toByteArray(Charsets.UTF_8)
                    socket.send(DatagramPacket(data, data.size, multicastGroup, 1900))
                    Timber.i("SonosDiscovery: M-SEARCH sent")

                    val deadline = System.currentTimeMillis() + timeoutSeconds * 1000L
                    val buffer = ByteArray(4096)

                    while (isActive && System.currentTimeMillis() < deadline) {
                        try {
                            val packet = DatagramPacket(buffer, buffer.size)
                            socket.receive(packet)
                            val response = String(packet.data, 0, packet.length)

                            val location = extractHeader(response, "LOCATION") ?: continue
                            if (!location.contains(":${SonosClient.PORT}")) continue

                            val ip =
                                try {
                                    URL(location).host
                                } catch (_: Exception) {
                                    continue
                                }
                            if (!seenIps.add(ip)) continue

                            Timber.i("SonosDiscovery: candidate at $ip")
                            // Fetch device info concurrently so the SSDP loop keeps running
                            launch {
                                val device = fetchDeviceInfo(ip) ?: return@launch
                                send(device)
                            }
                        } catch (_: SocketTimeoutException) {
                            // keep looping until deadline or cancellation
                        }
                    }
                }
            } finally {
                if (lock?.isHeld == true) lock.release()
            }
        }.flowOn(Dispatchers.IO)

    // Convenience wrapper for non-streaming callers
    suspend fun discover(
        context: Context,
        timeoutSeconds: Int = 4,
    ): List<SonosDevice> = discoverFlow(context, timeoutSeconds).toList()

    private fun extractHeader(
        response: String,
        header: String,
    ): String? =
        response
            .lines()
            .firstOrNull { it.startsWith("$header:", ignoreCase = true) }
            ?.substringAfter(":")
            ?.trim()

    private fun fetchDeviceInfo(ip: String): SonosDevice? =
        runCatching {
            SonosClient.deviceInfo(ip)?.let { SonosDevice(ip, it.name, it.model) }
        }.getOrElse { e ->
            Timber.w("SonosDiscovery: failed to fetch info for $ip: ${e.message}")
            null
        }
}
