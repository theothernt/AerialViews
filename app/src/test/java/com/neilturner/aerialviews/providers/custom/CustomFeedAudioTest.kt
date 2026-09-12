package com.neilturner.aerialviews.providers.custom

import android.net.Uri
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

internal class CustomFeedAudioTest {
    // Mirrors the production JsonHelper configuration used to parse custom feeds
    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

    @BeforeEach
    fun mockUri() {
        mockkStatic(Uri::class)
    }

    @AfterEach
    fun unmockUri() {
        unmockkStatic(Uri::class)
    }

    @Test
    fun `parses the separate audio url from an asset`() {
        val body =
            """
            {
              "assets": [
                {
                  "id": "sunset-beach",
                  "accessibilityLabel": "Sunset over the beach",
                  "url-1080-H264": "https://example.com/video.mp4",
                  "url-4K-SDR": "https://example.com/video-4k.webm",
                  "url-audio": "https://example.com/audio.m4a"
                }
              ]
            }
            """.trimIndent()

        val asset = json.decodeFromString<FeedVideos>(body).assets!!.single()

        assertEquals("https://example.com/audio.m4a", asset.audioUrl)
    }

    @Test
    fun `audio url is null when the feed omits it`() {
        val body = """{ "assets": [ { "url-1080-SDR": "https://example.com/video.mp4" } ] }"""

        val asset = json.decodeFromString<FeedVideos>(body).assets!!.single()

        assertNull(asset.audioUrl)
        assertNull(asset.audioUri())
    }

    @Test
    fun `audio uri is built from the audio url`() {
        val urlSlot = slot<String>()
        every { Uri.parse(capture(urlSlot)) } returns mockk(relaxed = true)
        val body = """{ "assets": [ { "url-audio": "https://example.com/audio.m4a" } ] }"""

        val asset = json.decodeFromString<FeedVideos>(body).assets!!.single()

        asset.audioUri()
        assertEquals("https://example.com/audio.m4a", urlSlot.captured)
    }

    @Test
    fun `blank audio url is treated as absent`() {
        val body = """{ "assets": [ { "url-audio": "  " } ] }"""

        val asset = json.decodeFromString<FeedVideos>(body).assets!!.single()

        assertNull(asset.audioUri())
    }

    @Test
    fun `photos in the same feed are unaffected`() {
        val body =
            """
            {
              "assets": [ { "url-1080-SDR": "https://example.com/v.mp4", "url-audio": "https://example.com/a.m4a" } ],
              "photos": [ { "url-1080": "https://example.com/p.jpg", "title": "Photo" } ]
            }
            """.trimIndent()

        val feed = json.decodeFromString<FeedVideos>(body)

        assertEquals("https://example.com/a.m4a", feed.assets!!.single().audioUrl)
        assertEquals("Photo", feed.photos!!.single().title)
    }
}
