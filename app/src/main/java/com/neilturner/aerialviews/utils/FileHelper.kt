@file:Suppress("unused")

package com.neilturner.aerialviews.utils

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import timber.log.Timber

object FileHelper {
    fun findLocalVideos(context: Context): List<String> {
        val videos = mutableListOf<String>()
        val uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val column = MediaStore.MediaColumns.DATA
        val projection = arrayOf(column)
        try {
            val cursor =
                context
                    .contentResolver
                    .query(uri, projection, null, null, null)
                    ?: return videos
            try {
                while (cursor.moveToNext()) {
                    videos.add(cursor.getString(cursor.getColumnIndexOrThrow(column)))
                }
                cursor.close()
            } catch (ex: Exception) {
                Timber.e(ex, "Exception in contentResolver cursor: ${ex.message}")
            }
        } catch (ex: Exception) {
            Timber.e(ex, "Exception in contentResolver query: ${ex.message}")
        }
        Timber.i("ContentResolver found ${videos.size} files")
        return videos
    }

    fun findLocalImages(context: Context): List<String> {
        val images = mutableListOf<String>()
        val uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val column = MediaStore.MediaColumns.DATA
        val projection = arrayOf(column)
        try {
            val cursor =
                context
                    .contentResolver
                    .query(uri, projection, null, null, null)
                    ?: return images
            try {
                while (cursor.moveToNext()) {
                    images.add(cursor.getString(cursor.getColumnIndexOrThrow(column)))
                }
                cursor.close()
            } catch (ex: Exception) {
                Timber.e(ex, "Exception in contentResolver cursor: ${ex.message}")
            }
        } catch (ex: Exception) {
            Timber.e(ex, "Exception in contentResolver query: ${ex.message}")
        }
        Timber.i("ContentResolver found ${images.size} files")
        return images
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

    fun isSupportedImageType(filename: String): Boolean {
        if (filename.endsWith(".avif", true) &&
            DeviceHelper.hasAvifSupport()
        ) { // AVIF - AV1 image format
            return true
        }

        return filename.endsWith(".jpg", true) ||
            filename.endsWith(".jpeg", true) ||
            filename.endsWith(".gif", true) ||
            filename.endsWith(".webp", true) ||
            filename.endsWith(".heic", true) || // HEIF format
            filename.endsWith(".png", true)
    }

    fun shouldFilter(
        uri: Uri,
        folder: String,
    ): Boolean {
        if (folder.isEmpty() || folder.isBlank()) {
            return false
        }

        var newFolder = if (folder.first() != '/') "/$folder" else folder
        newFolder = if (newFolder.last() != '/') "$newFolder/" else newFolder

        Timber.i("Looking for $newFolder in ${uri.path}")
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

    fun folderAndFilenameFromUri(
        uri: Uri,
        includeFilename: Boolean = false,
    ): String {
        val path =
            if (uri.pathSegments.size < 2) {
                ""
            } else {
                val count = uri.pathSegments.size
                uri.pathSegments[count - 2] ?: ""
            }
        return if (includeFilename) {
            "$path / ${uri.filenameWithoutExtension}"
        } else {
            path
        }
    }

    @Suppress("NAME_SHADOWING")
    fun fixLegacyFolder(folder: String): String {
        var folder = folder

        if (folder.isEmpty()) {
            return ""
        }

        if (folder.first() != '/') {
            folder = "/$folder"
            Timber.i("Fixing folder - adding leading slash")
        }

        if (folder.last() == '/') {
            folder = folder.dropLast(1)
            Timber.i("Fixing folder - removing trailing slash")
        }

        return folder
    }
}
