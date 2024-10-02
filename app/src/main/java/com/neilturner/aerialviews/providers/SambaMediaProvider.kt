package com.neilturner.aerialviews.providers

import android.content.Context
import android.net.Uri
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.protocol.commons.EnumWithValue
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.SmbConfig
import com.hierynomus.smbj.connection.Connection
import com.hierynomus.smbj.session.Session
import com.hierynomus.smbj.share.DiskShare
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.models.enums.AerialMediaSource
import com.neilturner.aerialviews.models.enums.AerialMediaType
import com.neilturner.aerialviews.models.enums.ProviderMediaType
import com.neilturner.aerialviews.models.enums.ProviderSourceType
import com.neilturner.aerialviews.models.prefs.SambaMediaPrefs
import com.neilturner.aerialviews.models.videos.AerialMedia
import com.neilturner.aerialviews.models.videos.VideoMetadata
import com.neilturner.aerialviews.utils.FileHelper
import com.neilturner.aerialviews.utils.SambaHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.net.URLEncoder

class SambaMediaProvider(
    context: Context,
    private val prefs: SambaMediaPrefs,
) : MediaProvider(context) {
    override val type = ProviderSourceType.LOCAL

    override val enabled: Boolean
        get() = prefs.enabled

    override suspend fun fetchMedia(): List<AerialMedia> = fetchSambaMedia().first

    override suspend fun fetchTest(): String = fetchSambaMedia().second

    override suspend fun fetchMetadata(): List<VideoMetadata> = emptyList()

    private suspend fun fetchSambaMedia(): Pair<List<AerialMedia>, String> {
        val media = mutableListOf<AerialMedia>()

        // Check hostname
        // Validate IP address or hostname?
        if (prefs.hostName.isEmpty()) {
            return Pair(media, "Hostname not specified")
        }

        // Check domain name - can be empty?
        // prefs.domainName.isEmpty()

        // Check share name
        if (prefs.shareName.isEmpty()) {
            return Pair(media, "Share name not specified")
        }

        //  Check share name
        val shareName: String
        val path: String
        try {
            // /Videos/Aerial/Community -> Videos + /Aerial/Community
            val shareNameAndPath = SambaHelper.parseShareAndPathName(Uri.parse(prefs.shareName))
            shareName = shareNameAndPath.first
            path = shareNameAndPath.second
        } catch (ex: Exception) {
            Timber.e(ex)
            return Pair(media, "Failed to parse share name")
        }

        val sambaMedia =
            try {
                findSambaMedia(
                    prefs.userName,
                    prefs.password,
                    prefs.domainName,
                    prefs.hostName,
                    shareName,
                    path,
                )
            } catch (ex: Exception) {
                Timber.e(ex)
                return Pair(emptyList(), ex.message.toString())
            }

        // Create samba URL, add to media list, adding media type
        sambaMedia.first.forEach { filename ->
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
            val item = AerialMedia(uri)

            if (FileHelper.isSupportedVideoType(filename)) {
                item.type = AerialMediaType.VIDEO
            } else if (FileHelper.isSupportedImageType(filename)) {
                item.type = AerialMediaType.IMAGE
            }
            item.source = AerialMediaSource.SAMBA
            media.add(item)
        }

        Timber.i("Videos found: ${media.size}")
        return Pair(media, sambaMedia.second)
    }

    private suspend fun findSambaMedia(
        userName: String,
        password: String,
        domainName: String,
        hostName: String,
        shareName: String,
        path: String,
    ): Pair<List<String>, String> =
        withContext(Dispatchers.IO) {
            val res = context.resources
            val selected = mutableListOf<String>()
            val excluded: Int
            val images: Int

            // SMB Config
            val config: SmbConfig
            try {
                config = SambaHelper.buildSmbConfig()
            } catch (ex: Exception) {
                Timber.e(ex)
                return@withContext Pair(selected, "Failed to create SMB config")
            }

            // SMB Client
            val smbClient = SMBClient(config)
            val connection: Connection
            try {
                connection = smbClient.connect(hostName)
            } catch (ex: Exception) {
                Timber.e(ex)
                return@withContext Pair(selected, "Failed to connect, hostname error")
            }

            // SMB Auth + session
            val session: Session?
            try {
                val authContext = SambaHelper.buildAuthContext(userName, password, domainName)
                session = connection.authenticate(authContext)
            } catch (ex: Exception) {
                Timber.e(ex)
                return@withContext Pair(
                    selected,
                    "Authentication failed. Please check the username and password, or server settings if using anonymous login",
                )
            }

            val share: DiskShare
            try {
                share = session?.connectShare(shareName) as DiskShare
            } catch (ex: Exception) {
                Timber.e(ex)
                return@withContext Pair(
                    selected,
                    "Unable to connect to share: $shareName. Please check the spelling of the share name or the server permissions",
                )
            }
            val files = listFilesAndFoldersRecursively(share, path)
            connection.close()
            smbClient.close()

            // Only pick videos
            if (prefs.mediaType != ProviderMediaType.PHOTOS) {
                selected.addAll(
                    files.filter { item ->
                        FileHelper.isSupportedVideoType(item)
                    },
                )
            }
            val videos = selected.size

            // Only pick images
            if (prefs.mediaType != ProviderMediaType.VIDEOS) {
                selected.addAll(
                    files.filter { item ->
                        FileHelper.isSupportedImageType(item)
                    },
                )
            }
            images = selected.size - videos
            excluded = files.size - selected.size

            var message = String.format(res.getString(R.string.samba_media_test_summary1), files.size.toString()) + "\n"
            message += String.format(res.getString(R.string.samba_media_test_summary2), excluded.toString()) + "\n"
            if (prefs.mediaType != ProviderMediaType.PHOTOS) {
                message += String.format(res.getString(R.string.samba_media_test_summary3), videos.toString()) + "\n"
            }
            if (prefs.mediaType != ProviderMediaType.VIDEOS) {
                message += String.format(res.getString(R.string.samba_media_test_summary4), images.toString()) + "\n"
            }
            message += String.format(res.getString(R.string.samba_media_test_summary5), selected.size.toString())
            return@withContext Pair(selected, message)
        }

    private fun listFilesAndFoldersRecursively(
        share: DiskShare,
        path: String,
    ): List<String> {
        val files = mutableListOf<String>()
        share.list(path).forEach { item ->
            val isFolder =
                EnumWithValue.EnumUtils.isSet(
                    item.fileAttributes,
                    FileAttributes.FILE_ATTRIBUTE_DIRECTORY,
                )

            if (FileHelper.isDotOrHiddenFile(item.fileName)) {
                return@forEach
            }

            if (isFolder && prefs.searchSubfolders) {
                files.addAll(listFilesAndFoldersRecursively(share, "$path/${item.fileName}"))
            } else if (!isFolder) {
                files.add("$path/${item.fileName}")
            }
        }
        return files
    }
}
