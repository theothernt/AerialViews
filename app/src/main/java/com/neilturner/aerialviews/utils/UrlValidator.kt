package com.neilturner.aerialviews.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
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

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Validates a single URL format only
     */
    fun isValidUrl(url: String): Boolean =
        try {
            UrlParser.parseServerUrl(url)
            true
        } catch (_: Exception) {
            false
        }

    /**
     * Validates a single URL with network testing and JSON validation
     */
    suspend fun validateUrlWithNetworkTest(url: String): UrlValidationResult {
        return withContext(Dispatchers.IO) {
            try {
                // First check URL format
                val parsedUrl = UrlParser.parseServerUrl(url)

                // Then test network accessibility
                val request =
                    Request
                        .Builder()
                        .url(parsedUrl)
                        .head() // Use HEAD request first to avoid downloading large content
                        .build()

                val response = okHttpClient.newCall(request).execute()

                if (!response.isSuccessful) {
                    return@withContext UrlValidationResult(
                        isValid = true,
                        isAccessible = false,
                        error = "HTTP ${response.code}: ${response.message}",
                    )
                }

                // Check if content type suggests JSON
                val contentType = response.header("content-type")?.lowercase()
                val mightBeJson = contentType?.contains("json") == true || contentType?.contains("text") == true

                if (mightBeJson) {
                    // If it might be JSON, make a GET request to verify
                    val getRequest =
                        Request
                            .Builder()
                            .url(parsedUrl)
                            .get()
                            .build()

                    val getResponse = okHttpClient.newCall(getRequest).execute()

                    if (getResponse.isSuccessful) {
                        val body = getResponse.body.string()
                        val containsJson = isValidJson(body)

                        return@withContext UrlValidationResult(
                            isValid = true,
                            isAccessible = true,
                            containsJson = containsJson,
                            error = if (!containsJson) "Response is not valid JSON" else null,
                        )
                    }
                }

                UrlValidationResult(
                    isValid = true,
                    isAccessible = true,
                    containsJson = false,
                    error = "Content does not appear to be JSON (Content-Type: $contentType)",
                )
            } catch (e: Exception) {
                Timber.w("URL validation failed: $url - ${e.message}")
                UrlValidationResult(
                    isValid = false,
                    isAccessible = false,
                    error = e.message,
                )
            }
        }
    }

    /**
     * Validates multiple URLs with network testing
     */
    suspend fun validateUrlsWithNetworkTest(urlsString: String): Map<String, UrlValidationResult> {
        if (urlsString.isBlank()) {
            return emptyMap()
        }

        val urls = urlsString.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        val results = mutableMapOf<String, UrlValidationResult>()

        for (url in urls) {
            results[url] = validateUrlWithNetworkTest(url)
        }

        return results
    }

    /**
     * Validates a comma-separated list of URLs (format only)
     * Returns a pair of (isValid, invalidUrls)
     */
    fun validateUrls(urlsString: String): Pair<Boolean, List<String>> {
        if (urlsString.isBlank()) {
            return Pair(true, emptyList()) // Empty is considered valid
        }

        val urls = urlsString.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        val invalidUrls = mutableListOf<String>()

        for (url in urls) {
            if (!isValidUrl(url)) {
                invalidUrls.add(url)
            }
        }

        return Pair(invalidUrls.isEmpty(), invalidUrls)
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
                    UrlParser.parseServerUrl(url)
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
            json.parseToJsonElement(jsonString)
            true
        } catch (_: Exception) {
            false
        }
}
