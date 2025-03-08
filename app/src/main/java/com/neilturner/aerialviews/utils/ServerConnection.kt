package com.neilturner.aerialviews.utils

import android.annotation.SuppressLint
import androidx.core.net.toUri
import okhttp3.OkHttpClient
import timber.log.Timber
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

data class ServerConfig(
    val url: String,
    val validateCertificates: Boolean = true,
)

object UrlParser {
    fun parseServerUrl(input: String): String {
        if (input.isBlank()) return ""

        // Remove any leading/trailing whitespace
        var processedUrl = input.trim()

        // Check if the URL starts with a protocol
        if (!processedUrl.startsWith("http://", ignoreCase = true) &&
            !processedUrl.startsWith("https://", ignoreCase = true)
        ) {
            // If no protocol is specified, prepend http://
            processedUrl = "http://$processedUrl"
        }

        try {
            val uri = processedUrl.toUri()
            // Validate basic URL components
            if (uri.host == null) {
                throw IllegalArgumentException("Invalid URL: Missing host")
            }
            return processedUrl
        } catch (e: Exception) {
            Timber.e(e, "URL parsing failed: ${e.message}")
            throw IllegalArgumentException("Invalid URL format: ${e.message}")
        }
    }
}

class SslHelper {
    fun createOkHttpClient(config: ServerConfig): OkHttpClient {
        val builder = OkHttpClient.Builder()

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
