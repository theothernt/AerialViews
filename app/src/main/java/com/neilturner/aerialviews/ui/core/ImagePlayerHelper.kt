package com.neilturner.aerialviews.ui.core

import android.net.Uri
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
import java.util.EnumSet

internal object ImagePlayerHelper {
    suspend fun byteArrayFromWebDavFile(uri: Uri): ByteArray =
        withContext(Dispatchers.IO) {
            val client = OkHttpSardine()
            client.setCredentials(WebDavMediaPrefs.userName, WebDavMediaPrefs.password)
            return@withContext client.get(uri.toString()).readBytes()
        }

    suspend fun byteArrayFromSambaFile(uri: Uri): ByteArray =
        withContext(Dispatchers.IO) {
            val shareNameAndPath = SambaHelper.parseShareAndPathName(uri)
            val shareName = shareNameAndPath.first
            val path = shareNameAndPath.second

            val config = SambaHelper.buildSmbConfig()
            val smbClient = SMBClient(config)
            val authContext = SambaHelper.buildAuthContext(SambaMediaPrefs.userName, SambaMediaPrefs.password, SambaMediaPrefs.domainName)

            smbClient.connect(SambaMediaPrefs.hostName).use { connection ->
                val session = connection?.authenticate(authContext)
                try {
                    val share = session?.connectShare(shareName) as DiskShare
                    val shareAccess = hashSetOf<SMB2ShareAccess>()
                    shareAccess.add(SMB2ShareAccess.ALL.iterator().next())
                    share.openFile(
                        path,
                        EnumSet.of(AccessMask.GENERIC_READ),
                        null,
                        shareAccess,
                        SMB2CreateDisposition.FILE_OPEN,
                        null,
                    ).use { file ->
                        return@withContext file.inputStream.readBytes()
                    }
                } finally {
                    session?.close()
                }
            }
        }
}
