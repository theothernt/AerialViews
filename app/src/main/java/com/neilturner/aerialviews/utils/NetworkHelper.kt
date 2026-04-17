@file:Suppress("unused")

package com.neilturner.aerialviews.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.util.Locale

object NetworkHelper {
    /**
     * Get network capabilities for the active network.
     * Returns null if no active network or capabilities cannot be determined.
     */
    @Suppress("DEPRECATION")
    fun getActiveNetworkCapabilities(context: Context): NetworkCapabilities? {
        val connectivityManager =
            context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return null
        return connectivityManager.getNetworkCapabilities(network)
    }

    @Suppress("DEPRECATION")
    fun isInternetAvailable(context: Context): Boolean {
        val actNw = getActiveNetworkCapabilities(context) ?: return false
        return when {
            actNw.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
            actNw.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
            actNw.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
            else -> false
        }
    }

    fun isNetworkAvailable(context: Context): Boolean {
        val capabilities = getActiveNetworkCapabilities(context) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun getIPAddress(context: Context): String {
        // Try modern method first (Android 10+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val modernIP = getIPAddressModern(context)
            if (modernIP.isNotEmpty()) {
                return modernIP
            }
        }

        // Try Wi-Fi IP address (legacy method)
        val wifiIP = getWifiIPAddressLegacy(context)
        if (wifiIP.isNotEmpty()) {
            return wifiIP
        }

        // Fallback to network interface enumeration
        return getNetworkInterfaceIPAddress()
    }

    @Suppress("DEPRECATION")
    private fun getIPAddressModern(context: Context): String {
        try {
            val networkCapabilities = getActiveNetworkCapabilities(context) ?: return ""

            if (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
            ) {
                val connectivityManager =
                    context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                val activeNetwork = connectivityManager.activeNetwork ?: return ""
                val linkProperties = connectivityManager.getLinkProperties(activeNetwork) ?: return ""

                for (linkAddress in linkProperties.linkAddresses) {
                    val address = linkAddress.address
                    if (address is Inet4Address && !address.isLoopbackAddress) {
                        return address.hostAddress ?: ""
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore and try fallback method
        }
        return ""
    }

    @Suppress("DEPRECATION")
    private fun getWifiIPAddressLegacy(context: Context): String {
        try {
            val wifiManager =
                context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val wifiInfo = wifiManager.connectionInfo
            val ip = wifiInfo.ipAddress

            if (ip != 0) {
                return String.format(
                    Locale.getDefault(),
                    "%d.%d.%d.%d",
                    (ip and 0xff),
                    (ip shr 8 and 0xff),
                    (ip shr 16 and 0xff),
                    (ip shr 24 and 0xff),
                )
            }
        } catch (e: Exception) {
            // Ignore and try alternative method
        }
        return ""
    }

    private fun getNetworkInterfaceIPAddress(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            for (networkInterface in interfaces) {
                if (networkInterface.isLoopback || !networkInterface.isUp) {
                    continue
                }

                val addresses = networkInterface.inetAddresses
                for (address in addresses) {
                    if (address is Inet4Address && !address.isLoopbackAddress) {
                        return address.hostAddress ?: ""
                    }
                }
            }
        } catch (e: Exception) {
            // Return empty if failed
        }
        return "Unable to determine IP"
    }

    suspend fun isHostReachable(
        hostname: String,
        port: Int,
    ): Boolean =
        withContext(Dispatchers.IO) {
            try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(hostname, port), 3000)
                    true
                }
            } catch (e: Exception) {
                Timber.e(e, "ServerCheck: Failed to connect to $hostname")
                false
            }
        }

    suspend fun sendWakeOnLan(macAddress: String) {
        if (macAddress.isBlank()) {
            Timber.e("WOL: MAC address is empty")
            return
        }

        try {
            withContext(Dispatchers.IO) {
                val macBytes = getMacBytes(macAddress)
                val bytes = ByteArray(6 + 16 * macBytes.size)
                for (i in 0..5) {
                    bytes[i] = 0xff.toByte()
                }
                for (i in 6 until bytes.size step macBytes.size) {
                    System.arraycopy(macBytes, 0, bytes, i, macBytes.size)
                }

                val address = InetAddress.getByName("255.255.255.255")
                val packet = DatagramPacket(bytes, bytes.size, address, 9)
                DatagramSocket().use { socket ->
                    socket.send(packet)
                }
            }
            Timber.i("WOL: Packet sent to $macAddress")
        } catch (e: Exception) {
            Timber.e(e, "WOL: Failed to send packet")
        }
    }

    fun isValidMacAddress(macAddress: String): Boolean =
        try {
            getMacBytes(macAddress)
            true
        } catch (e: IllegalArgumentException) {
            false
        }

    private fun getMacBytes(macStr: String): ByteArray {
        val bytes = ByteArray(6)
        val hex = macStr.split(":", "-")
        if (hex.size != 6) {
            throw IllegalArgumentException("Invalid MAC address")
        }
        try {
            for (i in 0..5) {
                bytes[i] = Integer.parseInt(hex[i], 16).toByte()
            }
        } catch (e: NumberFormatException) {
            throw IllegalArgumentException("Invalid hex digit in MAC address")
        }
        return bytes
    }
}
