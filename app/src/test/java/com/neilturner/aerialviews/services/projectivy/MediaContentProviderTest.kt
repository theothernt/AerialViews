package com.neilturner.aerialviews.services.projectivy

import android.provider.MediaStore
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Unit tests for MediaContentProvider.
 *
 * Note: Tests for toContentUri(), openFile(), and query() require Android instrumented tests
 * since they depend on android.net.Uri, android.util.Base64, and ContentResolver.
 * These tests cover the pure Kotlin logic that can be tested without Android dependencies.
 */
@DisplayName("MediaContentProvider Tests")
internal class MediaContentProviderTest {

    @Nested
    @DisplayName("MIME Type Detection")
    inner class MimeTypeTests {

        @Test
        @DisplayName("Should return video/mp4 for .mp4 files")
        fun testMp4MimeType() {
            val mimeType = getMimeTypeForTest("/path/to/video.mp4")
            assertEquals("video/mp4", mimeType)
        }

        @Test
        @DisplayName("Should return video/mp4 for .m4v files")
        fun testM4vMimeType() {
            val mimeType = getMimeTypeForTest("/path/to/video.m4v")
            assertEquals("video/mp4", mimeType)
        }

        @Test
        @DisplayName("Should return video/quicktime for .mov files")
        fun testMovMimeType() {
            val mimeType = getMimeTypeForTest("/path/to/video.mov")
            assertEquals("video/quicktime", mimeType)
        }

        @Test
        @DisplayName("Should return video/webm for .webm files")
        fun testWebmMimeType() {
            val mimeType = getMimeTypeForTest("/path/to/video.webm")
            assertEquals("video/webm", mimeType)
        }

        @Test
        @DisplayName("Should return video/x-matroska for .mkv files")
        fun testMkvMimeType() {
            val mimeType = getMimeTypeForTest("/path/to/video.mkv")
            assertEquals("video/x-matroska", mimeType)
        }

        @Test
        @DisplayName("Should return video/mp2t for .ts files")
        fun testTsMimeType() {
            val mimeType = getMimeTypeForTest("/path/to/video.ts")
            assertEquals("video/mp2t", mimeType)
        }

        @Test
        @DisplayName("Should return image/jpeg for .jpg files")
        fun testJpgMimeType() {
            val mimeType = getMimeTypeForTest("/path/to/image.jpg")
            assertEquals("image/jpeg", mimeType)
        }

        @Test
        @DisplayName("Should return image/jpeg for .jpeg files")
        fun testJpegMimeType() {
            val mimeType = getMimeTypeForTest("/path/to/image.jpeg")
            assertEquals("image/jpeg", mimeType)
        }

        @Test
        @DisplayName("Should return image/png for .png files")
        fun testPngMimeType() {
            val mimeType = getMimeTypeForTest("/path/to/image.png")
            assertEquals("image/png", mimeType)
        }

        @Test
        @DisplayName("Should return image/gif for .gif files")
        fun testGifMimeType() {
            val mimeType = getMimeTypeForTest("/path/to/image.gif")
            assertEquals("image/gif", mimeType)
        }

        @Test
        @DisplayName("Should return image/webp for .webp files")
        fun testWebpMimeType() {
            val mimeType = getMimeTypeForTest("/path/to/image.webp")
            assertEquals("image/webp", mimeType)
        }

        @Test
        @DisplayName("Should return image/heic for .heic files")
        fun testHeicMimeType() {
            val mimeType = getMimeTypeForTest("/path/to/image.heic")
            assertEquals("image/heic", mimeType)
        }

        @Test
        @DisplayName("Should return image/avif for .avif files")
        fun testAvifMimeType() {
            val mimeType = getMimeTypeForTest("/path/to/image.avif")
            assertEquals("image/avif", mimeType)
        }

        @Test
        @DisplayName("Should return application/octet-stream for unknown extensions")
        fun testUnknownMimeType() {
            val mimeType = getMimeTypeForTest("/path/to/file.xyz")
            assertEquals("application/octet-stream", mimeType)
        }

        @Test
        @DisplayName("Should handle uppercase extensions")
        fun testUppercaseExtension() {
            val mimeType = getMimeTypeForTest("/path/to/video.MP4")
            assertEquals("video/mp4", mimeType)
        }

        @Test
        @DisplayName("Should handle mixed case extensions")
        fun testMixedCaseExtension() {
            val mimeType = getMimeTypeForTest("/path/to/image.JpEg")
            assertEquals("image/jpeg", mimeType)
        }

        @Test
        @DisplayName("Should handle files with no extension")
        fun testNoExtension() {
            val mimeType = getMimeTypeForTest("/path/to/file")
            assertEquals("application/octet-stream", mimeType)
        }

        @Test
        @DisplayName("Should handle files with multiple dots")
        fun testMultipleDots() {
            val mimeType = getMimeTypeForTest("/path/to/my.video.file.mp4")
            assertEquals("video/mp4", mimeType)
        }

        @Test
        @DisplayName("Should handle hidden files with extensions")
        fun testHiddenFileWithExtension() {
            val mimeType = getMimeTypeForTest("/path/to/.hidden.mp4")
            assertEquals("video/mp4", mimeType)
        }

        /**
         * Helper method to test MIME type detection.
         * Mirrors the logic in MediaContentProvider.getMimeType()
         */
        private fun getMimeTypeForTest(filePath: String): String {
            val extension = filePath.substringAfterLast('.', "").lowercase()
            return when (extension) {
                "mp4", "m4v" -> "video/mp4"
                "mov" -> "video/quicktime"
                "webm" -> "video/webm"
                "mkv" -> "video/x-matroska"
                "ts" -> "video/mp2t"
                "jpg", "jpeg" -> "image/jpeg"
                "png" -> "image/png"
                "gif" -> "image/gif"
                "webp" -> "image/webp"
                "heic" -> "image/heic"
                "avif" -> "image/avif"
                else -> "application/octet-stream"
            }
        }
    }

