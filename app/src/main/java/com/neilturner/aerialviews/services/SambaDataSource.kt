package com.neilturner.aerialviews.services

import android.annotation.SuppressLint
import android.util.Log
import androidx.media3.common.C
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.share.DiskShare
import com.hierynomus.smbj.share.File
import com.neilturner.aerialviews.utils.SambaHelper
import com.neilturner.aerialviews.utils.toStringOrEmpty
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.lang.Exception
import java.util.EnumSet
import kotlin.math.min

@SuppressLint("UnsafeOptInUsageError")
class SambaDataSource : BaseDataSource(true) {

    private lateinit var dataSpec: DataSpec
    private var userName = ""
    private var password = ""
    private var hostName = ""
    private var shareName = ""
    private val domainName = "WORKGROUP"
    private var path = ""

    private var smbClient: SMBClient? = null
    private var inputStream: InputStream? = null

    private var bytesRead: Long = 0
    private var bytesToRead: Long = 0

    @SuppressLint("UnsafeOptInUsageError")
    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)

        this.dataSpec = dataSpec
        parseCredentials(dataSpec)
        bytesRead = dataSpec.position

        val remoteFile: File
        try {
            remoteFile = openSambaFile()
        } catch (e: Exception) {
            Log.e(TAG, e.message.toString())
            return 0
        }

        inputStream = remoteFile.inputStream

        val skipped = inputStream?.skip(bytesRead) ?: 0
        if (skipped < dataSpec.position) {
            throw EOFException()
        }

        bytesToRead = remoteFile.fileInformation.standardInformation.endOfFile
        transferStarted(dataSpec)
        return bytesToRead
    }

    @SuppressLint("UnsafeOptInUsageError")
    override fun read(buffer: ByteArray, offset: Int, readLength: Int): Int {
        return readInternal(buffer, offset, readLength)
    }

    @SuppressLint("UnsafeOptInUsageError")
    override fun getUri() = dataSpec.uri

    @SuppressLint("UnsafeOptInUsageError")
    override fun close() {
        try {
            inputStream?.close()
            smbClient?.close()
        } catch (e: IOException) {
            throw IOException(e)
        } finally {
            transferEnded()
            inputStream = null
            smbClient = null
        }
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun parseCredentials(dataSpec: DataSpec) {
        val uri = dataSpec.uri
        hostName = uri.host.toStringOrEmpty()

        val userInfo = SambaHelper.parseUserInfo(uri)
        userName = userInfo.first
        password = userInfo.second

        val shareNameAndPath = SambaHelper.parseShareAndPathName(uri)
        shareName = shareNameAndPath.first
        path = shareNameAndPath.second
    }

    private fun openSambaFile(): File {
        val config = SambaHelper.buildSmbConfig()
        smbClient = SMBClient(config)
        val connection = smbClient?.connect(hostName)
        val authContext = SambaHelper.buildAuthContext(userName, password, domainName)
        val session = connection?.authenticate(authContext)
        val share = session?.connectShare(shareName) as DiskShare

        val shareAccess = hashSetOf<SMB2ShareAccess>()
        shareAccess.add(SMB2ShareAccess.ALL.iterator().next())

        return share.openFile(
            path,
            EnumSet.of(AccessMask.GENERIC_READ),
            null,
            shareAccess,
            SMB2CreateDisposition.FILE_OPEN,
            null
        )
    }

    @SuppressLint("UnsafeOptInUsageError")
    @Throws(IOException::class)
    private fun readInternal(buffer: ByteArray, offset: Int, readLength: Int): Int {
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
        private const val TAG = "SambaDataSource"
    }
}

class SambaDataSourceFactory : DataSource.Factory {

    @SuppressLint("UnsafeOptInUsageError")
    override fun createDataSource(): DataSource {
        return SambaDataSource()
    }
}
