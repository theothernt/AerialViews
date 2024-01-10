package com.neilturner.aerialviews.utils

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import java.io.File

object FileHelper {

    fun findAllMedia(context: Context): List<String?> {
        val videos = mutableListOf<String>()
        try {
            val uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            val column = "_data"
            val projection = arrayOf(column)
            val cursor = context.contentResolver.query(uri, projection, null, null, null)
            try {
                cursor!!.moveToFirst()
                do {
                    videos.add(cursor.getString(cursor.getColumnIndexOrThrow(column)))
                } while (cursor.moveToNext())
                cursor.close()
            } catch (e: Exception) {
                Log.e(TAG, "Exception in findAllMedia cursor: ${e.message}")
                // e.printStackTrace()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception in findAllMedia: ${e.message}")
        }
        Log.i(TAG, "findAllMedia found ${videos.size} files")
        return videos
    }

//    fun isLocalVideo(uri: Uri): Boolean {
//        return !uri.toString().contains("http://", true) &&
//            !uri.toString().contains("https://", true)
//    }

    fun isSambaVideo(uri: Uri): Boolean {
        return uri.toString().contains("smb://", true)
    }

    fun fileExists(uri: Uri): Boolean {
        return File(uri.toString()).exists()
    }

    fun isDotOrHiddenFile(filename: String): Boolean {
        return filename.startsWith(".")
    }

    fun isSupportedVideoType(filename: String): Boolean {
        return filename.endsWith(".mov", true) ||
            filename.endsWith(".mp4", true) ||
            filename.endsWith(".m4v", true) ||
            filename.endsWith(".webm", true) ||
            filename.endsWith(".mkv", true) ||
            filename.endsWith(".ts", true)
    }

    fun shouldFilter(uri: Uri, folder: String): Boolean {
        if (folder.isEmpty() || folder.isBlank()) {
            return false
        }

        var newFolder = if (folder.first() != '/') "/$folder" else folder
        newFolder = if (newFolder.last() != '/') "$newFolder/" else newFolder

        Log.i(TAG, "Looking for $newFolder in ${uri.path}")
        return !uri.path.toStringOrEmpty().contains(newFolder, true)
    }

    fun filenameToTitleCase(uri: Uri): String {
        val filename = uri.lastPathSegment.toStringOrEmpty()
        val index = filename.lastIndexOf(".")

        // some.video.mov -> some.video
        var location = filename
        if (index > 0) {
            location = filename.substring(0, index)
        }

        // somevideo -> Somevideo
        // city-place_video -> City - Place Video
        // some.video -> Some Video
        location = location.replace("-", ".-.")
        location = location.replace("_", ".")
        return location.split(".").joinToString(" ") { it.lowercase().replaceFirstChar { char -> char.uppercase() } }
    }

    fun filenameToString(uri: Uri): String {
        val filename = uri.lastPathSegment.toStringOrEmpty()
        val index = filename.lastIndexOf(".")

        // some.video.mov -> some.video
        var location = filename
        if (index > 0) {
            location = filename.substring(0, index)
        }

        return location
    }

    @Suppress("NAME_SHADOWING")
    fun fixLegacyFolder(folder: String): String {
        var folder = folder

        if (folder.isEmpty()) {
            return ""
        }

        if (folder.first() != '/') {
            folder = "/$folder"
            Log.i(TAG, "Fixing folder - adding leading slash")
        }

        if (folder.last() == '/') {
            folder = folder.dropLast(1)
            Log.i(TAG, "Fixing folder - removing trailing slash")
        }

        return folder
    }

    private const val TAG = "FileHelper"
}
