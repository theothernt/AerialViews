package com.neilturner.aerialviews.providers

import android.content.Context
import android.net.Uri
import android.util.Log
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.share.DiskShare
import com.neilturner.aerialviews.models.prefs.NetworkVideoPrefs
import com.neilturner.aerialviews.models.videos.AerialVideo
import com.neilturner.aerialviews.utils.FileHelper
import com.neilturner.aerialviews.utils.SmbHelper

class NetworkVideoProvider(context: Context, private val prefs: NetworkVideoPrefs) : VideoProvider(context) {

    override fun fetchVideos(): List<AerialVideo> {
        val videos = mutableListOf<AerialVideo>()

        if (prefs.shareName.isEmpty() ||
            prefs.domainName.isEmpty() ||
            prefs.hostName.isEmpty()
        )
            return videos

        val shareNameAndPath = SmbHelper.parseShareAndPathName(Uri.parse(prefs.shareName))
        val shareName = shareNameAndPath.first
        val path = shareNameAndPath.second

        val networkVideos = try {
            findNetworkMedia(
                prefs.userName, prefs.password, prefs.domainName,
                prefs.hostName, shareName, path
            )
        } catch (e: Exception) {
            Log.e(TAG, e.message!!)
            emptyList()
        }

        networkVideos.forEach { filename ->
            var usernamePassword = ""
            if (prefs.userName.isNotEmpty()) {
                usernamePassword = prefs.userName

                if (prefs.password.isNotEmpty())
                    usernamePassword += ":${prefs.password}"

                usernamePassword += "@"
            }

            // smb://username@host/sharename/path
            // smb://username:password@host/sharename

            val uri = Uri.parse("smb://$usernamePassword${prefs.hostName}${prefs.shareName}/$filename")
            videos.add(AerialVideo(uri, ""))
        }

        Log.i(TAG, "Videos found: ${videos.size}")
        return videos
    }

    private fun findNetworkMedia(
        userName: String,
        password: String,
        domainName: String,
        hostName: String,
        shareName: String,
        path: String
    ): List<String> {
        val files = mutableListOf<String>()
        val config = SmbHelper.buildSmbConfig()
        val smbClient = SMBClient(config)
        val connection = smbClient.connect(hostName)
        val authContext = SmbHelper.buildAuthContext(userName, password, domainName)
        val session = connection?.authenticate(authContext)
        val share = session?.connectShare(shareName) as DiskShare

        share.list(path).forEach { item ->
            if (FileHelper.isVideoFilename(item.fileName)) {
                files.add(item.fileName)
            }
        }

        smbClient.close()
        return files
    }

    companion object {
        private const val TAG = "NetworkVideoProvider"
    }
}
