package com.neilturner.aerialviews.ui.core

import androidx.media3.common.Format
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Metadata
import androidx.media3.common.MimeTypes
import androidx.media3.container.MdtaMetadataEntry
import androidx.media3.container.Mp4LocationData
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.nio.charset.StandardCharsets

internal class TrackMetadataExtractorTest {
    @Test
    fun `extracts title and description from track metadata entries`() {
        val extracted =
            extractVideoMetadataFromTrackFormats(
                createFormats(
                    BuilderPopulatingEntry(title = "Sea Cliffs", description = "Atlantic coast"),
                ),
            )

        assertEquals("Sea Cliffs", extracted.title)
        assertEquals("Atlantic coast", extracted.description)
    }

    @Test
    fun `extracts creation date and offset from mdta entry`() {
        val extracted =
            extractVideoMetadataFromTrackFormats(
                createFormats(
                    MdtaMetadataEntry(
                        "com.apple.quicktime.creationdate",
                        "2024-02-09T11:22:33+01:00".toByteArray(StandardCharsets.UTF_8),
                        MdtaMetadataEntry.TYPE_INDICATOR_STRING,
                    ),
                ),
            )

        assertEquals("2024:02:09 11:22:33", extracted.date)
        assertEquals("+01:00", extracted.offset)
    }

    @Test
    fun `extracts location from mp4 location metadata entry`() {
        val extracted =
            extractVideoMetadataFromTrackFormats(
                createFormats(
                    Mp4LocationData(48.8566f, 2.3522f),
                ),
            )

        assertEquals(48.8566, extracted.latitude ?: 0.0, 0.0001)
        assertEquals(2.3522, extracted.longitude ?: 0.0, 0.0001)
    }

    @Test
    fun `returns empty metadata when no selected track metadata is available`() {
        val extracted = extractVideoMetadataFromTrackFormats(emptyList())

        assertNull(extracted.title)
        assertNull(extracted.description)
        assertNull(extracted.date)
        assertNull(extracted.offset)
        assertNull(extracted.latitude)
        assertNull(extracted.longitude)
    }

    private fun createFormats(vararg entries: Metadata.Entry): List<Format> =
        listOf(
            Format
                .Builder()
                .setSampleMimeType(MimeTypes.VIDEO_H264)
                .setMetadata(entries.takeIf { it.isNotEmpty() }?.let { Metadata(*it) })
                .build(),
        )
}

private class BuilderPopulatingEntry(
    private val title: String? = null,
    private val description: String? = null,
) : Metadata.Entry {
    override fun populateMediaMetadata(builder: MediaMetadata.Builder) {
        if (!title.isNullOrBlank()) builder.setTitle(title)
        if (!description.isNullOrBlank()) builder.setDescription(description)
    }
}
