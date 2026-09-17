package com.neilturner.aerialviews.providers.custom

import android.net.Uri
import com.neilturner.aerialviews.models.enums.VideoQuality
import com.neilturner.aerialviews.models.videos.Comm1Video
import io.mockk.CapturingSlot
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class CustomFeedHdrTest {
    // Mirrors the production JsonHelper configuration used to parse custom feeds
    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

    private lateinit var urlSlot: CapturingSlot<String>

    private val allQualities =
        """
        {
          "assets": [
            {
              "id": "night-walk",
              "url-1080-H264": "https://example.com/1080-h264.mp4",
              "url-1080-SDR": "https://example.com/1080-sdr.webm",
              "url-1080-HDR": "https://example.com/1080-hdr.webm",
              "url-4K-SDR": "https://example.com/4k-sdr.webm",
              "url-4K-HDR": "https://example.com/4k-hdr.webm"
            }
          ]
        }
        """.trimIndent()

    @BeforeEach
    fun mockUri() {
        mockkStatic(Uri::class)
        urlSlot = slot()
        every { Uri.parse(capture(urlSlot)) } returns mockk(relaxed = true)
    }

    @AfterEach
    fun unmockUri() {
        unmockkStatic(Uri::class)
    }

    private fun assetFrom(body: String): Comm1Video = json.decodeFromString<FeedVideos>(body).assets!!.single()

    @Test
    fun `1080p HDR selects the 1080p HDR rendition`() {
        assetFrom(allQualities).uriAtQuality(VideoQuality.VIDEO_1080_HDR)

        assertEquals("https://example.com/1080-hdr.webm", urlSlot.captured)
    }

    @Test
    fun `4K HDR selects the 4K HDR rendition`() {
        assetFrom(allQualities).uriAtQuality(VideoQuality.VIDEO_4K_HDR)

        assertEquals("https://example.com/4k-hdr.webm", urlSlot.captured)
    }

    @Test
    fun `4K HDR falls back to 4K SDR when the feed has no HDR rendition`() {
        val body =
            """
            {
              "assets": [
                {
                  "url-1080-H264": "https://example.com/1080-h264.mp4",
                  "url-4K-SDR": "https://example.com/4k-sdr.webm"
                }
              ]
            }
            """.trimIndent()

        assetFrom(body).uriAtQuality(VideoQuality.VIDEO_4K_HDR)

        assertEquals("https://example.com/4k-sdr.webm", urlSlot.captured)
    }

    @Test
    fun `1080p HDR falls back to 1080p SDR when the HDR url is blank`() {
        val body =
            """
            {
              "assets": [
                {
                  "url-1080-SDR": "https://example.com/1080-sdr.webm",
                  "url-1080-HDR": "   "
                }
              ]
            }
            """.trimIndent()

        assetFrom(body).uriAtQuality(VideoQuality.VIDEO_1080_HDR)

        assertEquals("https://example.com/1080-sdr.webm", urlSlot.captured)
    }

    @Test
    fun `SDR selections are unchanged by the HDR branches`() {
        val asset = assetFrom(allQualities)

        asset.uriAtQuality(VideoQuality.VIDEO_1080_SDR)
        assertEquals("https://example.com/1080-sdr.webm", urlSlot.captured)

        asset.uriAtQuality(VideoQuality.VIDEO_4K_SDR)
        assertEquals("https://example.com/4k-sdr.webm", urlSlot.captured)

        asset.uriAtQuality(VideoQuality.VIDEO_1080_H264)
        assertEquals("https://example.com/1080-h264.mp4", urlSlot.captured)
    }
}
