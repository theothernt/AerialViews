package com.neilturner.aerialviews.utils

import android.net.Uri
import android.util.Log
import com.hierynomus.smbj.auth.AuthenticationContext
import com.neilturner.aerialviews.models.prefs.NetworkVideoPrefs

object SmbHelper {

    fun fixShareName() {
        val shareName = NetworkVideoPrefs.shareName

        if (shareName.isEmpty())
            return

        if (shareName.first() != '/') {
            NetworkVideoPrefs.shareName = "/$shareName"
            Log.i(TAG, "Fixing ShareName - removing leading slash")
        }

        if (shareName.last() == '/') {
            NetworkVideoPrefs.shareName = shareName.dropLast(1)
            Log.i(TAG, "Fixing ShareName - removing trailing slash")
        }
    }

    fun parseUserInfo(uri: Uri): Pair<String, String> {
        var userName = ""
        var password = ""

        // If none (ie. anonymous) return early
        val userInfo = uri.userInfo ?: return Pair(userName, password)

        val parts = userInfo.split(":")
        userName = parts.elementAt(0)
        password = parts.elementAt(1)
        return Pair(userName, password)
    }

    fun parseShareAndPathName(uri: Uri): Pair<String, String> {
        val segments = uri.pathSegments.toMutableList()
        val shareName = segments.removeFirst()

        var path = ""
        if (segments.isNotEmpty()) {
            path = segments.joinToString("/")
        }

        // Should use Data class in future...
        // https://stackoverflow.com/a/48270757/247257
        return Pair(shareName, path)
    }

    fun buildAuthContext(userName: String, password: String, domainName: String): AuthenticationContext {
        if (userName.isEmpty() && password.isEmpty())
            return AuthenticationContext.anonymous()

        return AuthenticationContext(userName, password.toCharArray(), domainName)
    }

    private const val TAG = "SmbHelper"
}
