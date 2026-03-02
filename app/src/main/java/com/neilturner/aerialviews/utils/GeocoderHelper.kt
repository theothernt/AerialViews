package com.neilturner.aerialviews.utils

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.LinkedHashMap
import java.util.Locale
import kotlin.coroutines.resume

object GeocoderHelper {
    private const val MAX_CACHE_ENTRIES = 500

    data class GeocodedLocation(
        val city: String?,
        val state: String?,
        val country: String?,
    )

    private data class CacheEntry(
        val value: GeocodedLocation?,
    )

    private val cacheLock = Any()
    private val cache =
        object : LinkedHashMap<String, CacheEntry>(MAX_CACHE_ENTRIES + 1, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CacheEntry>?): Boolean = size > MAX_CACHE_ENTRIES
        }

    suspend fun reverseGeocode(
        context: Context,
        latitude: Double,
        longitude: Double,
    ): GeocodedLocation? {
        val key = cacheKey(latitude, longitude)
        synchronized(cacheLock) {
            cache[key]?.let { return it.value }
        }

        val result = reverseGeocodeUncached(context, latitude, longitude)
        synchronized(cacheLock) {
            cache[key] = CacheEntry(result)
        }
        return result
    }

    private suspend fun reverseGeocodeUncached(
        context: Context,
        latitude: Double,
        longitude: Double,
    ): GeocodedLocation? {
        if (!Geocoder.isPresent()) {
            Timber.i("Geocoder unavailable on this device")
            return null
        }

        val geocoder = Geocoder(context, Locale.getDefault())
        val address =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                geocodeModern(geocoder, latitude, longitude)
            } else {
                geocodeLegacy(geocoder, latitude, longitude)
            }

        return address?.let(::toGeocodedLocation)
    }

    private suspend fun geocodeModern(
        geocoder: Geocoder,
        latitude: Double,
        longitude: Double,
    ): Address? =
        suspendCancellableCoroutine { continuation ->
            try {
                geocoder.getFromLocation(
                    latitude,
                    longitude,
                    1,
                    object : Geocoder.GeocodeListener {
                        override fun onGeocode(addresses: MutableList<Address>) {
                            if (continuation.isActive) {
                                continuation.resume(addresses.firstOrNull())
                            }
                        }

                        override fun onError(errorMessage: String?) {
                            if (continuation.isActive) {
                                Timber.i("Geocoder error: $errorMessage")
                                continuation.resume(null)
                            }
                        }
                    },
                )
            } catch (e: Exception) {
                Timber.e(e, "Reverse geocoding failed")
                if (continuation.isActive) {
                    continuation.resume(null)
                }
            }
        }

    @Suppress("DEPRECATION")
    private suspend fun geocodeLegacy(
        geocoder: Geocoder,
        latitude: Double,
        longitude: Double,
    ): Address? =
        withContext(Dispatchers.IO) {
            try {
                geocoder.getFromLocation(latitude, longitude, 1)?.firstOrNull()
            } catch (e: Exception) {
                Timber.e(e, "Reverse geocoding failed")
                null
            }
        }

    private fun toGeocodedLocation(address: Address): GeocodedLocation? {
        val city =
            listOf(address.locality, address.subAdminArea, address.featureName)
                .firstOrNull { !it.isNullOrBlank() }
                ?.trim()
        val state = address.adminArea?.trim()?.takeIf { it.isNotBlank() }
        val country =
            listOf(address.countryName, address.countryCode)
                .firstOrNull { !it.isNullOrBlank() }
                ?.trim()

        if (city.isNullOrBlank() && state.isNullOrBlank() && country.isNullOrBlank()) {
            return null
        }

        return GeocodedLocation(city = city, state = state, country = country)
    }

    private fun cacheKey(
        latitude: Double,
        longitude: Double,
    ): String = String.format(Locale.US, "%.4f,%.4f", latitude, longitude)
}
