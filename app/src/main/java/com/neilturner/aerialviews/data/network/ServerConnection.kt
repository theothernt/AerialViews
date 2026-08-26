package com.neilturner.aerialviews.data.network

import android.annotation.SuppressLint
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import timber.log.Timber
import java.net.URI
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.regex.Pattern
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

data class ServerConfig(
    val url: String,
    val validateCertificates: Boolean = true,
)

object UrlParser {
    private val IPV4_PATTERN =
        Pattern.compile(
            "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$",
        )
    private val HOSTNAME_PATTERN =
        Pattern.compile(
            "^([a-zA-Z0-9]([a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?\\.)+[a-zA-Z]{2,}$",
        )
    private val SINGLE_LABEL_HOSTNAME_PATTERN =
        Pattern.compile(
            "^(?![0-9]+$)[a-zA-Z0-9]([a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?$",
        )

    fun parseServerUrl(input: String): String {
        if (input.isBlank()) return ""

        var processedUrl = input.trim()

        // Check if the original URL had https before stripping
        val originallyHttps =
            processedUrl.startsWith("https://", ignoreCase = true) ||
                processedUrl.startsWith("https:/", ignoreCase = true) ||
                processedUrl.startsWith("https:", ignoreCase = true)

        // Strip any number of leading protocol prefixes, including mangled ones
        // Handles cases like "http://http://...", "http://ttp//...", "https://https://..." etc.
        // First strip well-formed protocols, then strip any remaining partial protocol
        processedUrl = processedUrl.replace(Regex("^(https?://?)+", RegexOption.IGNORE_CASE), "")
        // Also handle mangled protocols like "http://ttp//", "https://tps//", etc.
        processedUrl = processedUrl.replace(Regex("^(https?://)?[a-z]*//+", RegexOption.IGNORE_CASE), "")

        // Now determine the correct protocol to prepend based on original URL
        processedUrl =
            if (originallyHttps) {
                "https://$processedUrl"
            } else {
                "http://$processedUrl"
            }

        try {
            val uri = URI(processedUrl)

            if (uri.host == null) {
                throw IllegalArgumentException("Invalid URL")
            }

            if (uri.scheme !in setOf("http", "https")) {
                throw IllegalArgumentException("Invalid URL")
            }

            if (!isValidHost(uri.host)) {
                throw IllegalArgumentException("Invalid host: ${uri.host}")
            }

            if (processedUrl.endsWith("/")) {
                processedUrl = processedUrl.dropLast(1)
            }

            return processedUrl
        } catch (e: Exception) {
            Timber.e(e, "URL parsing failed: ${e.message}")
            throw IllegalArgumentException("Invalid URL format: ${e.message}")
        }
    }

    private fun isValidHost(host: String): Boolean =
        IPV4_PATTERN.matcher(host).matches() ||
            HOSTNAME_PATTERN.matcher(host).matches() ||
            SINGLE_LABEL_HOSTNAME_PATTERN.matcher(host).matches() ||
            host.equals("localhost", ignoreCase = true)
}

class SslHelper {
    fun createOkHttpClient(config: ServerConfig): OkHttpClient {
        val builder = OkHttpClient.Builder()

        val logging =
            HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.HEADERS
            }

        builder.addInterceptor(logging)

        if (!config.validateCertificates) {
            val trustAllCerts =
                arrayOf<TrustManager>(
                    @SuppressLint("CustomX509TrustManager")
                    object : X509TrustManager {
                        @SuppressLint("TrustAllX509TrustManager")
                        override fun checkClientTrusted(
                            chain: Array<out X509Certificate>,
                            authType: String,
                        ) {}

                        @SuppressLint("TrustAllX509TrustManager")
                        override fun checkServerTrusted(
                            chain: Array<out X509Certificate>,
                            authType: String,
                        ) {}

                        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
                    },
                )

            try {
                val sslContext =
                    SSLContext.getInstance("TLS").apply {
                        init(null, trustAllCerts, SecureRandom())
                    }

                builder.sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
                builder.hostnameVerifier { _, _ -> true }
            } catch (e: Exception) {
                Timber.e(e, "Error setting up SSL: ${e.message}")
            }
        }

        return builder.build()
    }
}
