package com.neilturner.aerialviews.services.projectivy

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.webkit.MimeTypeMap
import androidx.core.net.toUri
import com.neilturner.aerialviews.models.prefs.ProjectivyLocalMediaPrefs
import com.neilturner.aerialviews.utils.FileHelper
import timber.log.Timber
import java.io.File
import java.io.FileNotFoundException

/**
 * ContentProvider that exposes local media files to external apps (e.g., Projectivy launcher).
 *
 * This provider allows sharing local video and image files via content:// URIs,
 * which is required for cross-app file access on modern Android versions.
 *
 * URI format: content://com.neilturner.aerialviews.media/local/{encoded-file-path}
 *
 * The file path is Base64-encoded to handle special characters and path separators safely.
 *
 * Access is controlled by ProjectivyLocalMediaPrefs:
 * - enabled: Controls whether local media sharing is active
 * - filterEnabled/filterFolder: Filters files by path containing a specific folder name
 */
class MediaContentProvider : ContentProvider() {

    companion object {
        const val AUTHORITY = "com.neilturner.aerialviews.media"
        private const val LOCAL_MEDIA = 1

        private val uriMatcher = UriMatcher(UriMatcher.NO_MATCH).apply {
            // Match: content://com.neilturner.aerialviews.media/local/*
            addURI(AUTHORITY, "local/*", LOCAL_MEDIA)
        }

        /**
         * Converts a local file:// URI to a content:// URI that can be shared with other apps.
         *
         * @param fileUri The original file:// URI
         * @return A content:// URI if the input is a file URI, otherwise returns the original URI
         */
        fun toContentUri(fileUri: Uri): Uri {
            val path = fileUri.path ?: return fileUri

            // Only convert file:// URIs, leave http/https as-is
            if (fileUri.scheme != "file" && fileUri.scheme != null) {
                return fileUri
            }

            // Base64 encode the path to handle special characters safely
            val encodedPath = android.util.Base64.encodeToString(
                path.toByteArray(Charsets.UTF_8),
                android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP,
            )

            return Uri.Builder()
                .scheme("content")
                .authority(AUTHORITY)
                .appendPath("local")
                .appendPath(encodedPath)
                .build()
        }

        /**
         * Extracts the original file path from a content:// URI.
         *
         * @param contentUri The content:// URI
         * @return The decoded file path, or null if the URI is invalid
         */
        private fun extractFilePath(contentUri: Uri): String? {
            val segments = contentUri.pathSegments
            if (segments.size < 2 || segments[0] != "local") {
                return null
            }

            val encodedPath = segments[1]
            return try {
                String(
                    android.util.Base64.decode(
                        encodedPath,
                        android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP,
                    ),
                    Charsets.UTF_8,
                )
            } catch (e: Exception) {
                Timber.e(e, "Failed to decode file path from URI: $contentUri")
                null
            }
        }
    }

    override fun onCreate(): Boolean {
        Timber.d("MediaContentProvider created")
        return true
    }

    override fun getType(uri: Uri): String? {
        return when (uriMatcher.match(uri)) {
            LOCAL_MEDIA -> {
                val filePath = extractFilePath(uri) ?: return null
                getMimeType(filePath)
            }
            else -> null
        }
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        if (mode != "r") {
            throw SecurityException("Only read access is allowed")
        }

        return when (uriMatcher.match(uri)) {
            LOCAL_MEDIA -> {
                // Check if local media provider is enabled
                if (!ProjectivyLocalMediaPrefs.enabled) {
                    Timber.d("Local media provider is disabled")
                    throw SecurityException("Local media provider is disabled")
                }

                val filePath = extractFilePath(uri)
                    ?: throw FileNotFoundException("Invalid URI: $uri")

                val file = File(filePath)
                if (!file.exists()) {
                    throw FileNotFoundException("File not found: $filePath")
                }

                if (!file.canRead()) {
                    throw SecurityException("Cannot read file: $filePath")
                }

                // Check if file type is supported (video or image)
                val filename = file.name
                if (!FileHelper.isSupportedVideoType(filename) &&
                    !FileHelper.isSupportedImageType(filename)
                ) {
                    Timber.d("Unsupported file type: $filename")
                    throw SecurityException("Unsupported file type: $filename")
                }

                // Apply folder filter if enabled
                if (ProjectivyLocalMediaPrefs.filterEnabled) {
                    val filterFolder = ProjectivyLocalMediaPrefs.filterFolder
                    val fileUri = filePath.toUri()
                    if (FileHelper.shouldFilter(fileUri, filterFolder)) {
                        Timber.d("File filtered out by folder filter: $filePath (filter: $filterFolder)")
                        throw SecurityException("File does not match folder filter")
                    }
                }

                Timber.d("Opening file for external access: $filePath")
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            }
            else -> throw FileNotFoundException("Unknown URI: $uri")
        }
    }

    private fun getMimeType(filePath: String): String {
        val extension = filePath.substringAfterLast('.', "").lowercase()
        val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)

        return mimeType ?: when (extension) {
            // Video types
            "mp4", "m4v" -> "video/mp4"
            "mov" -> "video/quicktime"
            "webm" -> "video/webm"
            "mkv" -> "video/x-matroska"
            "ts" -> "video/mp2t"
            // Image types
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "heic" -> "image/heic"
            "avif" -> "image/avif"
            else -> "application/octet-stream"
        }
    }

    // Required ContentProvider methods - not used for file serving

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