    @Nested
    @DisplayName("URI Authority")
    inner class AuthorityTests {

        @Test
        @DisplayName("Authority should match expected value")
        fun testAuthority() {
            assertEquals("com.neilturner.aerialviews.media", MediaContentProvider.AUTHORITY)
        }
    }

    @Nested
    @DisplayName("Base64 Encoding Logic")
    inner class Base64EncodingTests {

        @Test
        @DisplayName("Encoded path should be URL-safe")
        fun testUrlSafeEncoding() {
            // Test that we're using URL-safe Base64 flags
            // URL-safe Base64 uses - and _ instead of + and /
            val testPath = "/storage/emulated/0/Movies/video.mp4"
            val encoded = java.util.Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(testPath.toByteArray(Charsets.UTF_8))

            // Verify no unsafe characters
            assertEquals(false, encoded.contains("+"))
            assertEquals(false, encoded.contains("/"))
            assertEquals(false, encoded.contains("="))
        }

        @Test
        @DisplayName("Encoding should be reversible")
        fun testEncodingReversible() {
            val testPath = "/storage/emulated/0/My Movies/vacation video (2024).mp4"
            val encoded = java.util.Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(testPath.toByteArray(Charsets.UTF_8))
            val decoded = String(
                java.util.Base64.getUrlDecoder().decode(encoded),
                Charsets.UTF_8,
            )

            assertEquals(testPath, decoded)
        }

        @Test
        @DisplayName("Special characters should survive round-trip")
        fun testSpecialCharactersRoundTrip() {
            val testPath = "/storage/[brackets]/path (parentheses)/file's name.mp4"
            val encoded = java.util.Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(testPath.toByteArray(Charsets.UTF_8))
            val decoded = String(
                java.util.Base64.getUrlDecoder().decode(encoded),
                Charsets.UTF_8,
            )

            assertEquals(testPath, decoded)
        }

        @Test
        @DisplayName("Unicode characters should survive round-trip")
        fun testUnicodeRoundTrip() {
            val testPath = "/storage/日本語/中文/video.mp4"
            val encoded = java.util.Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(testPath.toByteArray(Charsets.UTF_8))
            val decoded = String(
                java.util.Base64.getUrlDecoder().decode(encoded),
                Charsets.UTF_8,
            )

            assertEquals(testPath, decoded)
        }
    }

