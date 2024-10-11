package com.neilturner.aerialviews.ui.core

import android.content.Context
import android.net.Uri
import android.os.Build
import coil.ImageLoader
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.request.ImageRequest
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.share.DiskShare
import com.neilturner.aerialviews.models.prefs.SambaMediaPrefs
import com.neilturner.aerialviews.models.prefs.WebDavMediaPrefs
import com.neilturner.aerialviews.utils.SambaHelper
import com.thegrizzlylabs.sardineandroid.impl.OkHttpSardine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.EnumSet
import kotlin.io.readBytes

object ImagePlayerHelper {

    fun buildImageLoader(context: Context, view: ImagePlayerView): ImageLoader {
        return ImageLoader
            .Builder(context)
            .eventListener(view)
            .components {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }.build()
    }

    suspend fun loadImage(view: ImagePlayerView, context: Context, listener: ImagePlayerView.OnImagePlayerEventListener?, loader: ImageLoader, uri: Uri) {
        val request =
            ImageRequest
                .Builder(context)
                .target()
        request.data(uri)
        loader.execute(request.build())
    }

    suspend fun loadSambaImage(view: ImagePlayerView, context: Context, listener: ImagePlayerView.OnImagePlayerEventListener?, loader: ImageLoader, uri: Uri) {
        val request =
            ImageRequest
                .Builder(context)
                .target(view)

        try {
            val byteArray = byteArrayFromSambaFile(uri)
            request.data(byteArray)
        } catch (ex: Exception) {
            Timber.e(ex, "Exception while getting byte array from SMB share: ${ex.message}")
            listener?.onImageError()
            return
        }

        loader.execute(request.build())
    }

    suspend fun loadWebDavImage(view: ImagePlayerView, context: Context, listener: ImagePlayerView.OnImagePlayerEventListener?, loader: ImageLoader, uri: Uri) {
        val request =
            ImageRequest
                .Builder(context)
                .target(view)

        try {
            val byteArray = byteArrayFromWebDavFile(uri)
            request.data(byteArray)
        } catch (ex: Exception) {
            Timber.e(ex, "Exception while getting byte array from WebDAV resource: ${ex.message}")
            listener?.onImageError()
            return
        }

        loader.execute(request.build())
    }

    private suspend fun byteArrayFromWebDavFile(uri: Uri): ByteArray =
        withContext(Dispatchers.IO) {
            val client = OkHttpSardine()
            client.setCredentials(WebDavMediaPrefs.userName, WebDavMediaPrefs.password)
            return@withContext client.get(uri.toString()).readBytes()
        }

    private suspend fun byteArrayFromSambaFile(uri: Uri): ByteArray =
        withContext(Dispatchers.IO) {
            val shareNameAndPath = SambaHelper.parseShareAndPathName(uri)
            val shareName = shareNameAndPath.first
            val path = shareNameAndPath.second

            val config = SambaHelper.buildSmbConfig()
            val smbClient = SMBClient(config)
            val authContext = SambaHelper.buildAuthContext(SambaMediaPrefs.userName, SambaMediaPrefs.password, SambaMediaPrefs.domainName)

            smbClient.connect(SambaMediaPrefs.hostName).use { connection ->
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
                return@withContext file.inputStream.readBytes()
            }
        }
}
