package com.neilturner.aerialviews.providers.immich

import android.annotation.SuppressLint
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import com.neilturner.aerialviews.models.enums.ImmichAuthType
import com.neilturner.aerialviews.models.prefs.ImmichMediaPrefs
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import kotlin.math.min

@SuppressLint("UnsafeOptInUsageError")
class ImmichDataSource : BaseDataSource(true) {
    private lateinit var dataSpec: DataSpec
    private var okHttpClient: OkHttpClient = OkHttpClient()
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

        val request =
            Request
                .Builder()
                .url(uri.toString())
                .addHeader("Range", "bytes=${dataSpec.position}-")
                .also { builder ->
                    when (ImmichMediaPrefs.authType) {
                        ImmichAuthType.API_KEY -> builder.addHeader("X-API-Key", ImmichMediaPrefs.apiKey)
                        ImmichAuthType.SHARED_LINK -> {
                            // Add any necessary headers for shared link authentication
                        }
                        null -> {
                            // No authentication
                        }
                    }
                }.build()

        try {
            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                throw IOException("Unexpected code ${response.code}")
            }

            inputStream = response.body?.byteStream()
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
                inputStream?.read(buffer, offset, min(readLength.toLong(), bytesRemaining).toInt()) ?: -1
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

@Suppress("unused")
class ImmichDataSourceFactory : DataSource.Factory {
    @SuppressLint("UnsafeOptInUsageError")
    override fun createDataSource(): DataSource = ImmichDataSource()
}
