package com.neilturner.aerialviews.providers.webdav

import com.neilturner.aerialviews.data.network.HttpLogging
import okhttp3.OkHttpClient
import timber.log.Timber
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

object WebDavSslHelper {
    fun createOkHttpClient(validateSsl: Boolean): OkHttpClient {
        val builder =
            if (validateSsl) {
                OkHttpClient.Builder()
            } else {
                createTrustAllClientBuilder()
            }

        HttpLogging.interceptor()?.let { builder.addInterceptor(it) }

        return builder.build()
    }

    private fun createTrustAllClientBuilder(): OkHttpClient.Builder {
        val trustAllCerts =
            arrayOf<TrustManager>(
                object : X509TrustManager {
                    override fun checkClientTrusted(
                        chain: Array<out X509Certificate>,
                        authType: String,
                    ) {
                    }

                    override fun checkServerTrusted(
                        chain: Array<out X509Certificate>,
                        authType: String,
                    ) {
                    }

                    override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
                },
            )

        return try {
            val sslContext =
                SSLContext.getInstance("TLS").apply {
                    // Deliberate, user-initiated bypass: this builder is only used when "Validate SSL
                    // Certificates" is switched off for a WebDAV server using a self-signed certificate.
                    // codeql[java/insecure-trustmanager]
                    init(null, trustAllCerts, SecureRandom())
                }

            OkHttpClient
                .Builder()
                .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
                .hostnameVerifier { _, _ -> true }
        } catch (e: Exception) {
            Timber.e(e, "Error setting up SSL: ${e.message}")
            OkHttpClient.Builder()
        }
    }
}
