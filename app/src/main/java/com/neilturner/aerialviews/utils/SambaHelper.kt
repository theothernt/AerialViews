package com.neilturner.aerialviews.utils

import android.net.Uri
import com.hierynomus.mssmb2.SMB2Dialect
import com.hierynomus.smbj.SmbConfig
import com.hierynomus.smbj.auth.AuthenticationContext
import com.neilturner.aerialviews.models.prefs.SambaMediaPrefs
import timber.log.Timber

@Suppress("unused")
object SambaHelper {
    @Suppress("NAME_SHADOWING")
    fun fixShareName(shareName: String): String {
        var shareName = shareName

        if (shareName.isEmpty()) {
            return ""
        }

        if (shareName.contains("\\", true)) {
            shareName = shareName.replace('\\', '/')
            shareName = shareName.replace("//", "/", true)
            Timber.i("Fixing ShareName - replacing back slashes with forward slashes in sharename")
        }

        if (shareName.contains("smb:/", true)) {
            shareName = shareName.replace("smb:/", "", true)
            Timber.i("Fixing ShareName - removing smb:/ from sharename")
        }

        if (shareName.first() != '/') {
            shareName = "/$shareName"
            Timber.i("Fixing ShareName - adding leading slash")
        }

        if (shareName.last() == '/') {
            shareName = shareName.dropLast(1)
            Timber.i("Fixing ShareName - removing trailing slash")
        }
        return shareName
    }

    fun parseUserInfo(uri: Uri): Pair<String, String> {
        var userName = ""
        var password = ""

        // TODO fix this
        // .userInfo seems to return user+pass string but it is decoded differently than expected
        // "hello+world" is return as "hello+world" and not "hello world"
        // encoding url vs encoding url parameters ?!

        // If none (ie. anonymous) return early
        val userInfo = uri.userInfo ?: return Pair(userName, password)

        val parts = userInfo.split(":")
        if (parts.isNotEmpty()) {
            userName = parts.elementAtOrElse(0) { "" }
            password = parts.elementAtOrElse(1) { "" }
        }
        return Pair(userName, password)
    }

    fun parseShareAndPathName(uri: Uri): Pair<String, String> {
        val segments = uri.pathSegments.toMutableList()
        val shareName = segments.removeFirst()

        var path = ""
        if (segments.isNotEmpty()) {
            path = segments.joinToString("/")
        }
        return Pair(shareName, path)
    }

    fun buildAuthContext(
        userName: String,
        password: String,
        domainName: String,
    ): AuthenticationContext {
        if (userName.isEmpty() && password.isEmpty()) {
            Timber.i("Using anonymous login auth")
            return AuthenticationContext.anonymous()
        }

        if (userName.equals("guest", true)) {
            Timber.i("Using guest login auth")
            return AuthenticationContext.guest()
        }
        return AuthenticationContext(userName, password.toCharArray(), domainName)
    }

    fun buildSmbConfig(): SmbConfig {
        val dialectStrings = SambaMediaPrefs.smbDialects
        val config =
            SmbConfig.builder()
                .withEncryptData(SambaMediaPrefs.enableEncryption)
                .withNegotiatedBufferSize()
        if (dialectStrings.isNotEmpty()) {
            Timber.i("Using SMB dialects: ${dialectStrings.joinToString(",")}")
            val dialects = dialectStrings.map { SMB2Dialect.valueOf(it) }
            config.withDialects(dialects)
        }
        return config.build()
    }
}
