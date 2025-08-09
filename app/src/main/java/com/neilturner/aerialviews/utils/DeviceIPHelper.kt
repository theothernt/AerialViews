package com.neilturner.aerialviews.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import androidx.annotation.RequiresApi
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Locale

object DeviceIPHelper {
    fun getIPAddress(context: Context): String {
        // Try modern method first (Android 10+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val modernIP = getIPAddressModern(context)
            if (modernIP.isNotEmpty()) {
                return modernIP
            }
        }

        // Try to get Wi-Fi IP address (legacy method)
        val wifiIP = getWifiIPAddressLegacy(context)
        if (wifiIP.isNotEmpty()) {
            return wifiIP
        }

        // Fallback to network interface enumeration
        return getNetworkInterfaceIPAddress()
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun getIPAddressModern(context: Context): String {
        try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val activeNetwork = connectivityManager.activeNetwork ?: return ""
            val networkCapabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return ""

            if (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
            ) {
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
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
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
}
