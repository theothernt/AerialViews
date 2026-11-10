package com.neilturner.aerialviews.models

import android.net.Uri
import com.neilturner.aerialviews.models.videos.AerialMedia
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
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

    /**
     * Regression: `size` stated by the playlist is larger than the number of rows the chunk source
     * can serve. This is what happened when WiFi-only filtering was applied as a SQL view over the
     * playlist cache, so the cached size counted remote items the filtered query could not return.
     * Iterating used to replay the tail of the filtered window and then throw
     * IllegalStateException("Playlist is empty").
     */
    @Test
    fun `playlist shrinks instead of crashing when size exceeds what the chunk source can serve`() {
        val declaredSize = 100
        val servable = 40
        val allMedia = List(declaredSize) { testMedia() }
        val servableMedia = allMedia.subList(0, servable)

        val playlist =
            MediaPlaylist(
                initialVideos = servableMedia,
                startPosition = -1,
                size = declaredSize,
                windowOffset = 0,
                fetchChunk = { offset, limit ->
                    val end = (offset + limit).coerceAtMost(servable)
                    if (offset < servable) servableMedia.subList(offset, end) else emptyList()
                },
            )

        // Must cycle the servable items in order, three times over, with no repeats and no
        // exception. Previously the tail of the filtered window was replayed and then
        // IllegalStateException("Playlist is empty") was thrown.
        val expected = List(3) { servableMedia }.flatten()
        val actual = (0 until expected.size).map { playlist.nextItem() }

        assertEquals(expected, actual)
        assertEquals(servable, playlist.size, "Playlist should have discovered its real length")
    }

    @Test
    fun `previousItem shrinks instead of crashing when size exceeds what the chunk source can serve`() {
        val declaredSize = 100
        val servable = 40
        val allMedia = List(declaredSize) { testMedia() }
        val servableMedia = allMedia.subList(0, servable)

        val playlist =
            MediaPlaylist(
                initialVideos = servableMedia,
                startPosition = 0,
                size = declaredSize,
                windowOffset = 0,
                fetchChunk = { offset, limit ->
                    val end = (offset + limit).coerceAtMost(servable)
                    if (offset < servable) servableMedia.subList(offset, end) else emptyList()
                },
            )

        // The first backwards step wraps using the declared size, which is only disproved once the
        // short chunk comes back, so it lands wherever the clamp puts it. It must still be a
        // servable item rather than an exception or a replay.
        val first = playlist.previousItem()
        assertTrue(first in servableMedia, "Expected a servable item, got a replay or a crash")

        // From there the playlist walks a complete backwards cycle over the real length: every
        // servable item exactly once, no repeats, no exception.
        val collected = listOf(first) + (0 until servable - 1).map { playlist.previousItem() }

        assertEquals(servable, playlist.size, "Playlist should have discovered its real length")
        assertEquals(servable, collected.distinct().size, "Expected no repeated items in a full backwards cycle")
        collected.forEach { assertTrue(it in servableMedia, "Expected only servable items") }
    }

    @Test
    fun `nextItem returns null instead of throwing when the chunk source is empty`() {
        val playlist =
            MediaPlaylist(
                initialVideos = emptyList(),
                startPosition = -1,
                size = 25,
                windowOffset = 0,
                fetchChunk = { _, _ -> emptyList() },
            )

        assertNull(playlist.nextItem())
        assertNull(playlist.previousItem())
        assertEquals(0, playlist.size)
    }

    @Test
    fun `nextItem returns null on an empty in-memory playlist`() {
        val playlist = MediaPlaylist(emptyList())

        assertEquals(0, playlist.size)
        assertNull(playlist.nextItem())
        assertNull(playlist.previousItem())
    }
}
