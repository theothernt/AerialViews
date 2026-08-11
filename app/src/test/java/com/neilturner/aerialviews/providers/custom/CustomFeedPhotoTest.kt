package com.neilturner.aerialviews.providers.custom

import android.net.Uri
import com.neilturner.aerialviews.models.enums.VideoQuality
import com.neilturner.aerialviews.models.videos.Comm1Photo
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class CustomFeedPhotoTest {
    // Mirrors the production JsonHelper configuration used to parse custom feeds
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Test
    fun `parses photos array with urls and title, ignoring unknown fields`() {
        val body =
            """
            {
              "category": "art-gallery",
              "name": "Art Gallery",
              "count": 1,
              "photos": [
                {
                  "id": "ART_1",
                  "source": 9,
                  "url-1080": "https://example.com/art-1080.jpg",
                  "url-4K": "https://example.com/art-4k.jpg",
                  "title": "A lovely painting",
                  "attribution": "Museum",
                  "added": "2026-07-04"
                }
              ]
            }
            """.trimIndent()

        val feed = json.decodeFromString<FeedVideos>(body)

        assertNull(feed.assets)
        assertEquals(1, feed.photos?.size)
        val photo = feed.photos!!.single()
        assertEquals("https://example.com/art-1080.jpg", photo.url1080)
        assertEquals("https://example.com/art-4k.jpg", photo.url4k)
        assertEquals("A lovely painting", photo.title)
    }

    @Test
    fun `parses a feed containing both videos and photos`() {
        val body =
            """
            {
              "assets": [
                { "url-1080-SDR": "https://example.com/video-1080.mp4", "url-4K-SDR": "https://example.com/video-4k.mp4" }
              ],
              "photos": [
                { "url-1080": "https://example.com/photo.jpg", "title": "Photo" }
              ]
            }
            """.trimIndent()

        val feed = json.decodeFromString<FeedVideos>(body)

        assertEquals(1, feed.assets?.size)
        assertEquals(1, feed.photos?.size)
        assertEquals("Photo", feed.photos!!.single().title)
    }

    @Test
    fun `photos are null when only videos are present`() {
        val body = """{ "assets": [ { "url-1080-SDR": "https://example.com/v.mp4" } ] }"""

        val feed = json.decodeFromString<FeedVideos>(body)

        assertEquals(1, feed.assets?.size)
        assertNull(feed.photos)
    }

    @BeforeEach
    fun mockUri() {
        mockkStatic(Uri::class)
    }

    @AfterEach
    fun unmockUri() {
        unmockkStatic(Uri::class)
    }

    private fun urlChosenFor(
        photoJson: String,
        quality: VideoQuality?,
    ): String {
        val urlSlot = slot<String>()
        every { Uri.parse(capture(urlSlot)) } returns mockk(relaxed = true)
        val photo = json.decodeFromString<Comm1Photo>(photoJson)
        photo.uriAtQuality(quality)
        return urlSlot.captured
    }

    private val bothQualities =
        """{ "url-1080": "https://example.com/1080.jpg", "url-4K": "https://example.com/4k.jpg" }"""

    @Test
    fun `4K quality selects the 4K url`() {
        assertEquals("https://example.com/4k.jpg", urlChosenFor(bothQualities, VideoQuality.VIDEO_4K_SDR))
        assertEquals("https://example.com/4k.jpg", urlChosenFor(bothQualities, VideoQuality.VIDEO_4K_HDR))
    }

    @Test
    fun `1080 quality selects the 1080 url`() {
        assertEquals("https://example.com/1080.jpg", urlChosenFor(bothQualities, VideoQuality.VIDEO_1080_SDR))
    }

    @Test
    fun `null quality defaults to the 1080 url`() {
        assertEquals("https://example.com/1080.jpg", urlChosenFor(bothQualities, null))
    }

    @Test
    fun `4K quality falls back to 1080 when 4K is missing`() {
        val onlyHd = """{ "url-1080": "https://example.com/1080.jpg" }"""
        assertEquals("https://example.com/1080.jpg", urlChosenFor(onlyHd, VideoQuality.VIDEO_4K_SDR))
    }

    @Test
    fun `1080 quality falls back to 4K when 1080 is missing`() {
        val only4k = """{ "url-4K": "https://example.com/4k.jpg" }"""
        assertEquals("https://example.com/4k.jpg", urlChosenFor(only4k, VideoQuality.VIDEO_1080_SDR))
    }
}
