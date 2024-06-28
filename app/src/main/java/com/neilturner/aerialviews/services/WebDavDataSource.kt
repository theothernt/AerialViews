package com.neilturner.aerialviews.services

import android.annotation.SuppressLint
import android.util.Log
import androidx.media3.common.C
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import com.neilturner.aerialviews.models.prefs.WebDavMediaPrefs
import com.thegrizzlylabs.sardineandroid.impl.OkHttpSardine
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import kotlin.math.min

@SuppressLint("UnsafeOptInUsageError")
class WebDavDataSource : BaseDataSource(true) {
    private lateinit var dataSpec: DataSpec
    private var userName = WebDavMediaPrefs.userName
    private var password = WebDavMediaPrefs.password

    private var webDavClient: OkHttpSardine? = null
    private var inputStream: InputStream? = null

    private var bytesRead: Long = 0
    private var bytesToRead: Long = 0

    @SuppressLint("UnsafeOptInUsageError")
    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)

        this.dataSpec = dataSpec
        bytesRead = dataSpec.position

        inputStream =
            try {
                openWebDavFile()
            } catch (ex: Exception) {
                Log.e(TAG, ex.message.toString())
                return 0
            }

        val skipped = inputStream?.skip(bytesRead) ?: 0
        if (skipped < dataSpec.position) {
            throw EOFException()
        }

        bytesToRead = inputStream?.available()?.toLong() ?: 0
        transferStarted(dataSpec)
        return bytesToRead
    }

    @SuppressLint("UnsafeOptInUsageError")
    override fun read(
        buffer: ByteArray,
        offset: Int,
        readLength: Int,
    ): Int {
        return readInternal(buffer, offset, readLength)
    }

    @SuppressLint("UnsafeOptInUsageError")
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
            webDavClient = null
        }
    }

    private fun openWebDavFile(): InputStream? {
        webDavClient?.setCredentials(userName, password)
        return webDavClient?.get(dataSpec.uri.toString())
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

    companion object {
        private const val TAG = "WebDevDataSource"
    }
}

class WebDavDataSourceFactory : DataSource.Factory {
    @SuppressLint("UnsafeOptInUsageError")
    override fun createDataSource(): DataSource {
        return WebDavDataSource()
    }
}
