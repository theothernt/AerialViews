package com.neilturner.aerialviews.providers.webdav

import com.neilturner.aerialviews.models.enums.SchemeType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.net.ConnectException
import java.net.UnknownHostException

@DisplayName("WebDavConnectionDiagnostics Tests")
internal class WebDavConnectionDiagnosticsTest {
    @Test
    fun `parses domain without port`() {
        val parsed = WebDavHostParser.parse("example.com")

        assertEquals("example.com", parsed.host)
        assertEquals(null, parsed.port)
    }

    @Test
    fun `parses ipv4 without port`() {
        val parsed = WebDavHostParser.parse("192.168.1.2")

        assertEquals("192.168.1.2", parsed.host)
        assertEquals(null, parsed.port)
    }

    @Test
    fun `parses host with explicit port`() {
        val parsed = WebDavHostParser.parse("example.com:5005")

        assertEquals("example.com", parsed.host)
        assertEquals(5005, parsed.port)
    }

    @Test
    fun `rejects full url in hostname field`() {
        assertThrows(IllegalArgumentException::class.java) {
            WebDavHostParser.parse("https://example.com:5005")
        }
    }

    @Test
    fun `rejects invalid port text`() {
        assertThrows(IllegalArgumentException::class.java) {
            WebDavHostParser.parse("example.com:abc")
        }
    }

    @Test
    fun `formats implicit http connection failure with default port hint`() {
        val endpoint = buildWebDavEndpoint(SchemeType.HTTP, "example.com", "/media")

        val result = formatWebDavConnectionError(endpoint, ConnectException("Connection refused"))

        val error = assertInstanceOf(WebDavConnectionTestResult.ConnectionError::class.java, result)
        assertEquals(
            "Could not connect to example.com:80. This server may use a non-default port. Specify the port in Hostname & port.",
            error.message,
        )
    }

    @Test
    fun `formats implicit https connection failure with default port hint`() {
        val endpoint = buildWebDavEndpoint(SchemeType.HTTPS, "example.com", "/media")

        val result = formatWebDavConnectionError(endpoint, ConnectException("Connection refused"))

        val error = assertInstanceOf(WebDavConnectionTestResult.ConnectionError::class.java, result)
        assertEquals(
            "Could not connect to example.com:443. This server may use a non-default port. Specify the port in Hostname & port.",
            error.message,
        )
    }

    @Test
    fun `formats explicit custom port failure without default port hint`() {
        val endpoint = buildWebDavEndpoint(SchemeType.HTTP, "example.com:5005", "/media")

        val result = formatWebDavConnectionError(endpoint, ConnectException("Connection refused"))

        val error = assertInstanceOf(WebDavConnectionTestResult.ConnectionError::class.java, result)
        assertEquals(
            "Could not connect to example.com:5005. Please check the hostname, scheme, port, and that the server is running.",
            error.message,
        )
    }

    @Test
    fun `formats unknown host failure`() {
        val endpoint = buildWebDavEndpoint(SchemeType.HTTP, "missing.example", "/media")

        val result = formatWebDavConnectionError(endpoint, UnknownHostException("missing.example"))

        val error = assertInstanceOf(WebDavConnectionTestResult.ConnectionError::class.java, result)
        assertEquals("Cannot resolve server hostname. Please check the address.", error.message)
    }

    @Test
    fun `formats auth failure`() {
        val endpoint = buildWebDavEndpoint(SchemeType.HTTP, "example.com", "/media")

        val result = formatWebDavConnectionError(endpoint, IllegalStateException("HTTP 401 Unauthorized"))

        val error = assertInstanceOf(WebDavConnectionTestResult.AuthError::class.java, result)
        assertEquals(
            "Authentication failed. Please check the username, password, and server permissions.",
            error.message,
        )
    }

    @Test
    fun `formats missing path failure`() {
        val endpoint = buildWebDavEndpoint(SchemeType.HTTP, "example.com", "/missing")

        val result = formatWebDavConnectionError(endpoint, IllegalStateException("HTTP 404 Not Found"))

        val error = assertInstanceOf(WebDavConnectionTestResult.PathError::class.java, result)
        assertEquals(
            "Unable to access path /missing. Please check the path name and server permissions.",
            error.message,
        )
    }
}
