package com.neilturner.aerialviews.models

import android.net.Uri
import com.neilturner.aerialviews.models.videos.AerialMedia
import com.neilturner.aerialviews.models.videos.AerialMediaMetadata
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MediaPlaylistTest {
    @Test
    fun `in-memory playlist moves next and previous without cache fetcher`() =
        runTest {
            val playlist = MediaPlaylist(listOf(media(0), media(1), media(2)))

            assertEquals("0", playlist.nextItem().metadata.title)
            assertEquals("1", playlist.nextItem().metadata.title)
            assertEquals("0", playlist.previousItem().metadata.title)
            assertEquals(0, playlist.currentPosition)
        }

    @Test
    fun `in-memory playlist wraps around`() =
        runTest {
            val playlist = MediaPlaylist(listOf(media(0), media(1), media(2)))

            assertEquals("0", playlist.nextItem().metadata.title)
            assertEquals("2", playlist.previousItem().metadata.title)
            assertEquals(2, playlist.currentPosition)
        }

    @Test
    fun `windowed playlist uses initial window when item is present`() =
        runTest {
            var fetchCount = 0
            val playlist =
                MediaPlaylist(
                    initialVideos = mediaRange(10, 10),
                    startPosition = 11,
                    size = 100,
                    windowOffset = 10,
                    fetchChunk = { offset, limit ->
                        fetchCount++
                        mediaRange(offset, limit)
                    },
                )

            assertEquals("12", playlist.nextItem().metadata.title)
            assertEquals(0, fetchCount)
        }

    @Test
    fun `windowed playlist fetches chunk when item is outside initial window`() =
        runTest {
            var fetchCount = 0
            val playlist =
                MediaPlaylist(
                    initialVideos = mediaRange(10, 10),
                    startPosition = 24,
                    size = 100,
                    windowOffset = 10,
                    fetchChunk = { offset, limit ->
                        fetchCount++
                        mediaRange(offset, limit)
                    },
                )

            assertEquals("25", playlist.nextItem().metadata.title)
            assertTrue(fetchCount >= 1)
        }

    private fun mediaRange(
        offset: Int,
        count: Int,
    ): List<AerialMedia> = (offset until offset + count).map { media(it) }

    private fun media(index: Int): AerialMedia =
        AerialMedia(
            uri = mockk<Uri>(),
            metadata = AerialMediaMetadata(title = index.toString()),
        )
}
