package com.neilturner.aerialviews.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("UrlValidator Tests")
internal class UrlValidatorTest {
    @Test
    @DisplayName("parseUrls handles null input")
    fun parseUrlsHandlesNullInput() {
        val result = UrlValidator.parseUrls(null)
        assertEquals(emptyList<String>(), result)
    }

    @Test
    @DisplayName("parseUrls handles blank input")
    fun parseUrlsHandlesBlankInput() {
        val result = UrlValidator.parseUrls("   ")
        assertEquals(emptyList<String>(), result)
    }

    @Test
    @DisplayName("parseUrls handles single valid URL")
    fun parseUrlsHandlesSingleValidUrl() {
        val result = UrlValidator.parseUrls("192.168.1.1:2283")
        assertEquals(listOf("http://192.168.1.1:2283"), result)
    }

    @Test
    @DisplayName("parseUrls handles multiple valid URLs")
    fun parseUrlsHandlesMultipleValidUrls() {
        val result = UrlValidator.parseUrls("192.168.1.1:2283, https://example.com, http://test.com")
        assertEquals(
            listOf(
                "http://192.168.1.1:2283",
                "https://example.com",
                "http://test.com",
            ),
            result,
        )
    }

    @Test
    @DisplayName("parseUrls filters out invalid URLs")
    fun parseUrlsFiltersOutInvalidUrls() {
        val result = UrlValidator.parseUrls("192.168.1.1:2283, not a url, https://example.com")
        // "not a url" throws an exception during URI parsing and gets filtered out
        assertEquals(
            listOf(
                "http://192.168.1.1:2283",
                "https://example.com",
            ),
            result,
        )
    }

    @Test
    @DisplayName("parseUrls handles corrupted protocol prefix from crash")
    fun parseUrlsHandlesCorruptedProtocolPrefix() {
        // This is the exact URL from the crash log
        // The current fix should handle this by stripping the protocol and re-adding it
        val result = UrlValidator.parseUrls("http://ttp//192.168.1.186:2283")
        // After the fix, this should be cleaned up to http://192.168.1.186:2283
        assertEquals(listOf("http://192.168.1.186:2283"), result)
    }

    @Test
    @DisplayName("parseUrls handles URLs with trailing slashes")
    fun parseUrlsHandlesUrlsWithTrailingSlashes() {
        val result = UrlValidator.parseUrls("https://example.com/, http://test.com/")
        assertEquals(
            listOf(
                "https://example.com",
                "http://test.com",
            ),
            result,
        )
    }

    @Test
    @DisplayName("parseUrls handles duplicated protocol prefixes")
    fun parseUrlsHandlesDuplicatedProtocolPrefixes() {
        val result = UrlValidator.parseUrls("http://http://example.com, https://https://test.com")
        assertEquals(
            listOf(
                "http://example.com",
                "https://test.com",
            ),
            result,
        )
    }

    @Test
    @DisplayName("parseUrls trims whitespace from URLs")
    fun parseUrlsTrimsWhitespace() {
        val result = UrlValidator.parseUrls("  192.168.1.1:2283  ,  https://example.com  ")
        assertEquals(
            listOf(
                "http://192.168.1.1:2283",
                "https://example.com",
            ),
            result,
        )
    }

    @Test
    @DisplayName("isValidUrl returns true for valid URLs")
    fun isValidUrlReturnsTrueForValidUrls() {
        assertTrue(UrlValidator.isValidUrl("192.168.1.1:2283"))
        assertTrue(UrlValidator.isValidUrl("https://example.com"))
        assertTrue(UrlValidator.isValidUrl("http://test.com"))
    }

    @Test
    @DisplayName("isValidUrl returns false for invalid URLs")
    fun isValidUrlReturnsFalseForInvalidUrls() {
        assertEquals(false, UrlValidator.isValidUrl("not a url"))
    }
}
