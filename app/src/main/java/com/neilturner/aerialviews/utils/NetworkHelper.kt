@file:Suppress("unused")

package com.neilturner.aerialviews.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

object NetworkHelper {
    @Suppress("DEPRECATION")
    fun isInternetAvailable(context: Context): Boolean {
        var result = false
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val networkCapabilities = connectivityManager.activeNetwork ?: return false
            val actNw =
                connectivityManager.getNetworkCapabilities(networkCapabilities) ?: return false
            result =
                when {
                    actNw.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
                    actNw.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
                    actNw.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
                    else -> false
                }
        } else {
            connectivityManager.run {
                connectivityManager.activeNetworkInfo?.run {
                    result =
                        when (type) {
                            ConnectivityManager.TYPE_WIFI -> true
                            ConnectivityManager.TYPE_MOBILE -> true
                            ConnectivityManager.TYPE_ETHERNET -> true
                            else -> false
                        }
                }
            }
        }
        return result
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
            val socket = DatagramSocket()
            withContext(Dispatchers.IO) {
                socket.send(packet)
                socket.close()
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
