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
        Timber.i("ContentResolver found ${videos.size} videos")
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
        Timber.i("ContentResolver found ${images.size} photos")
        return images
    }

    fun isDotOrHiddenFile(filename: String): Boolean = filename.startsWith(".")

    fun isSupportedVideoType(filename: String): Boolean =
        filename.endsWith(".mov", true) ||
            filename.endsWith(".mp4", true) ||
            filename.endsWith(".m4v", true) ||
            filename.endsWith(".webm", true) ||
            filename.endsWith(".mkv", true) ||
            filename.endsWith(".ts", true)

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
            filename.endsWith(".heic", true) ||
            // HEIF format
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

    fun formatFolderAndFilenameFromUri(
        uri: Uri,
        includeFilename: Boolean = false,
        pathDepth: Int = 1,
    ): String {
        val segments = uri.pathSegments.toMutableList()
        segments.removeAt(segments.lastIndex) // Remove filename at the end of the list
        val segmentCount = segments.size
        val filename = uri.filenameWithoutExtension
        var path = ""

        if (segmentCount > 0) {
            val effectiveDepth = pathDepth.coerceIn(1, minOf(5, segmentCount))
            val relevantSegments = segments.takeLast(effectiveDepth)
            path = relevantSegments.joinToString(" / ")
        }

        return if (includeFilename && path.isNotBlank()) {
            "$path / $filename"
        } else if (includeFilename && path.isBlank()) {
            filename
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
