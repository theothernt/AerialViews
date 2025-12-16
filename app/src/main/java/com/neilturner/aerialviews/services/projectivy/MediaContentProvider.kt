package com.neilturner.aerialviews.services.projectivy

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.core.net.toUri
import com.neilturner.aerialviews.models.enums.ProviderMediaType
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
        private const val LOCAL_MEDIA_LIST = 2

        // Cursor columns for query results
        const val COLUMN_ID = MediaStore.MediaColumns._ID
        const val COLUMN_DATA = MediaStore.MediaColumns.DATA
        const val COLUMN_DISPLAY_NAME = MediaStore.MediaColumns.DISPLAY_NAME
        const val COLUMN_MIME_TYPE = MediaStore.MediaColumns.MIME_TYPE
        const val COLUMN_CONTENT_URI = "content_uri"

        private val uriMatcher = UriMatcher(UriMatcher.NO_MATCH).apply {
            // Match: content://com.neilturner.aerialviews.media/local/*
            addURI(AUTHORITY, "local/*", LOCAL_MEDIA)
            // Match: content://com.neilturner.aerialviews.media/local (for listing)
            addURI(AUTHORITY, "local", LOCAL_MEDIA_LIST)
        }

        /**
         * Converts a local file:// URI to a content:// URI that can be shared with other apps.
         *
         * @param fileUri The original file:// URI
         * @return A content:// URI if the input is a file URI, otherwise returns the original URI
         */
        fun toContentUri(fileUri: Uri): Uri {
            Timber.d("toContentUri() called with: $fileUri")

            val path = fileUri.path ?: run {
                Timber.d("toContentUri() - No path found, returning original URI")
                return fileUri
            }

            // Only convert file:// URIs, leave http/https as-is
            if (fileUri.scheme != "file" && fileUri.scheme != null) {
                Timber.d("toContentUri() - Non-file URI (scheme: ${fileUri.scheme}), returning as-is")
                return fileUri
            }

            // Base64 encode the path to handle special characters safely
            val encodedPath = android.util.Base64.encodeToString(
                path.toByteArray(Charsets.UTF_8),
                android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP,
            )

            val contentUri = Uri.Builder()
                .scheme("content")
                .authority(AUTHORITY)
                .appendPath("local")
                .appendPath(encodedPath)
                .build()

            Timber.d("toContentUri() - Converted to: $contentUri")
            return contentUri
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

    /**
     * Validates if a file is allowed to be accessed based on preferences and file type.
     *
     * @param filePath The absolute path to the file
     * @return true if the file passes all validation checks, false otherwise
     */
    private fun isMediaFileValid(filePath: String): Boolean {
        val file = File(filePath)

        // Check if file exists and is readable
        if (!file.exists() || !file.canRead()) {
            return false
        }

        val filename = file.name

        // Skip hidden files
        if (FileHelper.isDotOrHiddenFile(filename)) {
            return false
        }

        // Check if file type is supported (video or image)
        if (!FileHelper.isSupportedVideoType(filename) &&
            !FileHelper.isSupportedImageType(filename)
        ) {
            return false
        }

        // Apply folder filter if enabled
        if (ProjectivyLocalMediaPrefs.filterEnabled) {
            val filterFolder = ProjectivyLocalMediaPrefs.filterFolder
            val fileUri = filePath.toUri()
            if (FileHelper.shouldFilter(fileUri, filterFolder)) {
                return false
            }
        }

        return true
    }

    override fun getType(uri: Uri): String? {
        Timber.d("getType() called with URI: $uri")
        return when (uriMatcher.match(uri)) {
            LOCAL_MEDIA -> {
                val filePath = extractFilePath(uri) ?: run {
                    Timber.w("getType() - Failed to extract file path from URI: $uri")
                    return null
                }
                val mimeType = getMimeType(filePath)
                Timber.d("getType() - Returning MIME type: $mimeType for path: $filePath")
                mimeType
            }
            else -> {
                Timber.w("getType() - Unknown URI pattern: $uri")
                null
            }
        }
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        Timber.d("openFile() called with URI: $uri, mode: $mode")

        if (mode != "r") {
            Timber.w("openFile() - Rejected: Invalid access mode '$mode' (only 'r' allowed)")
            throw SecurityException("Only read access is allowed")
        }

        return when (uriMatcher.match(uri)) {
            LOCAL_MEDIA -> {
                // Check if local media provider is enabled
                if (!ProjectivyLocalMediaPrefs.enabled) {
                    Timber.w("openFile() - Rejected: Local media provider is disabled")
                    throw SecurityException("Local media provider is disabled")
                }

                val filePath = extractFilePath(uri)
                    ?: throw FileNotFoundException("Invalid URI: $uri").also {
                        Timber.e("openFile() - Failed to extract file path from URI: $uri")
                    }

                Timber.d("openFile() - Extracted file path: $filePath")

                // Validate file using shared helper
                if (!isMediaFileValid(filePath)) {
                    Timber.w("openFile() - Rejected: File validation failed for: $filePath")
                    throw SecurityException("File access not allowed: $filePath")
                }

                Timber.i("openFile() - SUCCESS: Opening file for external access: $filePath")
                ParcelFileDescriptor.open(File(filePath), ParcelFileDescriptor.MODE_READ_ONLY)
            }
            else -> {
                Timber.e("openFile() - Unknown URI pattern: $uri")
                throw FileNotFoundException("Unknown URI: $uri")
            }
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
    ): Cursor? {
        Timber.d("query() called with URI: $uri")

        return when (uriMatcher.match(uri)) {
            LOCAL_MEDIA_LIST -> {
                // Check if local media provider is enabled
                if (!ProjectivyLocalMediaPrefs.enabled) {
                    Timber.w("query() - Local media provider is disabled, returning empty cursor")
                    return createEmptyCursor()
                }

                val ctx = context ?: run {
                    Timber.e("query() - Context is null")
                    return createEmptyCursor()
                }

                Timber.d("query() - Fetching local media files")
                queryLocalMedia(ctx)
            }
            else -> {
                Timber.w("query() - Unknown URI pattern: $uri")
                null
            }
        }
    }

    private fun createEmptyCursor(): MatrixCursor {
        return MatrixCursor(
            arrayOf(COLUMN_ID, COLUMN_DATA, COLUMN_DISPLAY_NAME, COLUMN_MIME_TYPE, COLUMN_CONTENT_URI),
        )
    }

    private fun queryLocalMedia(ctx: android.content.Context): Cursor {
        val columns = arrayOf(COLUMN_ID, COLUMN_DATA, COLUMN_DISPLAY_NAME, COLUMN_MIME_TYPE, COLUMN_CONTENT_URI)
        val cursor = MatrixCursor(columns)

        val mediaType = ProjectivyLocalMediaPrefs.mediaType
        val includeVideos = mediaType != ProviderMediaType.PHOTOS
        val includeImages = mediaType != ProviderMediaType.VIDEOS

        Timber.d("query() - Media type: $mediaType (videos: $includeVideos, images: $includeImages)")

        // Get videos and images using FileHelper based on media type preference
        val videos = if (includeVideos) FileHelper.findLocalVideos(ctx) else emptyList()
        val images = if (includeImages) FileHelper.findLocalImages(ctx) else emptyList()

        // Combine all media files into a single list
        val allMedia = videos + images

        Timber.d("query() - Found ${videos.size} videos and ${images.size} images")

        var id = 0L
        var videoCount = 0
        var imageCount = 0

        // Process all media files in a single loop
        for (filePath in allMedia) {
            // Use shared validation helper
            if (!isMediaFileValid(filePath)) {
                continue
            }

            val file = File(filePath)
            val contentUri = toContentUri(filePath.toUri())
            val mimeType = getMimeType(filePath)

            cursor.addRow(
                arrayOf(
                    id++,
                    filePath,
                    file.name,
                    mimeType,
                    contentUri.toString(),
                ),
            )

            // Track counts for logging
            if (FileHelper.isSupportedVideoType(file.name)) {
                videoCount++
            } else {
                imageCount++
            }
        }

        Timber.i("query() - Returning $videoCount videos and $imageCount images (${cursor.count} total) after filtering")
        return cursor
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        Timber.d("insert() called with URI: $uri (not implemented)")
        return null
    }

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int {
        Timber.d("update() called with URI: $uri (not implemented)")
        return 0
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        Timber.d("delete() called with URI: $uri (not implemented)")
        return 0
    }
}
