package com.neilturner.aerialviews.providers.custom

import com.neilturner.aerialviews.models.enums.AerialMediaType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CustomFeedCsvParserTest {
    @Test
    fun `parses video and image rows with optional descriptions`() {
        val csv =
            """
            https://example.com/video.mp4,A city timelapse
            https://example.com/image.jpg,A lovely city view
            https://example.com/no-description.webp
            """.trimIndent()

        val result = CustomFeedCsvParser.parse(csv)

        assertEquals(3, result.size)
        assertEquals("https://example.com/video.mp4", result[0].url)
        assertEquals("A city timelapse", result[0].description)
        assertEquals(AerialMediaType.VIDEO, result[0].type)
        assertEquals("https://example.com/image.jpg", result[1].url)
        assertEquals("A lovely city view", result[1].description)
        assertEquals(AerialMediaType.IMAGE, result[1].type)
        assertEquals("", result[2].description)
        assertEquals(AerialMediaType.IMAGE, result[2].type)
    }

    @Test
    fun `keeps commas after the first comma in the description`() {
        val result = CustomFeedCsvParser.parse("https://example.com/image.jpg,A city, at sunset, with lights")

        assertEquals(1, result.size)
        assertEquals("A city, at sunset, with lights", result.single().description)
    }

    @Test
    fun `supports quoted whole rows`() {
        val result = CustomFeedCsvParser.parse("\"https://example.com/image.jpg,A lovely city view\"")

        assertEquals(1, result.size)
        assertEquals("https://example.com/image.jpg", result.single().url)
        assertEquals("A lovely city view", result.single().description)
    }

    @Test
    fun `detects extensions before query strings`() {
        val result =
            CustomFeedCsvParser.parse(
                "https://example.com/video.m3u8?token=abc,Live stream\n" +
                    "https://example.com/photo.png?token=abc,Photo",
            )

        assertEquals(2, result.size)
        assertEquals(AerialMediaType.VIDEO, result[0].type)
        assertEquals(AerialMediaType.IMAGE, result[1].type)
    }

    @Test
    fun `ignores headers blank lines and unsupported media rows`() {
        val result =
            CustomFeedCsvParser.parse(
                """
                url,description

                https://example.com/page.html,Unsupported
                https://example.com/photo.jpeg,Supported
                """.trimIndent(),
            )

        assertEquals(1, result.size)
        assertEquals("https://example.com/photo.jpeg", result.single().url)
    }
}
