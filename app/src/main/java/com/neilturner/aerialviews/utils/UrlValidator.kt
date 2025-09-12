package com.neilturner.aerialviews.utils

import timber.log.Timber

object UrlValidator {

    /**
     * Validates a single URL
     */
    fun isValidUrl(url: String): Boolean {
        return try {
            UrlParser.parseServerUrl(url)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Validates a comma-separated list of URLs
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

        return urlsString?.split(",")
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
}
