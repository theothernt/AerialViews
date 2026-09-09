package com.neilturner.aerialviews.providers.webdav

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import timber.log.Timber
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

internal object WebDavSslHelper {
    fun createOkHttpClient(validateSsl: Boolean): OkHttpClient {
        val builder =
            if (validateSsl) {
                OkHttpClient.Builder()
            } else {
                createTrustAllClientBuilder()
            }

        val logging =
            HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.HEADERS
            }
        builder.addInterceptor(logging)

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
