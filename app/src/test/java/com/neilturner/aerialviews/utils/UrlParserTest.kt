package com.neilturner.aerialviews.utils

import com.neilturner.aerialviews.data.network.UrlParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("UrlParser Tests")
internal class UrlParserTest {
    @Test
    @DisplayName("Parses local IP host without protocol")
    fun parsesLocalIpWithoutProtocol() {
        val parsed = UrlParser.parseServerUrl("192.168.1.1")
        assertEquals("http://192.168.1.1", parsed)
    }

    @Test
    @DisplayName("Parses local IP host with port")
    fun parsesLocalIpWithPort() {
        val parsed = UrlParser.parseServerUrl("192.168.1.1:2283")
        assertEquals("http://192.168.1.1:2283", parsed)
    }

    @Test
    @DisplayName("Parses FQDN without protocol")
    fun parsesFqdnWithoutProtocol() {
        val parsed = UrlParser.parseServerUrl("immich.example.com")
        assertEquals("http://immich.example.com", parsed)
    }

    @Test
    @DisplayName("Keeps HTTPS protocol when provided")
    fun keepsHttpsProtocol() {
        val parsed = UrlParser.parseServerUrl("https://immich.example.com")
        assertEquals("https://immich.example.com", parsed)
    }

    @Test
    @DisplayName("Removes trailing slash from valid URL")
    fun removesTrailingSlash() {
        val parsed = UrlParser.parseServerUrl("https://immich.example.com/")
        assertEquals("https://immich.example.com", parsed)
    }

    @Test
    @DisplayName("Throws for invalid URL input")
    fun throwsForInvalidUrl() {
        assertThrows(IllegalArgumentException::class.java) {
            UrlParser.parseServerUrl("not a valid url")
        }
    }

    @Test
    @DisplayName("Returns empty for blank input")
    fun returnsEmptyForBlankInput() {
        val parsed = UrlParser.parseServerUrl("   ")
        assertEquals("", parsed)
    }

    @Test
    @DisplayName("Handles corrupted protocol prefix http://ttp//")
    fun handlesCorruptedProtocolPrefix() {
        val parsed = UrlParser.parseServerUrl("http://ttp//192.168.1.186:2283")
        assertEquals("http://192.168.1.186:2283", parsed)
    }

    @Test
    @DisplayName("Handles duplicated http:// protocol")
    fun handlesDuplicatedHttpProtocol() {
        val parsed = UrlParser.parseServerUrl("http://http://192.168.1.186:2283")
        assertEquals("http://192.168.1.186:2283", parsed)
    }

    @Test
    @DisplayName("Handles duplicated https:// protocol")
    fun handlesDuplicatedHttpsProtocol() {
        val parsed = UrlParser.parseServerUrl("https://https://example.com")
        assertEquals("https://example.com", parsed)
    }

    @Test
    @DisplayName("Handles mixed protocol prefixes http://https://")
    fun handlesMixedProtocolPrefixes() {
        // When the URL starts with http:// first, it's treated as HTTP
        // even if it contains https:// later
        val parsed = UrlParser.parseServerUrl("http://https://example.com")
        assertEquals("http://example.com", parsed)
    }

    @Test
    @DisplayName("Handles protocol with single slash http:/")
    fun handlesProtocolWithSingleSlash() {
        val parsed = UrlParser.parseServerUrl("http:/example.com")
        assertEquals("http://example.com", parsed)
    }

    @Test
    @DisplayName("Handles multiple mixed protocol prefixes")
    fun handlesMultipleMixedProtocolPrefixes() {
        val parsed = UrlParser.parseServerUrl("https://http://https://example.com")
        assertEquals("https://example.com", parsed)
    }

    @Test
    @DisplayName("Handles IP address with corrupted protocol")
    fun handlesIpAddressWithCorruptedProtocol() {
        val parsed = UrlParser.parseServerUrl("http://ttp//10.0.0.1:8080")
        assertEquals("http://10.0.0.1:8080", parsed)
    }

    @Test
    @DisplayName("Handles FQDN with corrupted protocol")
    fun handlesFqdnWithCorruptedProtocol() {
        val parsed = UrlParser.parseServerUrl("http://ttp//immich.example.com")
        assertEquals("http://immich.example.com", parsed)
    }

    @Test
    @DisplayName("Handles https with corrupted prefix")
    fun handlesHttpsWithCorruptedPrefix() {
        val parsed = UrlParser.parseServerUrl("https://ttps//example.com")
        assertEquals("https://example.com", parsed)
    }

    @Test
    @DisplayName("Handles URL with port and trailing slash")
    fun handlesUrlWithPortAndTrailingSlash() {
        val parsed = UrlParser.parseServerUrl("http://192.168.1.1:2283/")
        assertEquals("http://192.168.1.1:2283", parsed)
    }

    @Test
    @DisplayName("Handles various malformed protocol variations")
    fun handlesVariousMalformedProtocolVariations() {
        // Test case-insensitive handling
        val parsed1 = UrlParser.parseServerUrl("HTTP://HTTP://example.com")
        assertEquals("http://example.com", parsed1)

        val parsed2 = UrlParser.parseServerUrl("HTTP://httP://example.com")
        assertEquals("http://example.com", parsed2)

        val parsed3 = UrlParser.parseServerUrl("hTTp://example.com")
        assertEquals("http://example.com", parsed3)
    }
}
