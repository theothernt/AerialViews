package com.neilturner.aerialviews.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class NetworkHelperTest {
    private val context = mockk<Context>()
    private val connectivityManager = mockk<ConnectivityManager>()
    private val capabilities = mockk<NetworkCapabilities>()

    @Test
    fun `isOnWifiOrEthernet returns true when on Wifi`() {
        setupNetworkMock(hasWifi = true, hasEthernet = false)
        assertTrue(NetworkHelper.isOnWifiOrEthernet(context))
    }

    @Test
    fun `isOnWifiOrEthernet returns true when on Ethernet`() {
        setupNetworkMock(hasWifi = false, hasEthernet = true)
        assertTrue(NetworkHelper.isOnWifiOrEthernet(context))
    }

    @Test
    fun `isOnWifiOrEthernet returns false when on Cellular only`() {
        setupNetworkMock(hasWifi = false, hasEthernet = false)
        assertFalse(NetworkHelper.isOnWifiOrEthernet(context))
    }

    @Test
    fun `isOnWifiOrEthernet returns false when network capabilities unavailable`() {
        every { context.getSystemService(Context.CONNECTIVITY_SERVICE) } returns connectivityManager
        every { connectivityManager.activeNetwork } returns null

        assertFalse(NetworkHelper.isOnWifiOrEthernet(context))
    }

    private fun setupNetworkMock(
        hasWifi: Boolean,
        hasEthernet: Boolean,
    ) {
        val mockNetwork = mockk<android.net.Network>()
        every { context.getSystemService(Context.CONNECTIVITY_SERVICE) } returns connectivityManager
        every { connectivityManager.activeNetwork } returns mockNetwork
        every { connectivityManager.getNetworkCapabilities(mockNetwork) } returns capabilities
        every { capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) } returns hasWifi
        every { capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) } returns hasEthernet
    }
}
