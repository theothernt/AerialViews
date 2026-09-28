package com.neilturner.aerialviews.data.storage

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("FileHelper Audio Type Detection Tests")
internal class FileHelperAudioTypeTest {
    @Nested
    @DisplayName("Supported Audio Formats")
    inner class SupportedFormats {
        @Test
        @DisplayName("Should detect MP3 files")
        fun testMp3Files() {
            assertTrue(FileHelper.isSupportedAudioType("song.mp3"))
            assertTrue(FileHelper.isSupportedAudioType("song.MP3"))
            assertTrue(FileHelper.isSupportedAudioType("song.Mp3"))
            assertTrue(FileHelper.isSupportedAudioType("/path/to/song.mp3"))
        }

        @Test
        @DisplayName("Should detect FLAC files")
        fun testFlacFiles() {
            assertTrue(FileHelper.isSupportedAudioType("song.flac"))
            assertTrue(FileHelper.isSupportedAudioType("song.FLAC"))
            assertTrue(FileHelper.isSupportedAudioType("/music/song.flac"))
        }

        @Test
        @DisplayName("Should detect OGG files")
        fun testOggFiles() {
            assertTrue(FileHelper.isSupportedAudioType("song.ogg"))
            assertTrue(FileHelper.isSupportedAudioType("song.OGG"))
        }

        @Test
        @DisplayName("Should detect WAV files")
        fun testWavFiles() {
            assertTrue(FileHelper.isSupportedAudioType("song.wav"))
            assertTrue(FileHelper.isSupportedAudioType("song.WAV"))
        }

        @Test
        @DisplayName("Should detect M4A files")
        fun testM4aFiles() {
            assertTrue(FileHelper.isSupportedAudioType("song.m4a"))
            assertTrue(FileHelper.isSupportedAudioType("song.M4A"))
            assertTrue(FileHelper.isSupportedAudioType("/path/to/05 The Club Rules.m4a"))
            assertTrue(FileHelper.isSupportedAudioType("01 Be Here Now.m4a"))
        }

        @Test
        @DisplayName("Should detect AAC files")
        fun testAacFiles() {
            assertTrue(FileHelper.isSupportedAudioType("song.aac"))
            assertTrue(FileHelper.isSupportedAudioType("song.AAC"))
        }

        @Test
        @DisplayName("Should detect WMA files")
        fun testWmaFiles() {
            assertTrue(FileHelper.isSupportedAudioType("song.wma"))
            assertTrue(FileHelper.isSupportedAudioType("song.WMA"))
        }

        @Test
        @DisplayName("Should detect OPUS files")
        fun testOpusFiles() {
            assertTrue(FileHelper.isSupportedAudioType("song.opus"))
            assertTrue(FileHelper.isSupportedAudioType("song.OPUS"))
        }
    }

    @Nested
    @DisplayName("Unsupported Formats")
    inner class UnsupportedFormats {
        @Test
        @DisplayName("Should reject video files")
        fun testVideoFiles() {
            assertFalse(FileHelper.isSupportedAudioType("video.mp4"))
            assertFalse(FileHelper.isSupportedAudioType("video.mov"))
            assertFalse(FileHelper.isSupportedAudioType("video.mkv"))
            assertFalse(FileHelper.isSupportedAudioType("video.webm"))
            assertFalse(FileHelper.isSupportedAudioType("video.m4v"))
            assertFalse(FileHelper.isSupportedAudioType("video.ts"))
        }

        @Test
        @DisplayName("Should reject image files")
        fun testImageFiles() {
            assertFalse(FileHelper.isSupportedAudioType("image.jpg"))
            assertFalse(FileHelper.isSupportedAudioType("image.jpeg"))
            assertFalse(FileHelper.isSupportedAudioType("image.png"))
            assertFalse(FileHelper.isSupportedAudioType("image.gif"))
            assertFalse(FileHelper.isSupportedAudioType("image.webp"))
            assertFalse(FileHelper.isSupportedAudioType("image.heic"))
        }

        @Test
        @DisplayName("Should reject other file types")
        fun testOtherFiles() {
            assertFalse(FileHelper.isSupportedAudioType("document.pdf"))
            assertFalse(FileHelper.isSupportedAudioType("text.txt"))
            assertFalse(FileHelper.isSupportedAudioType("archive.zip"))
            assertFalse(FileHelper.isSupportedAudioType("noextension"))
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    inner class EdgeCases {
        @Test
        @DisplayName("Should handle paths with spaces")
        fun testPathsWithSpaces() {
            assertTrue(FileHelper.isSupportedAudioType("/music/my song.m4a"))
            assertTrue(FileHelper.isSupportedAudioType("/path/to/05 The Club Rules.m4a"))
        }

        @Test
        @DisplayName("Should handle paths with special characters")
        fun testPathsWithSpecialChars() {
            assertTrue(FileHelper.isSupportedAudioType("/music/song (1).m4a"))
            assertTrue(FileHelper.isSupportedAudioType("/music/song[2023].m4a"))
        }

        @Test
        @DisplayName("Should handle full URIs")
        fun testFullUris() {
            assertTrue(FileHelper.isSupportedAudioType("smb://user@host/share/path/song.m4a"))
            assertTrue(FileHelper.isSupportedAudioType("file:///storage/emulated/0/Music/song.mp3"))
        }

        @Test
        @DisplayName("Should reject similar but incorrect extensions")
        fun testSimilarExtensions() {
            assertFalse(FileHelper.isSupportedAudioType("file.m4a.bak"))
            assertFalse(FileHelper.isSupportedAudioType("file.mp3.txt"))
            assertFalse(FileHelper.isSupportedAudioType("file_m4a"))
        }
    }
}
