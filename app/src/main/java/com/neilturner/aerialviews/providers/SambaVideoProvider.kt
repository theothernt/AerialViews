package com.neilturner.aerialviews.providers

import android.content.Context
import android.net.Uri
import android.util.Log
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.protocol.commons.EnumWithValue
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.SmbConfig
import com.hierynomus.smbj.connection.Connection
import com.hierynomus.smbj.session.Session
import com.hierynomus.smbj.share.DiskShare
import com.neilturner.aerialviews.models.enums.MediaType
import com.neilturner.aerialviews.models.prefs.SambaVideoPrefs
import com.neilturner.aerialviews.models.videos.AerialVideo
import com.neilturner.aerialviews.models.videos.VideoMetadata
import com.neilturner.aerialviews.utils.FileHelper
import com.neilturner.aerialviews.utils.SambaHelper
import java.net.URLEncoder

class SambaVideoProvider(context: Context, private val prefs: SambaVideoPrefs) : VideoProvider(context) {

    override val enabled: Boolean
        get() = prefs.enabled

    override fun fetchVideos(): List<AerialVideo> {
        return fetchSambaVideos().first
    }

    override fun fetchTest(): String {
        return fetchSambaVideos().second
    }

    override fun fetchMetadata(): List<VideoMetadata> {
        return emptyList()
    }

    private fun fetchSambaVideos(): Pair<List<AerialVideo>, String> {
        val videos = mutableListOf<AerialVideo>()

        // Check hostname
        // Validate IP address or hostname?
        if (prefs.hostName.isEmpty()) {
            return Pair(videos, "Hostname not specified")
        }

        // Check domain name - can be empty?
        // prefs.domainName.isEmpty()

        // Check share name
        if (prefs.shareName.isEmpty()) {
            return Pair(videos, "Share name not specified")
        }

        //  Check share name
        val shareName: String
        val path: String
        try {
            // /Videos/Aerial/Community -> Videos + /Aerial/Community
            val shareNameAndPath = SambaHelper.parseShareAndPathName(Uri.parse(prefs.shareName))
            shareName = shareNameAndPath.first
            path = shareNameAndPath.second
        } catch (e: Exception) {
            Log.e(TAG, e.message.toString())
            return Pair(videos, "Failed to parse share name")
        }

        val sambaVideos = try {
            findSambaMedia(
                prefs.userName,
                prefs.password,
                prefs.domainName,
                prefs.hostName,
                shareName,
                path
            )
        } catch (e: Exception) {
            Log.e(TAG, e.message.toString())
            Pair(emptyList(), e.message.toString())
        }

        sambaVideos.first.forEach { filename ->
            var usernamePassword = ""
            if (prefs.userName.isNotEmpty()) {
                usernamePassword = URLEncoder.encode(prefs.userName, "utf-8")

                if (prefs.password.isNotEmpty()) {
                    usernamePassword += ":" + URLEncoder.encode(prefs.password, "utf-8")
                }

                usernamePassword += "@"
            }
            // smb://username@host/sharename/path
            // smb://username:password@host/sharename
            val uri = Uri.parse("smb://$usernamePassword${prefs.hostName}/$shareName/$filename")
            videos.add(AerialVideo(uri, ""))
        }

        Log.i(TAG, "Videos found: ${videos.size}")
        return Pair(videos, sambaVideos.second)
    }

    private fun findSambaMedia(
        userName: String,
        password: String,
        domainName: String,
        hostName: String,
        shareName: String,
        path: String
    ): Pair<List<String>, String> {
        val files = mutableListOf<String>()

        // SMB Config
        val config: SmbConfig
        try {
            config = SambaHelper.buildSmbConfig()
        } catch (e: Exception) {
            Log.e(TAG, e.message.toString())
            return Pair(files, "Failed to create SMB config")
        }

        // SMB Client
        val smbClient = SMBClient(config)
        val connection: Connection
        try {
            connection = smbClient.connect(hostName)
        } catch (e: Exception) {
            Log.e(TAG, e.message.toString())
            return Pair(files, "Failed to connect, hostname error")
        }

        // SMB Auth + session
        val session: Session?
        try {
            val authContext = SambaHelper.buildAuthContext(userName, password, domainName)
            session = connection.authenticate(authContext)
        } catch (e: Exception) {
            Log.e(TAG, e.message.toString())
            return Pair(files, "Authentication failed. Please check the username and password, or server settings if using anonymous login")
        }

        val share: DiskShare
        try {
            share = session?.connectShare(shareName) as DiskShare
        } catch (e: Exception) {
            Log.e(TAG, e.message.toString())
            return Pair(files, "Unable to connect to share: $shareName. Please check the spelling of the share name or the server permissions")
        }
        files.addAll(listFilesAndFoldersRecursive(share, path))
        smbClient.close()

        val filteredFiles = mutableListOf<String>()

        // Filter out non-video, dot files, etc
        if (SambaVideoPrefs.mediaType == MediaType.VIDEOS ||
            SambaVideoPrefs.mediaType == MediaType.VIDEOS_IMAGES
        ) {
            filteredFiles.addAll(
                files.filter { item ->
                    FileHelper.isSupportedVideoType(item)
                }
            )
        }
        if (SambaVideoPrefs.mediaType == MediaType.IMAGES ||
            SambaVideoPrefs.mediaType == MediaType.VIDEOS_IMAGES
        ) {
            filteredFiles.addAll(
                files.filter { item ->
                    FileHelper.isSupportedImageType(item)
                }
            )
        }

        // Show user normal auth vs anonymous vs guest?
        val excluded = files.size - filteredFiles.size
        var message = "Files found on samba share: ${files.size + excluded}\n"
        message += "Videos/Images with unsupported file extensions: $excluded\n"
        message += "Videos/Images selected for playback: ${filteredFiles.size}"
        return Pair(filteredFiles, message)
    }

    private fun listFilesAndFoldersRecursive(share: DiskShare, path: String): List<String> {
        val files = mutableListOf<String>()
        share.list(path).forEach { item ->
            val isFolder = EnumWithValue.EnumUtils.isSet(
                item.fileAttributes,
                FileAttributes.FILE_ATTRIBUTE_DIRECTORY
            )

            if (FileHelper.isDotOrHiddenFile(item.fileName)) {
                return@forEach
            }

            if (isFolder && prefs.searchSubfolders) {
                files.addAll(listFilesAndFoldersRecursive(share, "$path/${item.fileName}"))
            } else if (!isFolder) {
                files.add("$path/${item.fileName}")
            }
        }
        return files
    }

    companion object {
        private const val TAG = "SambaVideoProvider"
    }
}
