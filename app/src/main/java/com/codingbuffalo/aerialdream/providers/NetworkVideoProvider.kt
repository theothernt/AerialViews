package com.codingbuffalo.aerialdream.providers

import android.content.Context
import android.net.Uri
import android.util.Log
import com.codingbuffalo.aerialdream.models.prefs.NetworkVideoPrefs
import com.codingbuffalo.aerialdream.models.videos.AerialVideo
import com.codingbuffalo.aerialdream.utils.FileHelper
import com.codingbuffalo.aerialdream.utils.SmbHelper
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.share.DiskShare

class NetworkVideoProvider(context: Context, private val prefs: NetworkVideoPrefs) : VideoProvider(context) {

    override fun fetchVideos(): List<AerialVideo> {
        val videos = mutableListOf<AerialVideo>()

        if (prefs.shareName.isEmpty() ||
                prefs.domainName.isEmpty() ||
                prefs.hostName.isEmpty())
                    return videos

        val shareNameAndPath = SmbHelper.parseShareAndPathName(Uri.parse(prefs.shareName))
        val shareName = shareNameAndPath.first
        val path = shareNameAndPath.second

        val networkVideos = try {
            findNetworkMedia(prefs.userName, prefs.password, prefs.domainName,
                prefs.hostName, shareName, path)
        } catch (e: Exception) {
            Log.e(TAG, e.message!!)
            emptyList()
        }

        networkVideos.forEach{ filename ->
            var usernamePassword = ""
            if (prefs.userName.isNotEmpty() && prefs.password.isNotEmpty())
                usernamePassword = "${prefs.userName}:${prefs.password}@"

            val uri = Uri.parse("smb://$usernamePassword${prefs.hostName}${prefs.shareName}/$filename")
            if (prefs.filenameAsLocation) {
                val location = FileHelper.filenameToTitleCase(uri.lastPathSegment!!)
                videos.add(AerialVideo(uri, location))
            }  else {
                videos.add(AerialVideo(uri, ""))
            }
        }

        Log.i(TAG, "Videos found: ${videos.size}")
        return videos
    }

    private fun findNetworkMedia(userName: String, password: String, domainName: String,
                                 hostName: String, shareName: String, path: String): List<String> {
        val files = mutableListOf<String>()
        val smbClient = SMBClient()
        val connection = smbClient.connect(hostName)
        val authContext = AuthenticationContext(userName, password.toCharArray(), domainName)
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