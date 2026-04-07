package com.neilturner.aerialviews.providers.music

import android.content.Context
import androidx.core.net.toUri
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.protocol.commons.EnumWithValue
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.connection.Connection
import com.hierynomus.smbj.session.Session
import com.hierynomus.smbj.share.DiskShare
import com.neilturner.aerialviews.models.enums.AerialMediaSource
import com.neilturner.aerialviews.models.music.MusicTrack
import com.neilturner.aerialviews.models.prefs.SambaProviderPreferences
import com.neilturner.aerialviews.utils.FileHelper
import com.neilturner.aerialviews.utils.SambaHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.net.URLEncoder

class SambaMusicProvider(
    context: Context,
    private val prefs: SambaProviderPreferences,
) : MusicProvider(context) {
    override val enabled: Boolean
        get() = prefs.enabled && prefs.musicEnabled

    override suspend fun fetchMusic(): List<MusicTrack> {
        if (prefs.hostName.isEmpty() || prefs.shareName.isEmpty()) {
            return emptyList()
        }

        return withContext(Dispatchers.IO) {
            val shareNameAndPath = try {
                SambaHelper.parseShareAndPathName(prefs.shareName.toUri())
            } catch (ex: Exception) {
                Timber.e(ex, "SambaMusicProvider: failed to parse share name")
                return@withContext emptyList<MusicTrack>()
            }

            val (shareName, path) = shareNameAndPath
            val files = findSambaAudioFiles(shareName, path)

            files.map { filename ->
                val usernamePassword = buildCredentials()
                val domain = URLEncoder.encode(prefs.domainName, "utf-8")
                val dialects = prefs.smbDialects.joinToString(",")
                val dialectsEncoded = URLEncoder.encode(dialects, "utf-8")
                val uri = "smb://$usernamePassword${prefs.hostName}/$shareName/$filename?domain=$domain&enc=${prefs.enableEncryption}&dialects=$dialectsEncoded"
                    .toUri()
                val title = FileHelper.stripAudioFileExtension(filename.substringAfterLast('/'))

                MusicTrack(
                    uri = uri,
                    source = AerialMediaSource.SAMBA,
                    title = title,
                )
            }
        }
    }

    private fun buildCredentials(): String {
        if (prefs.userName.isEmpty()) return ""
        var creds = URLEncoder.encode(prefs.userName, "utf-8")
        if (prefs.password.isNotEmpty()) {
            creds += ":" + URLEncoder.encode(prefs.password, "utf-8")
        }
        return "$creds@"
    }

    private fun findSambaAudioFiles(shareName: String, path: String): List<String> {
        val config = try {
            SambaHelper.buildSmbConfig(prefs)
        } catch (ex: Exception) {
            Timber.e(ex, "SambaMusicProvider: failed to create SMB config")
            return emptyList()
        }

        val smbClient = SMBClient(config)
        val connection = try {
            smbClient.connect(prefs.hostName)
        } catch (ex: Exception) {
            Timber.e(ex, "SambaMusicProvider: connection failed")
            return emptyList()
        }

        val session = try {
            val authContext = SambaHelper.buildAuthContext(prefs.userName, prefs.password, prefs.domainName)
            connection.authenticate(authContext)
        } catch (ex: Exception) {
            Timber.e(ex, "SambaMusicProvider: authentication failed")
            connection.close()
            smbClient.close()
            return emptyList()
        }

        val share = try {
            session.connectShare(shareName) as DiskShare
        } catch (ex: Exception) {
            Timber.e(ex, "SambaMusicProvider: share connection failed")
            connection.close()
            smbClient.close()
            return emptyList()
        }

        val files = listAudioFilesRecursively(share, path)
        connection.close()
        smbClient.close()

        return files
    }

    private fun listAudioFilesRecursively(share: DiskShare, path: String): List<String> {
        val files = mutableListOf<String>()
        share.list(path).forEach { item ->
            val isFolder = EnumWithValue.EnumUtils.isSet(
                item.fileAttributes,
                FileAttributes.FILE_ATTRIBUTE_DIRECTORY,
            )

            if (FileHelper.isDotOrHiddenFile(item.fileName)) {
                return@forEach
            }

            if (isFolder && prefs.searchSubfolders) {
                files.addAll(listAudioFilesRecursively(share, "$path/${item.fileName}"))
            } else if (!isFolder && FileHelper.isSupportedAudioType(item.fileName)) {
                files.add("$path/${item.fileName}")
            }
        }
        return files
    }
}
