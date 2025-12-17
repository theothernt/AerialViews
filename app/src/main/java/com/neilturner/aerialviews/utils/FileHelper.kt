package com.neilturner.aerialviews.utils

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import timber.log.Timber

object FileHelper {
    fun findLocalVideos(context: Context): List<String> = findLocalMedia(context, MediaStore.Video.Media.EXTERNAL_CONTENT_URI, "videos")

    fun findLocalImages(context: Context): List<String> = findLocalMedia(context, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "photos")

    /**
     * Queries MediaStore for media file paths.
     *
     * Note: MediaStore.MediaColumns.DATA is deprecated on Android 10+ (API 29) due to
     * scoped storage, but it remains the only way to get actual file paths (not content:// URIs).
     * This still works on Android TV devices which typically have relaxed storage restrictions.
     */
    private fun findLocalMedia(
        context: Context,
        contentUri: Uri,
        mediaType: String,
    ): List<String> {
        val results = mutableListOf<String>()

        @Suppress("DEPRECATION")
        val column = MediaStore.MediaColumns.DATA
        val projection = arrayOf(column)
        try {
            context.contentResolver
                .query(contentUri, projection, null, null, null)
                ?.use { cursor ->
                    val columnIndex = cursor.getColumnIndexOrThrow(column)
                    while (cursor.moveToNext()) {
                        results.add(cursor.getString(columnIndex))
                    }
                }
        } catch (ex: Exception) {
            Timber.e(ex, "Exception querying MediaStore for $mediaType: ${ex.message}")
        }
        Timber.i("ContentResolver found ${results.size} $mediaType")
        return results
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
        if (folder.isBlank()) {
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

    fun fixLegacyFolder(folder: String): String {
        if (folder.isEmpty()) {
            return ""
        }

        var result = folder

        if (result.first() != '/') {
            result = "/$result"
            Timber.i("Fixing folder - adding leading slash")
        }

        if (result.last() == '/') {
            result = result.dropLast(1)
            Timber.i("Fixing folder - removing trailing slash")
        }

        return result
    }
}
