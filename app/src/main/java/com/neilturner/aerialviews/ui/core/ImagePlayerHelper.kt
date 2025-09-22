package com.neilturner.aerialviews.ui.core

import android.net.Uri
import android.os.Build.VERSION.SDK_INT
import coil3.decode.Decoder
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import coil3.util.DebugLogger
import coil3.util.Logger
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.share.DiskShare
import com.neilturner.aerialviews.BuildConfig
import com.neilturner.aerialviews.models.enums.ImmichAuthType
import com.neilturner.aerialviews.models.prefs.ImmichMediaPrefs
import com.neilturner.aerialviews.models.prefs.SambaMediaPrefs
import com.neilturner.aerialviews.models.prefs.WebDavMediaPrefs
import com.neilturner.aerialviews.utils.FirebaseHelper
import com.neilturner.aerialviews.utils.SambaHelper
import com.neilturner.aerialviews.utils.ServerConfig
import com.neilturner.aerialviews.utils.SslHelper
import com.thegrizzlylabs.sardineandroid.impl.OkHttpSardine
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import timber.log.Timber
import java.io.InputStream
import java.util.EnumSet
import java.util.concurrent.TimeUnit

internal object ImagePlayerHelper {
    val logger: Logger? = if (BuildConfig.DEBUG) DebugLogger() else null

    fun buildGifDecoder(): Decoder.Factory =
        if (SDK_INT >= 28) {
            AnimatedImageDecoder.Factory()
        } else {
            GifDecoder.Factory()
        }

    fun buildOkHttpClient(): OkHttpClient {
        val serverConfig = ServerConfig("", ImmichMediaPrefs.validateSsl)
        val okHttpClient = SslHelper().createOkHttpClient(serverConfig)
        return okHttpClient
            .newBuilder()
            .addInterceptor(ApiKeyInterceptor())
            .build()
    }

    internal class ApiKeyInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
            val originalRequest = chain.request()
            val newRequest =
                when (ImmichMediaPrefs.authType) {
                    ImmichAuthType.API_KEY -> {
                        Timber.d("Adding X-API-Key header")
                        originalRequest
                            .newBuilder()
                            .addHeader("X-API-Key", ImmichMediaPrefs.apiKey)
                            .build()
                    }
                    else -> {
                        Timber.d("NOT Adding X-API-Key header")
                        originalRequest
                    }
                }
            return chain.proceed(newRequest)
        }
    }

    fun streamFromWebDavFile(uri: Uri): InputStream? {
        val okHttpClient =
            OkHttpClient
                .Builder()
                .callTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .build()
        val client = OkHttpSardine(okHttpClient)
        try {
            client.setCredentials(WebDavMediaPrefs.userName, WebDavMediaPrefs.password)
            return client.get(uri.toString())
        } catch (ex: Exception) {
            Timber.e(ex, "Exception while creating WebDav client: ${ex.message}")
            FirebaseHelper.crashlyticsException(ex)
            return null
        }
    }

    fun streamFromSambaFile(uri: Uri): InputStream? {
        val shareNameAndPath = SambaHelper.parseShareAndPathName(uri)
        val shareName = shareNameAndPath.first
        val path = shareNameAndPath.second
        val config = SambaHelper.buildSmbConfig()
        val smbClient = SMBClient(config)
        val authContext =
            SambaHelper.buildAuthContext(
                SambaMediaPrefs.userName,
                SambaMediaPrefs.password,
                SambaMediaPrefs.domainName,
            )

        try {
            val connection = smbClient.connect(SambaMediaPrefs.hostName)
            val session = connection?.authenticate(authContext)
            val share = session?.connectShare(shareName) as DiskShare
            val shareAccess = hashSetOf<SMB2ShareAccess>()
            shareAccess.add(SMB2ShareAccess.ALL.iterator().next())
            val file =
                share.openFile(
                    path,
                    EnumSet.of(AccessMask.GENERIC_READ),
                    null,
                    shareAccess,
                    SMB2CreateDisposition.FILE_OPEN,
                    null,
                )

            return object : InputStream() {
                private val wrappedStream = file.inputStream

                override fun read(): Int = wrappedStream.read()

                override fun read(b: ByteArray): Int = wrappedStream.read(b)

                override fun read(
                    b: ByteArray,
                    off: Int,
                    len: Int,
                ): Int = wrappedStream.read(b, off, len)

                override fun skip(n: Long): Long = wrappedStream.skip(n)

                override fun available(): Int = wrappedStream.available()

                override fun close() {
                    try {
                        wrappedStream.close()
                        file.close()
                        share.close()
                        session.close()
                        // connection.close()
                        // smbClient.close()
                    } catch (ex: Exception) {
                        Timber.e(ex, "Error closing SMB resources: ${ex.message}")
                    }
                }
            }
        } catch (ex: Exception) {
            Timber.e(ex, "Exception while opening Samba file: ${ex.message}")
            FirebaseHelper.crashlyticsException(ex)
            smbClient.close()
            return null
        }
    }
}
