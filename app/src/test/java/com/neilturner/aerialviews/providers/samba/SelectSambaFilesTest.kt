package com.neilturner.aerialviews.providers.samba

import com.neilturner.aerialviews.data.storage.FileHelper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class SelectSambaFilesTest {
    @Test
    fun `audio files never reach the slideshow selection`() {
        val result = selectSambaFiles(mixedShare, includeVideos = true, includePhotos = true, musicEnabled = true)

        val leaked = result.selected.filter { FileHelper.isSupportedAudioType(it.first) }
        assertTrue(
            leaked.isEmpty(),
            "Audio leaked into the slideshow playlist: ${leaked.map { it.first }}",
        )
        assertEquals(
            listOf("/media/clip.mp4", "/media/photo.jpg"),
            result.selected.map { it.first },
        )
    }

    @Test
    fun `audio files never reach the selection even when videos and photos are excluded`() {
        val result = selectSambaFiles(mixedShare, includeVideos = false, includePhotos = false, musicEnabled = true)

        assertTrue(
            result.selected.isEmpty(),
            "Nothing should be selected when both videos and photos are disabled: ${result.selected.map { it.first }}",
        )
        assertEquals(2, result.music, "Tracks should still be counted for the summary")
    }

    @Test
    fun `summary counts each file exactly once`() {
        // 6 files: 1 video, 1 photo, 2 tracks, 2 unsupported.
        val result = selectSambaFiles(mixedShare, includeVideos = true, includePhotos = true, musicEnabled = true)

        assertEquals(1, result.videos)
        assertEquals(1, result.images)
        assertEquals(2, result.music)
        assertEquals(2, result.unsupported, "Tracks must not also be counted as unsupported")

        assertEquals(
            mixedShare.size,
            result.videos + result.images + result.music + result.unsupported,
            "Summary rows must sum to the number of files found",
        )
    }

    @Test
    fun `tracks count as unsupported when music is disabled`() {
        val result = selectSambaFiles(mixedShare, includeVideos = true, includePhotos = true, musicEnabled = false)

        assertEquals(0, result.music)
        assertEquals(
            4,
            result.unsupported,
            "With music disabled the 2 tracks and 2 unrecognised files are all unsupported",
        )
        assertEquals(mixedShare.size, result.videos + result.images + result.music + result.unsupported)
    }

    @Test
    fun `video selection respects the video toggle`() {
        val onlyPhotos = selectSambaFiles(mixedShare, includeVideos = false, includePhotos = true, musicEnabled = true)

        assertEquals(0, onlyPhotos.videos)
        assertEquals(1, onlyPhotos.images)
        assertEquals(listOf("/media/photo.jpg"), onlyPhotos.selected.map { it.first })
    }

    @Test
    fun `an empty share selects nothing and does not go negative`() {
        val result = selectSambaFiles(emptyList(), includeVideos = true, includePhotos = true, musicEnabled = true)

        assertTrue(result.selected.isEmpty())
        assertEquals(0, result.unsupported)
        assertEquals(0, result.videos)
        assertEquals(0, result.images)
        assertEquals(0, result.music)
    }

    @Test
    fun `selection preserves listing order`() {
        val result = selectSambaFiles(mixedShare, includeVideos = true, includePhotos = true, musicEnabled = true)

        val listedOrder = mixedShare.map { it.first }.filter { it in result.selected.map { f -> f.first } }
        assertEquals(listedOrder, result.selected.map { it.first })
    }

    @Test
    fun `selected entries keep their original timestamp`() {
        val result = selectSambaFiles(mixedShare, includeVideos = true, includePhotos = true, musicEnabled = true)

        assertTrue(result.selected.all { (path, time) -> time > 0L }, "Timestamps should be carried through")
        assertFalse(result.selected.any { (path, _) -> path.contains(".mp3") || path.contains(".flac") })
    }

    /** One video, one photo, two tracks and two unrecognised files. */
    private val mixedShare =
        listOf(
            "/media/clip.mp4" to 50L,
            "/media/photo.jpg" to 40L,
            "/media/song.mp3" to 30L,
            "/media/track.flac" to 20L,
            "/media/readme.txt" to 10L,
            "/media/noextension" to 5L,
        )
}
