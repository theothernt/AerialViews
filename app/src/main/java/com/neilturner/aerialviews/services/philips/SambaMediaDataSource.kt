package com.neilturner.aerialviews.services.philips

import android.media.MediaDataSource
import androidx.core.net.toUri
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.neilturner.aerialviews.data.network.SambaHelper
import timber.log.Timber
import java.io.IOException
import java.util.EnumSet

class SambaMediaDataSource(uriString: String) : MediaDataSource() {
    private val smbClient: com.hierynomus.smbj.SMBClient
    private val remoteFile: com.hierynomus.smbj.share.File
    private val inputStream: java.io.InputStream
    private var currentPosition: Long = 0

    init {
        val uri = uriString.toUri()
        val hostName = uri.host.orEmpty()
        val (userName, password) = SambaHelper.parseUserInfo(uri)
        val domainName = uri.getQueryParameter("domain").orEmpty().ifEmpty { "WORKGROUP" }
        val enableEncryption = uri.getQueryParameter("enc")?.toBooleanStrictOrNull() ?: false
        val smbDialects =
            uri
                .getQueryParameter("dialects")
                .orEmpty()
                .split(",")
                .filter { it.isNotBlank() }
                .toSet()

        val (shareName, path) = SambaHelper.parseShareAndPathName(uri)

        smbClient = com.hierynomus.smbj.SMBClient(SambaHelper.buildSmbConfig(enableEncryption, smbDialects))
        val connection = smbClient.connect(hostName)
        val authContext = SambaHelper.buildAuthContext(userName, password, domainName)
        val session = connection.authenticate(authContext)
        val share = session.connectShare(shareName) as com.hierynomus.smbj.share.DiskShare

        val shareAccess = hashSetOf<SMB2ShareAccess>()
        shareAccess.add(SMB2ShareAccess.ALL.iterator().next())

        remoteFile =
            share.openFile(
                path,
                EnumSet.of(AccessMask.GENERIC_READ),
                null,
                shareAccess,
                SMB2CreateDisposition.FILE_OPEN,
                null,
            )
        inputStream = remoteFile.inputStream
        Timber.i("SambaMediaDataSource: opened $hostName/$shareName/$path")
    }

    override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
        if (position > currentPosition) {
            var remaining = position - currentPosition
            while (remaining > 0) {
                val skipped = inputStream.skip(remaining)
                if (skipped <= 0) return -1
                remaining -= skipped
            }
            currentPosition = position
        } else if (position < currentPosition) {
            return -1
        }
        val bytesRead = inputStream.read(buffer, offset, size)
        if (bytesRead > 0) {
            currentPosition += bytesRead
        }
        return bytesRead
    }

    override fun getSize(): Long = remoteFile.fileInformation.standardInformation.endOfFile

    override fun close() {
        try {
            inputStream.close()
            remoteFile.close()
            smbClient.close()
        } catch (e: IOException) {
            Timber.e(e, "SambaMediaDataSource: error closing")
        }
    }
}
