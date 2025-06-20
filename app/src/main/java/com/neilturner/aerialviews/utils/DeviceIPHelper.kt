package com.neilturner.aerialviews.utils

import android.content.Context
import android.net.wifi.WifiManager
import java.net.Inet4Address
import java.net.NetworkInterface

object DeviceIPHelper {
    fun getIPAddress(context: Context): String {
        // Try to get Wi-Fi IP address first
        val wifiIP = getWifiIPAddress(context)
        if (wifiIP.isNotEmpty()) {
            return wifiIP
        }
        
        // Fallback to network interface enumeration
        return getNetworkInterfaceIPAddress()
    }
    
    private fun getWifiIPAddress(context: Context): String {
        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val wifiInfo = wifiManager.connectionInfo
            val ip = wifiInfo.ipAddress
            
            if (ip != 0) {
                return String.format(
                    "%d.%d.%d.%d",
                    (ip and 0xff),
                    (ip shr 8 and 0xff),
                    (ip shr 16 and 0xff),
                    (ip shr 24 and 0xff)
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
