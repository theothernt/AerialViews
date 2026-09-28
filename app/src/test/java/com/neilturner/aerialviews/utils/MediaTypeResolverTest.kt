package com.neilturner.aerialviews.utils

import com.neilturner.aerialviews.models.enums.AerialMediaType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("aerialMediaTypeFor Tests")
internal class MediaTypeResolverTest {
    @Nested
    @DisplayName("Video Files")
    inner class VideoFiles {
        @Test
        @DisplayName("Should resolve supported video extensions to VIDEO")
        fun testVideoExtensions() {
            listOf("clip.mov", "clip.mp4", "clip.m4v", "clip.webm", "clip.mkv", "clip.ts").forEach { filename ->
                assertEquals(AerialMediaType.VIDEO, aerialMediaTypeFor(filename), "Expected VIDEO for $filename")
            }
        }

        @Test
        @DisplayName("Should resolve video files regardless of case")
        fun testVideoExtensionsCaseInsensitive() {
            assertEquals(AerialMediaType.VIDEO, aerialMediaTypeFor("CLIP.MP4"))
            assertEquals(AerialMediaType.VIDEO, aerialMediaTypeFor("Clip.Mkv"))
        }
    }

    @Nested
    @DisplayName("Image Files")
    inner class ImageFiles {
        @Test
        @DisplayName("Should resolve supported image extensions to IMAGE")
        fun testImageExtensions() {
            listOf("photo.jpg", "photo.jpeg", "photo.png", "photo.gif", "photo.webp", "photo.heic").forEach { filename ->
                assertEquals(AerialMediaType.IMAGE, aerialMediaTypeFor(filename), "Expected IMAGE for $filename")
            }
        }

        @Test
        @DisplayName("Should resolve image files regardless of case")
        fun testImageExtensionsCaseInsensitive() {
            assertEquals(AerialMediaType.IMAGE, aerialMediaTypeFor("PHOTO.JPG"))
        }

        // AVIF is intentionally not covered: FileHelper.isSupportedImageType consults
        // DeviceHelper.hasAvifSupport(), which reads Build.VERSION and cannot run in a plain JVM test.
    }

    @Nested
    @DisplayName("Audio Files")
    inner class AudioFiles {
        @Test
        @DisplayName("Should return null for every supported audio extension")
        fun testAudioExtensionsReturnNull() {
            listOf(
                "song.mp3",
                "song.flac",
                "song.ogg",
                "song.wav",
                "song.m4a",
                "song.aac",
                "song.wma",
                "song.opus",
            ).forEach { filename ->
                assertNull(aerialMediaTypeFor(filename), "Expected null for $filename")
            }
        }

        @Test
        @DisplayName("Should return null for audio files regardless of case")
        fun testAudioExtensionsCaseInsensitive() {
            assertNull(aerialMediaTypeFor("SONG.MP3"))
            assertNull(aerialMediaTypeFor("Song.Flac"))
        }

        /**
         * Regression guard: audio must never be classified as slideshow media. AerialMedia.type
         * defaults to VIDEO, so any audio file reaching the media playlist is played as a video
         * and produces a black screen.
         */
        @Test
        @DisplayName("Should never resolve audio to VIDEO or IMAGE")
        fun testAudioNeverResolvesToSlideshowType() {
            val audioFiles =
                listOf("track.mp3", "track.flac", "track.ogg", "track.wav", "track.m4a", "track.aac", "track.wma", "track.opus")

            audioFiles.forEach { filename ->
                val type = aerialMediaTypeFor(filename)
                assertNull(type, "Audio file $filename must not be classified as slideshow media")
            }
        }
    }

    @Nested
    @DisplayName("Unsupported Files")
    inner class UnsupportedFiles {
        @Test
        @DisplayName("Should return null for unrelated extensions")
        fun testUnrelatedExtensions() {
            listOf("document.pdf", "text.txt", "archive.zip", "binary.bin", "noextension").forEach { filename ->
                assertNull(aerialMediaTypeFor(filename), "Expected null for $filename")
            }
        }

        @Test
        @DisplayName("Should return null for empty filename")
        fun testEmptyFilename() {
            assertNull(aerialMediaTypeFor(""))
        }

        @Test
        @DisplayName("Should not match extensions that are only a suffix of the real one")
        fun testNearMissExtensions() {
            // Ends-with matching means these must NOT resolve - the trailing characters differ.
            assertNull(aerialMediaTypeFor("clip.mp4x"))
            assertNull(aerialMediaTypeFor("song.mp3x"))
            assertNull(aerialMediaTypeFor("photo.jpgx"))
        }
    }

    @Nested
    @DisplayName("Paths")
    inner class Paths {
        @Test
        @DisplayName("Should resolve on the extension for full file paths")
        fun testFullPaths() {
            assertEquals(AerialMediaType.VIDEO, aerialMediaTypeFor("/Videos/Aerial/community/clip.mp4"))
            assertEquals(AerialMediaType.IMAGE, aerialMediaTypeFor("smb://nas/media/Aerial/photo.jpg"))
        }

        @Test
        @DisplayName("Should return null for full paths to audio files")
        fun testFullPathsToAudio() {
            assertNull(aerialMediaTypeFor("/Music/Albums/track.flac"))
            assertNull(aerialMediaTypeFor("smb://nas/media/Music/track.mp3"))
        }
    }
}
