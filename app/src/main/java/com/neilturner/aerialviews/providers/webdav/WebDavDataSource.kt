package com.neilturner.aerialviews.providers.webdav

import android.annotation.SuppressLint
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import com.neilturner.aerialviews.data.network.SambaHelper
import okhttp3.Credentials
import okhttp3.Request
import okhttp3.Response
import timber.log.Timber
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit
import kotlin.math.min

@SuppressLint("UnsafeOptInUsageError")
class WebDavDataSource(
    private val validateSsl: Boolean = true,
) : BaseDataSource(true) {
    private lateinit var dataSpec: DataSpec

    private var response: Response? = null
    private var inputStream: InputStream? = null

    private var bytesRead: Long = 0
    private var bytesToRead: Long = C.LENGTH_UNSET.toLong()

    @SuppressLint("UnsafeOptInUsageError")
    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)
        this.dataSpec = dataSpec
        bytesRead = dataSpec.position

        val startTime = System.currentTimeMillis()
        openWebDavFile(bytesRead)
        val connectionTime = System.currentTimeMillis() - startTime
        Timber.i("WebDAV connection established in ${connectionTime}ms (Size: $bytesToRead bytes)")

        transferStarted(dataSpec)
        return bytesToRead
    }

    @SuppressLint("UnsafeOptInUsageError")
    override fun read(
        buffer: ByteArray,
        offset: Int,
        readLength: Int,
    ): Int = readInternal(buffer, offset, readLength)

    override fun getUri(): Uri = dataSpec.uri

    @SuppressLint("UnsafeOptInUsageError")
    override fun close() {
        try {
            inputStream?.close()
        } catch (e: IOException) {
            throw IOException(e)
        } finally {
            transferEnded()
            inputStream = null
            response?.close()
            response = null
        }
    }

    private fun openWebDavFile(offset: Long) {
        val urlWithUserInfo = dataSpec.uri.toString()

        val okHttpClient =
            WebDavSslHelper
                .createOkHttpClient(validateSsl)
                .newBuilder()
                .callTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build()

        val (userName, password) = SambaHelper.parseUserInfo(dataSpec.uri)
        val url = stripUserInfo(urlWithUserInfo)

        val request =
            Request
                .Builder()
                .url(url)
                .header("Range", "bytes=$offset-")
                .apply {
                    if (userName.isNotEmpty()) {
                        header("Authorization", Credentials.basic(userName, password))
                    }
                }.build()

        val opened = okHttpClient.newCall(request).execute()
        if (!opened.isSuccessful) {
            opened.close()
            throw IOException("Could not open $url: ${opened.code} ${opened.message}")
        }

        response = opened
        inputStream = opened.body.byteStream()
        // A ranged answer only holds the rest of the file, the total size is in the content
        // range header.
        bytesToRead = totalLength(opened)
    }

    private fun totalLength(opened: Response): Long {
        val contentRange = opened.header("Content-Range")
        if (contentRange != null) {
            val total = contentRange.substringAfterLast('/').trim().toLongOrNull()
            if (total != null) return total
        }
        val length = opened.body.contentLength()
        return if (length >= 0) length else C.LENGTH_UNSET.toLong()
    }

    private fun stripUserInfo(url: String): String {
        val withoutScheme = url.substringAfter("://")
        val atIndex = withoutScheme.indexOf('@')
        if (atIndex == -1) return url
        val hostAndPath = withoutScheme.substringAfter('@')
        return "${url.substringBefore("://")}://$hostAndPath"
    }

    @SuppressLint("UnsafeOptInUsageError")
    @Throws(IOException::class)
    private fun readInternal(
        buffer: ByteArray,
        offset: Int,
        readLength: Int,
    ): Int {
        var newReadLength = readLength
        if (newReadLength == 0) {
            return 0
        }

        if (bytesToRead != C.LENGTH_UNSET.toLong()) {
            val bytesRemaining: Long = bytesToRead - bytesRead
            if (bytesRemaining == 0L) {
                return C.RESULT_END_OF_INPUT
            }
            newReadLength = min(newReadLength.toLong(), bytesRemaining).toInt()
        }

        val read = inputStream!!.read(buffer, offset, newReadLength)
        if (read == -1) {
            if (bytesToRead != C.LENGTH_UNSET.toLong()) {
                throw EOFException()
            }
            return C.RESULT_END_OF_INPUT
        }

        bytesRead += read.toLong()
        bytesTransferred(read)
        return read
    }
}

class WebDavDataSourceFactory(
    private val validateSsl: Boolean = true,
) : DataSource.Factory {
    @SuppressLint("UnsafeOptInUsageError")
    override fun createDataSource(): DataSource = WebDavDataSource(validateSsl)
}
