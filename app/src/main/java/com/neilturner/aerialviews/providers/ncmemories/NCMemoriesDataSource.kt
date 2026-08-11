package com.neilturner.aerialviews.providers.ncmemories

import android.annotation.SuppressLint
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import com.neilturner.aerialviews.data.network.ServerConfig
import com.neilturner.aerialviews.data.network.SslHelper
import com.neilturner.aerialviews.models.prefs.NCMemoriesMediaPrefs
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import kotlin.math.min
@SuppressLint("UnsafeOptInUsageError")
class NCMemoriesDataSource : BaseDataSource(true) {
    private lateinit var dataSpec: DataSpec
    private var okHttpClient: OkHttpClient =
        SslHelper().createOkHttpClient(
            ServerConfig(
                "",
                NCMemoriesMediaPrefs.validateSsl
            )
        )
    private var inputStream: InputStream? = null
    private var bytesRemaining: Long = 0
    private var opened = false

    override fun open(dataSpec: DataSpec): Long {
        this.dataSpec = dataSpec
        val uri = dataSpec.uri
        bytesRemaining = dataSpec.length
        if (bytesRemaining == C.LENGTH_UNSET.toLong()) {
            bytesRemaining = Long.MAX_VALUE
        }

        transferInitializing(dataSpec)

        val credential = Credentials.basic(
            NCMemoriesMediaPrefs.username,
            NCMemoriesMediaPrefs.password
        )

        val request =
            Request
                .Builder()
                .url(uri.toString())
                .addHeader("Range", "bytes=${dataSpec.position}-")
                .addHeader("Authorization", credential)
                .addHeader("OCS-APIRequest", "true")
                .build()

        try {
            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                throw IOException("Unexpected code ${response.code}")
            }

            inputStream = response.body.byteStream()
            opened = true
            transferStarted(dataSpec)
        } catch (e: IOException) {
            throw IOException(e)
        }

        return bytesRemaining
    }

    override fun read(
        buffer: ByteArray,
        offset: Int,
        readLength: Int,
    ): Int {
        if (readLength == 0) {
            return 0
        }
        if (bytesRemaining == 0L) {
            return C.RESULT_END_OF_INPUT
        }

        val bytesRead =
            try {
                inputStream?.read(buffer, offset, min(readLength.toLong(), bytesRemaining).toInt())
                    ?: -1
            } catch (e: IOException) {
                throw IOException(e)
            }

        if (bytesRead == -1) {
            if (bytesRemaining != Long.MAX_VALUE) {
                throw EOFException()
            }
            return C.RESULT_END_OF_INPUT
        }

        bytesRemaining -= bytesRead.toLong()
        bytesTransferred(bytesRead)
        return bytesRead
    }

    override fun getUri(): Uri? = if (opened) dataSpec.uri else null

    override fun close() {
        try {
            inputStream?.close()
        } catch (e: IOException) {
            throw IOException(e)
        } finally {
            inputStream = null
            if (opened) {
                opened = false
                transferEnded()
            }
        }
    }
}

class NCMemoriesDataSourceFactory : DataSource.Factory {
    @SuppressLint("UnsafeOptInUsageError")
    override fun createDataSource(): DataSource = NCMemoriesDataSource()
}
