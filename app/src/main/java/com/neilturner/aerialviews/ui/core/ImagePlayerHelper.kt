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
import java.io.InputStream
import java.util.EnumSet

internal object ImagePlayerHelper {
    fun streamFromWebDavFile(uri: Uri): InputStream? {
        var client: OkHttpSardine? = OkHttpSardine()
        client?.setCredentials(WebDavMediaPrefs.userName, WebDavMediaPrefs.password)
        return client?.get(uri.toString())
    }

    fun streamFromSambaFile(uri: Uri): InputStream? {
        val shareNameAndPath = SambaHelper.parseShareAndPathName(uri)
        val shareName = shareNameAndPath.first
        val path = shareNameAndPath.second
        val config = SambaHelper.buildSmbConfig()
        val smbClient = SMBClient(config)
        val authContext =
            SambaHelper.buildAuthContext(
                SambaMediaPrefs.userName,
                SambaMediaPrefs.password,
                SambaMediaPrefs.domainName,
            )

        val connection = smbClient.connect(SambaMediaPrefs.hostName)
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
        return file.inputStream
    }
}