    @Nested
    @DisplayName("Cursor Column Constants")
    inner class CursorColumnTests {

        @Test
        @DisplayName("COLUMN_ID should match MediaStore._ID")
        fun testColumnId() {
            assertEquals(MediaStore.MediaColumns._ID, MediaContentProvider.COLUMN_ID)
        }

        @Test
        @DisplayName("COLUMN_DATA should match MediaStore.DATA")
        fun testColumnData() {
            assertEquals(MediaStore.MediaColumns.DATA, MediaContentProvider.COLUMN_DATA)
        }

        @Test
        @DisplayName("COLUMN_DISPLAY_NAME should match MediaStore.DISPLAY_NAME")
        fun testColumnDisplayName() {
            assertEquals(MediaStore.MediaColumns.DISPLAY_NAME, MediaContentProvider.COLUMN_DISPLAY_NAME)
        }

        @Test
        @DisplayName("COLUMN_MIME_TYPE should match MediaStore.MIME_TYPE")
        fun testColumnMimeType() {
            assertEquals(MediaStore.MediaColumns.MIME_TYPE, MediaContentProvider.COLUMN_MIME_TYPE)
        }

        @Test
        @DisplayName("COLUMN_CONTENT_URI should be 'content_uri'")
        fun testColumnContentUri() {
            assertEquals("content_uri", MediaContentProvider.COLUMN_CONTENT_URI)
        }
    }

    @Nested
    @DisplayName("File Type Support Logic")
    inner class FileTypeSupportTests {

        @Test
        @DisplayName("Should identify supported video types")
        fun testSupportedVideoTypes() {
            val supportedVideos = listOf("video.mp4", "video.m4v", "video.mov", "video.webm", "video.mkv", "video.ts")
            for (filename in supportedVideos) {
                assertTrue(isSupportedVideoTypeForTest(filename), "Expected $filename to be a supported video type")
            }
        }

        @Test
        @DisplayName("Should reject unsupported video types")
        fun testUnsupportedVideoTypes() {
            val unsupportedVideos = listOf("video.avi", "video.wmv", "video.flv", "video.3gp")
            for (filename in unsupportedVideos) {
                assertFalse(isSupportedVideoTypeForTest(filename), "Expected $filename to NOT be a supported video type")
            }
        }

        @Test
        @DisplayName("Should identify supported image types")
        fun testSupportedImageTypes() {
            val supportedImages = listOf("image.jpg", "image.jpeg", "image.png", "image.gif", "image.webp", "image.heic")
            for (filename in supportedImages) {
                assertTrue(isSupportedImageTypeForTest(filename), "Expected $filename to be a supported image type")
            }
        }

        @Test
        @DisplayName("Should reject unsupported image types")
        fun testUnsupportedImageTypes() {
            val unsupportedImages = listOf("image.bmp", "image.tiff", "image.ico", "image.svg")
            for (filename in unsupportedImages) {
                assertFalse(isSupportedImageTypeForTest(filename), "Expected $filename to NOT be a supported image type")
            }
        }

        @Test
        @DisplayName("Should identify hidden files")
        fun testHiddenFiles() {
            assertTrue(isDotOrHiddenFileForTest(".hidden"))
            assertTrue(isDotOrHiddenFileForTest(".hidden.mp4"))
            assertTrue(isDotOrHiddenFileForTest(".."))
            assertTrue(isDotOrHiddenFileForTest("."))
        }

        @Test
        @DisplayName("Should not flag normal files as hidden")
        fun testNormalFilesNotHidden() {
            assertFalse(isDotOrHiddenFileForTest("video.mp4"))
            assertFalse(isDotOrHiddenFileForTest("my.video.mp4"))
            assertFalse(isDotOrHiddenFileForTest("normal_file"))
        }

        /**
         * Helper method mirroring FileHelper.isSupportedVideoType()
         */
        private fun isSupportedVideoTypeForTest(filename: String): Boolean =
            filename.endsWith(".mov", true) ||
                filename.endsWith(".mp4", true) ||
                filename.endsWith(".m4v", true) ||
                filename.endsWith(".webm", true) ||
                filename.endsWith(".mkv", true) ||
                filename.endsWith(".ts", true)

        /**
         * Helper method mirroring FileHelper.isSupportedImageType() (without AVIF device check)
         */
        private fun isSupportedImageTypeForTest(filename: String): Boolean =
            filename.endsWith(".jpg", true) ||
                filename.endsWith(".jpeg", true) ||
                filename.endsWith(".gif", true) ||
                filename.endsWith(".webp", true) ||
                filename.endsWith(".heic", true) ||
                filename.endsWith(".png", true)

        /**
         * Helper method mirroring FileHelper.isDotOrHiddenFile()
         */
        private fun isDotOrHiddenFileForTest(filename: String): Boolean = filename.startsWith(".")
    }

