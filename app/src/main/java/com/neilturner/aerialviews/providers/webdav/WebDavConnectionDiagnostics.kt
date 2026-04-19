package com.neilturner.aerialviews.providers.webdav

import com.neilturner.aerialviews.models.enums.SchemeType
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

internal data class WebDavEndpoint(
    val host: String,
    val explicitPort: Int?,
    val scheme: SchemeType,
    val path: String,
) {
    val effectivePort: Int
        get() = explicitPort ?: defaultPortFor(scheme)

    val authority: String
        get() = if (explicitPort != null) "$host:$explicitPort" else host

    val baseUrl: String
        get() = "${scheme.name.lowercase()}://$authority$path"

    val hasExplicitPort: Boolean
        get() = explicitPort != null
}

internal data class ParsedHostName(
    val host: String,
    val port: Int?,
)

internal sealed interface WebDavConnectionTestResult {
    data class ValidationError(
        val message: String,
    ) : WebDavConnectionTestResult

    data class ConnectionError(
        val message: String,
    ) : WebDavConnectionTestResult

    data class AuthError(
        val message: String,
    ) : WebDavConnectionTestResult

    data class PathError(
        val message: String,
    ) : WebDavConnectionTestResult

    data class SuccessSummary(
        val files: List<String>,
        val summary: String,
    ) : WebDavConnectionTestResult
}

internal object WebDavHostParser {
    fun parse(input: String): ParsedHostName {
        val trimmed = input.trim()
        require(trimmed.isNotEmpty()) { "Hostname and port not specified" }
        require(!trimmed.contains("://")) { "Enter only a hostname or IP address, with an optional port" }
        require(trimmed.none { it == '/' || it == '?' || it == '#' || it == '@' }) {
            "Enter only a hostname or IP address, with an optional port"
        }

        if (trimmed.startsWith("[")) {
            val closing = trimmed.indexOf(']')
            require(closing > 1) { "Invalid hostname" }

            val host = trimmed.substring(1, closing)
            val suffix = trimmed.substring(closing + 1)
            require(suffix.isEmpty() || suffix.startsWith(":")) { "Invalid hostname" }

            val port = suffix.removePrefix(":").takeIf { it.isNotEmpty() }?.let(::parsePort)
            return ParsedHostName(host = host, port = port)
        }

        require(trimmed.count { it == ':' } <= 1) { "Invalid hostname. IPv6 addresses must use [addr]:port format" }

        val lastColon = trimmed.lastIndexOf(':')
        if (lastColon == -1) {
            return ParsedHostName(host = trimmed, port = null)
        }

        val host = trimmed.substring(0, lastColon)
        val portText = trimmed.substring(lastColon + 1)
        require(host.isNotBlank()) { "Hostname and port not specified" }
        require(portText.isNotBlank()) { "Port must be a number between 1 and 65535" }

        return ParsedHostName(
            host = host,
            port = parsePort(portText),
        )
    }

    private fun parsePort(input: String): Int {
        val port = input.toIntOrNull() ?: throw IllegalArgumentException("Port must be a number between 1 and 65535")
        require(port in 1..65535) { "Port must be a number between 1 and 65535" }
        return port
    }
}

internal fun buildWebDavEndpoint(
    scheme: SchemeType?,
    hostName: String,
    pathName: String,
): WebDavEndpoint {
    require(!pathName.isBlank()) { "Path name not specified" }

    val parsedHost = WebDavHostParser.parse(hostName)
    return WebDavEndpoint(
        host = parsedHost.host,
        explicitPort = parsedHost.port,
        scheme = scheme ?: SchemeType.HTTP,
        path = pathName,
    )
}

internal fun defaultPortFor(scheme: SchemeType): Int =
    when (scheme) {
        SchemeType.HTTP -> 80
        SchemeType.HTTPS -> 443
    }

internal fun formatWebDavConnectionError(
    endpoint: WebDavEndpoint,
    throwable: Throwable,
): WebDavConnectionTestResult {
    val message = throwable.message.orEmpty()
    val rootCause = throwable.rootCause()

    return when {
        rootCause is UnknownHostException -> {
            WebDavConnectionTestResult.ConnectionError(
                "Cannot resolve server hostname. Please check the address.",
            )
        }

        rootCause is ConnectException || rootCause is SocketTimeoutException ||
            message.contains(
                "connection refused",
                ignoreCase = true,
            )
        -> {
            if (endpoint.hasExplicitPort) {
                WebDavConnectionTestResult.ConnectionError(
                    "Could not connect to ${endpoint.host}:${endpoint.effectivePort}. Please check the hostname, scheme, port, and that the server is running.",
                )
            } else {
                WebDavConnectionTestResult.ConnectionError(
                    "Could not connect to ${endpoint.host}:${endpoint.effectivePort}. This server may use a non-default port. Specify the port in Hostname & port.",
                )
            }
        }

        message.contains("401") || message.contains("403") || message.contains("unauthorized", ignoreCase = true) ||
            message.contains("forbidden", ignoreCase = true) -> {
            WebDavConnectionTestResult.AuthError(
                "Authentication failed. Please check the username, password, and server permissions.",
            )
        }

        message.contains("404") || message.contains("not found", ignoreCase = true) -> {
            WebDavConnectionTestResult.PathError(
                "Unable to access path ${endpoint.path}. Please check the path name and server permissions.",
            )
        }

        else -> {
            val cleanedMessage = message.ifBlank { "Unknown WebDAV error" }
            WebDavConnectionTestResult.ConnectionError(cleanedMessage)
        }
    }
}

private fun Throwable.rootCause(): Throwable {
    var current: Throwable = this
    while (current.cause != null && current.cause !== current) {
        current = current.cause!!
    }
    return current
}
