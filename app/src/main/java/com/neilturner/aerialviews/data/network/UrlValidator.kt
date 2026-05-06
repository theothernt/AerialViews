package com.neilturner.aerialviews.data.network

import androidx.core.net.toUri
import okhttp3.OkHttpClient
import timber.log.Timber
import java.util.concurrent.TimeUnit

data class UrlValidationResult(
    val isValid: Boolean,
    val isAccessible: Boolean = false,
    val containsJson: Boolean = false,
    val error: String? = null,
)

object UrlValidator {
    private val okHttpClient =
        OkHttpClient
            .Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()

    /**
     * Validates a single URL format only
     */
    fun isValidUrl(url: String): Boolean =
        try {
            // Check if this is an RTSP URL
            if (url.startsWith("rtsp://", ignoreCase = true)) {
                // Basic validation for RTSP URLs
                val uri = url.toUri()
                uri.host != null
            } else {
                // Use existing parser for HTTP/HTTPS URLs
                UrlParser.parseServerUrl(url)
                true
            }
        } catch (_: Exception) {
            false
        }

    /**
     * Validates a comma-separated list of URLs (format only)
     * Returns a list of pairs where first is isValid boolean and second is the URL
     */
    fun validateUrls(urlsString: String): List<Pair<Boolean, String>> {
        if (urlsString.isBlank()) {
            return emptyList()
        }

        val urls = urlsString.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        val results = mutableListOf<Pair<Boolean, String>>()

        for (url in urls) {
            val isValid = isValidUrl(url)
            results.add(Pair(isValid, url))
        }

        return results
    }

    /**
     * Parses and returns a list of valid URLs from a comma-separated string
     */
    fun parseUrls(urlsString: String?): List<String> {
        if (urlsString?.isBlank() == true) {
            return emptyList()
        }

        return urlsString
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.mapNotNull { url ->
                try {
                    // Check if this is an RTSP URL
                    if (url.startsWith("rtsp://", ignoreCase = true)) {
                        // Basic validation for RTSP URLs
                        val uri = url.toUri()
                        if (uri.host != null) {
                            url // Return the RTSP URL as-is if valid
                        } else {
                            Timber.w("Invalid RTSP URL skipped: $url - no host")
                            null
                        }
                    } else {
                        // Use existing parser for HTTP/HTTPS URLs
                        UrlParser.parseServerUrl(url)
                    }
                } catch (e: Exception) {
                    Timber.w("Invalid URL skipped: $url - ${e.message}")
                    null
                }
            } ?: emptyList()
    }

    /**
     * Checks if a string contains valid JSON
     */
    private fun isValidJson(jsonString: String): Boolean =
        try {
            JsonHelper.json.parseToJsonElement(jsonString)
            true
        } catch (_: Exception) {
            false
        }
}
