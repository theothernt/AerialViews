package com.neilturner.aerialviews.services

import android.annotation.SuppressLint
import androidx.media3.common.C
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import com.neilturner.aerialviews.models.prefs.WebDavMediaPrefs
import com.thegrizzlylabs.sardineandroid.impl.OkHttpSardine
import timber.log.Timber
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import kotlin.math.min

@SuppressLint("UnsafeOptInUsageError")
class WebDavDataSource : BaseDataSource(true) {
    private lateinit var dataSpec: DataSpec

    private var client: OkHttpSardine? = null
    private var inputStream: InputStream? = null

    private var bytesRead: Long = 0
    private var bytesToRead: Long = 0

    @SuppressLint("UnsafeOptInUsageError")
    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)
        this.dataSpec = dataSpec
        bytesRead = dataSpec.position

        val (file, size) = openWebDavFile()
        inputStream = file

        val skipped = inputStream?.skip(bytesRead) ?: 0
        if (skipped < dataSpec.position) {
            throw EOFException()
        }

        bytesToRead = size
        transferStarted(dataSpec)
        return bytesToRead
    }

    @SuppressLint("UnsafeOptInUsageError")
    override fun read(
        buffer: ByteArray,
        offset: Int,
        readLength: Int,
    ): Int = readInternal(buffer, offset, readLength)

    override fun getUri() = dataSpec.uri

    @SuppressLint("UnsafeOptInUsageError")
    override fun close() {
        try {
            inputStream?.close()
        } catch (e: IOException) {
            throw IOException(e)
        } finally {
            transferEnded()
            inputStream = null
            client = null
        }
    }

    private fun openWebDavFile(): Pair<InputStream?, Long> {
        return try {
            var size = 0L
            val url = dataSpec.uri.toString()
            if (client == null) {
                client = OkHttpSardine()
                client?.setCredentials(WebDavMediaPrefs.userName, WebDavMediaPrefs.password, true)
            }
            val resource = client?.list(url)
            if (resource?.isNotEmpty() == true) {
                size = resource[0].contentLength
            }
            val file = client?.get(url)
            return Pair(file, size)
        } catch (ex: Exception) {
            Timber.e(ex)
            Pair(null, 0)
        }
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

class WebDavDataSourceFactory : DataSource.Factory {
    @SuppressLint("UnsafeOptInUsageError")
    override fun createDataSource(): DataSource = WebDavDataSource()
}