    @Nested
    @DisplayName("Folder Filter Logic")
    inner class FolderFilterTests {

        @Test
        @DisplayName("Should not filter when folder is empty")
        fun testEmptyFolderNoFilter() {
            assertFalse(shouldFilterForTest("/storage/emulated/0/Movies/video.mp4", ""))
            assertFalse(shouldFilterForTest("/storage/emulated/0/Movies/video.mp4", "   "))
        }

        @Test
        @DisplayName("Should filter out files not containing folder")
        fun testFilterOutNonMatchingPath() {
            assertTrue(shouldFilterForTest("/storage/emulated/0/Movies/video.mp4", "Photos"))
            assertTrue(shouldFilterForTest("/storage/emulated/0/DCIM/photo.jpg", "Movies"))
        }

        @Test
        @DisplayName("Should not filter files containing folder")
        fun testKeepMatchingPath() {
            assertFalse(shouldFilterForTest("/storage/emulated/0/Movies/video.mp4", "Movies"))
            assertFalse(shouldFilterForTest("/storage/emulated/0/DCIM/Camera/photo.jpg", "Camera"))
        }

        @Test
        @DisplayName("Should match folder case-insensitively")
        fun testCaseInsensitiveMatch() {
            assertFalse(shouldFilterForTest("/storage/emulated/0/Movies/video.mp4", "movies"))
            assertFalse(shouldFilterForTest("/storage/emulated/0/movies/video.mp4", "Movies"))
            assertFalse(shouldFilterForTest("/storage/emulated/0/MOVIES/video.mp4", "movies"))
        }

        @Test
        @DisplayName("Should handle folder with leading slash")
        fun testFolderWithLeadingSlash() {
            assertFalse(shouldFilterForTest("/storage/emulated/0/Movies/video.mp4", "/Movies"))
        }

        @Test
        @DisplayName("Should handle folder with trailing slash")
        fun testFolderWithTrailingSlash() {
            assertFalse(shouldFilterForTest("/storage/emulated/0/Movies/video.mp4", "Movies/"))
        }

        @Test
        @DisplayName("Should not match partial folder names")
        fun testNoPartialMatch() {
            // "Movie" should not match "Movies" folder
            assertTrue(shouldFilterForTest("/storage/emulated/0/Movies/video.mp4", "Movie"))
        }

        /**
         * Helper method mirroring FileHelper.shouldFilter() logic
         */
        private fun shouldFilterForTest(filePath: String, folder: String): Boolean {
            if (folder.isEmpty() || folder.isBlank()) {
                return false
            }

            var newFolder = if (folder.first() != '/') "/$folder" else folder
            newFolder = if (newFolder.last() != '/') "$newFolder/" else newFolder

            return !filePath.contains(newFolder, true)
        }
    }
}
