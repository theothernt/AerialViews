package com.neilturner.aerialviews.ui.core

import android.net.Uri
import com.neilturner.aerialviews.models.enums.AerialMediaSource
import com.neilturner.aerialviews.models.videos.AerialExifMetadata
import com.neilturner.aerialviews.models.videos.AerialMedia
import com.neilturner.aerialviews.models.videos.AerialMediaMetadata
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class VideoMetadataHandlerTest {
    @Test
    fun `apply video metadata uses file values for non immich media`() {
        val media = createMedia(source = AerialMediaSource.LOCAL)
        val metadata =
            ExtractedVideoMetadata(
                title = "Cliffs",
                description = "Ocean lookout",
                date = "2024:02:09 11:22:33",
                offset = "+01:00",
                latitude = 48.8566,
                longitude = 2.3522,
            )

        val changed = applyVideoMetadataToMedia(media, metadata)

        assertTrue(changed)
        assertEquals("Cliffs", media.metadata.title)
        assertEquals("Ocean lookout", media.metadata.exif.description)
        assertEquals("2024:02:09 11:22:33", media.metadata.exif.date)
        assertEquals("+01:00", media.metadata.exif.offset)
        assertEquals(48.8566, media.metadata.exif.latitude)
        assertEquals(2.3522, media.metadata.exif.longitude)
    }

    @Test
    fun `apply video metadata ignores embedded metadata for immich media`() {
        val media =
            createMedia(
                source = AerialMediaSource.IMMICH,
                description = "From Immich",
                date = "2023:08:12 00:00:00",
            )
        val metadata =
            ExtractedVideoMetadata(
                title = "From File",
                date = "2024:02:09 00:00:00",
                latitude = 48.8566,
                longitude = 2.3522,
            )

        val changed = applyVideoMetadataToMedia(media, metadata)

        assertFalse(changed)
        assertEquals("", media.metadata.title)
        assertEquals("From Immich", media.metadata.exif.description)
        assertEquals("2023:08:12 00:00:00", media.metadata.exif.date)
    }

    @Test
    fun `apply video metadata does not fill blank immich values from file`() {
        val media = createMedia(source = AerialMediaSource.IMMICH)
        val metadata =
            ExtractedVideoMetadata(
                title = "From File",
                description = "From File Description",
                date = "2024:01:01 00:00:00",
            )

        val changed = applyVideoMetadataToMedia(media, metadata)

        assertFalse(changed)
        assertEquals("", media.metadata.title)
        assertEquals(null, media.metadata.exif.description)
        assertEquals(null, media.metadata.exif.date)
    }

    @Test
    fun `apply video metadata ignores file metadata for unknown source`() {
        val media = createMedia(source = AerialMediaSource.UNKNOWN)
        val metadata =
            ExtractedVideoMetadata(
                title = "Unknown Source",
                description = "Should not apply",
                date = "2024:02:09 11:22:33",
                latitude = 40.0,
                longitude = -73.0,
            )

        val changed = applyVideoMetadataToMedia(media, metadata)

        assertFalse(changed)
        assertEquals("", media.metadata.title)
        assertEquals(null, media.metadata.exif.description)
        assertEquals(null, media.metadata.exif.date)
        assertEquals(null, media.metadata.exif.latitude)
        assertEquals(null, media.metadata.exif.longitude)
    }

    @Test
    fun `format video metadata for log only includes non-blank values`() {
        val metadata =
            ExtractedVideoMetadata(
                title = "Cliffs",
                description = "  ",
                date = "2024:02:09 00:00:00",
            )

        val log = formatVideoMetadataForLog(metadata)

        assertEquals("title=Cliffs, recordingDate=2024:02:09 00:00:00", log)
    }

    private fun createMedia(
        source: AerialMediaSource,
        description: String? = null,
        date: String? = null,
    ): AerialMedia =
        AerialMedia(
            uri = mockk<Uri>(),
            source = source,
            metadata =
                AerialMediaMetadata(
                    exif =
                        AerialExifMetadata(
                            description = description,
                            date = date,
                        ),
                ),
        )
}
