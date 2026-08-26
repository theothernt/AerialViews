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
import com.hierynomus.smbj.session.Session
import com.hierynomus.smbj.share.DiskShare
import com.neilturner.aerialviews.BuildConfig
import com.neilturner.aerialviews.data.network.SambaHelper
import com.neilturner.aerialviews.data.network.ServerConfig
import com.neilturner.aerialviews.data.network.SslHelper
import com.neilturner.aerialviews.models.enums.AerialMediaSource
import com.neilturner.aerialviews.models.enums.ImmichAuthType
import com.neilturner.aerialviews.models.prefs.ImmichMediaPrefs
import com.neilturner.aerialviews.models.prefs.NCMemoriesMediaPrefs
import com.neilturner.aerialviews.models.prefs.WebDavMediaPrefs
import com.neilturner.aerialviews.models.prefs.WebDavMediaPrefs2
import com.neilturner.aerialviews.models.videos.AerialMedia
import com.neilturner.aerialviews.providers.webdav.WebDavHostParser
import com.neilturner.aerialviews.providers.webdav.defaultPortFor
import com.neilturner.aerialviews.utils.FirebaseHelper
import com.thegrizzlylabs.sardineandroid.impl.OkHttpSardine
import okhttp3.Credentials
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

    fun buildOkHttpClient(
        validateSsl: Boolean = true,
        source: AerialMediaSource? = null,
    ): OkHttpClient {
        val serverConfig = ServerConfig("", validateSsl)
        val okHttpClient = SslHelper().createOkHttpClient(serverConfig)
        return okHttpClient
            .newBuilder()
            .also { builder ->
                when (source) {
                    AerialMediaSource.IMMICH -> {
                        builder.addInterceptor(ImmichApiKeyInterceptor())
                    }

                    AerialMediaSource.NCMEMORIES -> {
                        builder.addInterceptor(NCMemoriesAuthInterceptor())
                    }

                    else -> {
                        // no additional headers
                    }
                }
            }.build()
    }

    internal class ImmichApiKeyInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
            val originalRequest = chain.request()
            val newRequest =
                when (ImmichMediaPrefs.authType) {
                    ImmichAuthType.API_KEY -> {
                        Timber.d("Adding X-API-Key header")
                        originalRequest
                            .newBuilder()
                            .addHeader("X-API-Key", ImmichMediaPrefs.apiKey.trim())
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

    internal class NCMemoriesAuthInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
            val originalRequest = chain.request()
            val newRequest =
                if (NCMemoriesMediaPrefs.enabled) {
                    Timber.d("Adding Nextcloud Memories headers")

                    val credential =
                        Credentials.basic(
                            NCMemoriesMediaPrefs.username,
                            NCMemoriesMediaPrefs.password,
                        )

                    originalRequest
                        .newBuilder()
                        .addHeader("Authorization", credential)
                        .addHeader("OCS-APIRequest", "true")
                        .build()
                } else {
                    Timber.d("Skipping Nextcloud Memories request headers")
                    originalRequest
                }
            return chain.proceed(newRequest)
        }
    }

    private fun stripUserinfo(url: String): String {
        val withoutScheme = url.substringAfter("://")
        val atIndex = withoutScheme.indexOf('@')
        if (atIndex == -1) return url
        val hostAndPath = withoutScheme.substringAfter('@')
        return "${url.substringBefore("://")}://$hostAndPath"
    }

    fun stripUserinfoFromUri(uri: Uri): Uri {
        val url = uri.toString()
        return Uri.parse(stripUserinfo(url))
    }

    fun streamFromWebDavFile(uri: Uri): InputStream? {
        val baseClient = buildOkHttpClient(validateSsl = getWebDavValidateSslFromUri(uri), source = AerialMediaSource.WEBDAV)
        val okHttpClient = baseClient.newBuilder().build()
        val client = OkHttpSardine(okHttpClient)
        val (userName, password) = SambaHelper.parseUserInfo(uri)
        try {
            client.setCredentials(userName, password, true)
            val cleanUrl = stripUserinfo(uri.toString())
            return client.get(cleanUrl)
        } catch (ex: Exception) {
            Timber.e(ex, "Exception while creating WebDav client: ${ex.message}")
            FirebaseHelper.crashlyticsException(ex)
            return null
        }
    }

    private fun getWebDavValidateSslFromUri(uri: Uri): Boolean {
        val host = uri.host?.lowercase() ?: return true
        val port = if (uri.port == -1) null else uri.port

        if (WebDavMediaPrefs.hostName.isNotBlank()) {
            val parsed = runCatching { WebDavHostParser.parse(WebDavMediaPrefs.hostName) }.getOrNull()
            if (parsed != null && parsed.host.equals(host, ignoreCase = true)) {
                val prefPort =
                    parsed.port ?: defaultPortFor(WebDavMediaPrefs.scheme ?: com.neilturner.aerialviews.models.enums.SchemeType.HTTP)
                if (port == null || port == prefPort) return WebDavMediaPrefs.validateSsl
            }
        }

        if (WebDavMediaPrefs2.hostName.isNotBlank()) {
            val parsed = runCatching { WebDavHostParser.parse(WebDavMediaPrefs2.hostName) }.getOrNull()
            if (parsed != null && parsed.host.equals(host, ignoreCase = true)) {
                val prefPort =
                    parsed.port ?: defaultPortFor(WebDavMediaPrefs2.scheme ?: com.neilturner.aerialviews.models.enums.SchemeType.HTTP)
                if (port == null || port == prefPort) return WebDavMediaPrefs2.validateSsl
            }
        }

        return true
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
                    val finalUri =
                        if (SDK_INT >= 29) {
                            android.provider.MediaStore.setRequireOriginal(uri)
                        } else {
                            uri
                        }
                    context.contentResolver.openInputStream(finalUri)
                }

                "http", "https" -> {
                    // Remote images are fetched directly by Coil's network loader,
                    // so there's no local stream to open here.
                    null
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
            val client =
                buildOkHttpClient(
                    validateSsl = ImmichMediaPrefs.validateSsl,
                    source = AerialMediaSource.IMMICH,
                )
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

    fun streamFromNCMemoriesFile(uri: Uri): InputStream? =
        try {
            val client =
                buildOkHttpClient(
                    validateSsl = NCMemoriesMediaPrefs.validateSsl,
                    source = AerialMediaSource.NCMEMORIES,
                )
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
            Timber.e(ex, "Exception while opening Nextcloud Memories file: ${ex.message}")
            FirebaseHelper.crashlyticsException(ex)
            null
        }

    fun streamFromSambaFile(uri: Uri): InputStream? {
        val startTime = System.currentTimeMillis()
        val (hostName, shareName, path, authContext, config) = parseSambaParams(uri)
        val smbClient = SMBClient(config)
        return try {
            val (session, share) = connectSamba(smbClient, hostName, shareName, authContext)
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
    ): Pair<Session, DiskShare> {
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
            AerialMediaSource.NCMEMORIES -> streamFromNCMemoriesFile(media.uri)
            else -> streamFromLocalFile(context, media.uri)
        }
}
