package com.neilturner.aerialviews.ui.core

import android.content.Context
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
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.share.DiskShare
import com.neilturner.aerialviews.BuildConfig
import com.neilturner.aerialviews.models.enums.AerialMediaSource
import com.neilturner.aerialviews.models.enums.ImmichAuthType
import com.neilturner.aerialviews.models.prefs.ImmichMediaPrefs
import com.neilturner.aerialviews.models.videos.AerialMedia
import com.neilturner.aerialviews.utils.FirebaseHelper
import com.neilturner.aerialviews.utils.SambaHelper
import com.neilturner.aerialviews.utils.ServerConfig
import com.neilturner.aerialviews.utils.SslHelper
import com.thegrizzlylabs.sardineandroid.impl.OkHttpSardine
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.FilterInputStream
import java.io.InputStream
import java.util.EnumSet

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
        val baseClient = buildOkHttpClient()
        val okHttpClient = baseClient.newBuilder().build()
        val client = OkHttpSardine(okHttpClient)
        val (userName, password) = SambaHelper.parseUserInfo(uri)
        try {
            client.setCredentials(userName, password)
            return client.get(uri.toString())
        } catch (ex: Exception) {
            Timber.e(ex, "Exception while creating WebDav client: ${ex.message}")
            FirebaseHelper.crashlyticsException(ex)
            return null
        }
    }

    fun streamFromLocalFile(
        context: Context,
        uri: Uri,
    ): InputStream? =
        try {
            // Handle file:// URIs directly, content:// URIs via ContentResolver
            return when (uri.scheme) {
                null, "file" -> {
                    val path = uri.path ?: return null
                    java.io.File(path).inputStream()
                }

                "content" -> {
                    context.contentResolver.openInputStream(uri)
                }

                else -> {
                    Timber.e("Unsupported URI scheme: ${uri.scheme}")
                    null
                }
            }
        } catch (ex: Exception) {
            Timber.e(ex, "Exception while opening local file: ${ex.message}")
            FirebaseHelper.crashlyticsException(ex)
            null
        }

    fun streamFromImmichFile(uri: Uri): InputStream? =
        try {
            val client = buildOkHttpClient()
            val request = Request.Builder().url(uri.toString()).build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                response.close()
                return null
            }
            val responseBody = response.body
            object : FilterInputStream(responseBody.byteStream()) {
                override fun close() {
                    response.use { super.close() }
                }
            }
        } catch (ex: Exception) {
            Timber.e(ex, "Exception while opening Immich file: ${ex.message}")
            FirebaseHelper.crashlyticsException(ex)
            null
        }

    fun streamFromSambaFile(uri: Uri): InputStream? {
        val startTime = System.currentTimeMillis()
        val (hostName, shareName, path, authContext, config) = parseSambaParams(uri)
        val smbClient = SMBClient(config)
        return try {
            val (session, share) = connectSamba(smbClient, hostName, shareName, authContext, startTime)
            val openStartTime = System.currentTimeMillis()
            val file =
                share.openFile(
                    path,
                    EnumSet.of(AccessMask.GENERIC_READ),
                    null,
                    EnumSet.of(SMB2ShareAccess.FILE_SHARE_READ),
                    SMB2CreateDisposition.FILE_OPEN,
                    null,
                )
            Timber.d(
                "SAMBA: Opened file in ${System.currentTimeMillis() - openStartTime}ms. Total setup time: ${System.currentTimeMillis() - startTime}ms",
            )
            object : FilterInputStream(file.inputStream) {
                override fun close() {
                    try {
                        super.close()
                        file.close()
                        share.close()
                        session.close()
                    } catch (ex: Exception) {
                        Timber.e(ex, "Error closing SMB resources: ${ex.message}")
                    }
                }
            }
        } catch (ex: Exception) {
            Timber.e(ex, "Exception while opening Samba file: ${ex.message}")
            FirebaseHelper.crashlyticsException(ex)
            smbClient.close()
            null
        }
    }

    private data class SambaParams(
        val hostName: String,
        val shareName: String,
        val path: String,
        val authContext: AuthenticationContext,
        val config: com.hierynomus.smbj.SmbConfig,
    )

    private fun parseSambaParams(uri: Uri): SambaParams {
        val hostName = uri.host.orEmpty()
        val (userName, password) = SambaHelper.parseUserInfo(uri)
        val domainName = uri.getQueryParameter("domain").orEmpty().ifEmpty { "WORKGROUP" }
        val useEncryption = uri.getQueryParameter("enc")?.toBooleanStrictOrNull() ?: false
        val smbDialects =
            uri
                .getQueryParameter("dialects")
                .orEmpty()
                .split(",")
                .filter { it.isNotBlank() }
                .toSet()
        val (shareName, path) = SambaHelper.parseShareAndPathName(uri)
        val config = SambaHelper.buildSmbConfig(useEncryption, smbDialects)
        val authContext = SambaHelper.buildAuthContext(userName, password, domainName)
        return SambaParams(hostName, shareName, path, authContext, config)
    }

    private fun connectSamba(
        smbClient: SMBClient,
        hostName: String,
        shareName: String,
        authContext: AuthenticationContext,
        startTime: Long,
    ): Pair<com.hierynomus.smbj.session.Session, DiskShare> {
        val connectStartTime = System.currentTimeMillis()
        val connection = smbClient.connect(hostName)
        val session = connection.authenticate(authContext)
        val share = session.connectShare(shareName) as DiskShare
        Timber.d("SAMBA: Connected and authenticated in ${System.currentTimeMillis() - connectStartTime}ms")
        return Pair(session, share)
    }

    fun streamFromMedia(
        context: Context,
        media: AerialMedia,
    ): InputStream? =
        when (media.source) {
            AerialMediaSource.SAMBA -> streamFromSambaFile(media.uri)
            AerialMediaSource.WEBDAV -> streamFromWebDavFile(media.uri)
            AerialMediaSource.IMMICH -> streamFromImmichFile(media.uri)
            else -> streamFromLocalFile(context, media.uri)
        }
}
