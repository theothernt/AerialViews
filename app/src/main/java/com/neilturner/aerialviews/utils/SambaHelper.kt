package com.neilturner.aerialviews.utils

import android.net.Uri
import com.hierynomus.mssmb2.SMB2Dialect
import com.hierynomus.smbj.SmbConfig
import com.hierynomus.smbj.auth.AuthenticationContext
import com.neilturner.aerialviews.models.prefs.SambaMediaPrefs
import com.neilturner.aerialviews.models.prefs.SambaProviderPreferences
import timber.log.Timber
import java.util.concurrent.TimeUnit

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

        // Handle double forward slashes (can occur from input or after backslash conversion)
        while (shareName.contains("//")) {
            shareName = shareName.replace("//", "/")
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

    /**
     * Parse user info from URI.
     * @param uri the SMB URI to parse
     * @return Pair of username and password
     */
    fun parseUserInfo(uri: Uri): Pair<String, String> {
        // If none (ie. anonymous) return early
        val userInfo = uri.userInfo ?: return Pair("", "")

        val parts = userInfo.split(":")
        val userName = parts.elementAtOrElse(0) { "" }
        val password = parts.elementAtOrElse(1) { "" }
        return Pair(userName, password)
    }

    /**
     * Parse user info from URI string.
     * This overload is useful for testing without Android dependencies.
     * @param uriString the SMB URI string to parse
     * @return Pair of username and password
     */
    fun parseUserInfo(uriString: String): Pair<String, String> {
        val userInfo = extractUserInfo(uriString) ?: return Pair("", "")

        val parts = userInfo.split(":")
        val userName = parts.elementAtOrElse(0) { "" }
        val password = parts.elementAtOrElse(1) { "" }
        return Pair(userName, password)
    }

    /**
     * Parse share name and path from URI.
     * @param uri the SMB URI to parse
     * @return Pair of share name and path
     */
    fun parseShareAndPathName(uri: Uri): Pair<String, String> {
        val segments = uri.pathSegments.toMutableList()
        val shareName = segments.removeAt(0)

        var path = ""
        if (segments.isNotEmpty()) {
            path = segments.joinToString("/")
        }
        return Pair(shareName, path)
    }

    /**
     * Parse share name and path from URI string.
     * This overload is useful for testing without Android dependencies.
     * @param uriString the SMB URI string to parse
     * @return Pair of share name and path
     */
    fun parseShareAndPathName(uriString: String): Pair<String, String> {
        val pathPart = extractPathAfterHost(uriString)
        val segments = pathPart.trim('/').split("/").toMutableList()

        if (segments.isEmpty()) {
            return Pair("", "")
        }

        val shareName = segments.removeAt(0)
        val path = if (segments.isNotEmpty()) segments.joinToString("/") else ""
        return Pair(shareName, path)
    }

    /**
     * Extract user info from URI string.
     */
    private fun extractUserInfo(uriString: String): String? {
        val withoutScheme = uriString.removePrefix("smb://")
        val authority = withoutScheme.substringBefore("/")
        return if ("@" in authority) authority.substringBefore("@") else null
    }

    /**
     * Extract path portion after host from URI string.
     */
    private fun extractPathAfterHost(uriString: String): String {
        val withoutScheme = uriString.removePrefix("smb://")
        return withoutScheme.substringAfter("/", "")
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

    fun buildSmbConfig(prefs: SambaProviderPreferences = SambaMediaPrefs): SmbConfig =
        buildSmbConfig(prefs.enableEncryption, prefs.smbDialects)

    fun buildSmbConfig(
        enableEncryption: Boolean,
        dialectStrings: Set<String>,
    ): SmbConfig {
        val config =
            SmbConfig
                .builder()
                .withTimeout(30, TimeUnit.SECONDS)
                .withReadTimeout(30, TimeUnit.SECONDS)
                .withEncryptData(enableEncryption)
                .withNegotiatedBufferSize()
        if (dialectStrings.isNotEmpty()) {
            Timber.i("Using SMB dialects: ${dialectStrings.joinToString(",")}")
            val dialects = dialectStrings.map { SMB2Dialect.valueOf(it) }
            config.withDialects(dialects)
        }
        return config.build()
    }
}
