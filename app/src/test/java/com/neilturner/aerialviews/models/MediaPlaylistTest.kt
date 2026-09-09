package com.neilturner.aerialviews.models

import android.net.Uri
import com.neilturner.aerialviews.models.videos.AerialMedia
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

internal class MediaPlaylistTest {
    @Test
    fun `nextItem starts after saved current position`() {
        val first = testMedia()
        val second = testMedia()
        val third = testMedia()
        val playlist = MediaPlaylist(listOf(first, second, third), startPosition = 0)

        assertSame(second, playlist.nextItem())
    }

    @Test
    fun `nextItem starts at first item when no position has been saved`() {
        val first = testMedia()
        val second = testMedia()
        val playlist = MediaPlaylist(listOf(first, second), startPosition = -1)

        assertSame(first, playlist.nextItem())
    }

    @Test
    fun `nextItem loops back to first item when reaching end of playlist with chunk windowing`() {
        val totalItems = 60
        val allMedia = List(totalItems) { testMedia() }

        // Initial window of size 50 (items 0..49)
        val initialChunk = allMedia.subList(0, 50)

        val playlist =
            MediaPlaylist(
                initialVideos = initialChunk,
                startPosition = -1,
                size = totalItems,
                windowOffset = 0,
                fetchChunk = { offset, limit ->
                    val end = (offset + limit).coerceAtMost(totalItems)
                    if (offset < totalItems) allMedia.subList(offset, end) else emptyList()
                },
            )

        // Iterate through all 60 items
        for (i in 0 until totalItems) {
            val item = playlist.nextItem()
            assertSame(allMedia[i], item, "Expected item at index $i")
        }

        // 61st item (wrap around to index 0)
        val loopedItem = playlist.nextItem()
        assertSame(allMedia[0], loopedItem, "Expected playlist to loop back to item 0")
    }

    @Test
    fun `previousItem loops back to end of playlist when at position 0 with chunk windowing`() {
        val totalItems = 60
        val allMedia = List(totalItems) { testMedia() }

        val playlist =
            MediaPlaylist(
                initialVideos = allMedia.subList(0, 50),
                startPosition = 0,
                size = totalItems,
                windowOffset = 0,
                fetchChunk = { offset, limit ->
                    val end = (offset + limit).coerceAtMost(totalItems)
                    if (offset < totalItems) allMedia.subList(offset, end) else emptyList()
                },
            )

        val prevItem = playlist.previousItem()
        assertSame(allMedia[59], prevItem, "Expected previousItem from 0 to loop to item 59")
    }

    private fun testMedia() = AerialMedia(uri = mockk<Uri>(relaxed = true))
}
