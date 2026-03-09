package com.neilturner.aerialviews.utils

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
}
