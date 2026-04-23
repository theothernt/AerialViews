package com.neilturner.aerialviews.ui.core

import android.net.Uri
import androidx.media3.common.MediaMetadata
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
        val media = createMedia(source = AerialMediaSource.UNKNOWN)
        val metadata =
            MediaMetadata
                .Builder()
                .setTitle("Cliffs")
                .setDescription("Ocean lookout")
                .setRecordingYear(2024)
                .setRecordingMonth(2)
                .setRecordingDay(9)
                .build()

        val changed = applyVideoMetadataToMedia(media, metadata)

        assertTrue(changed)
        assertEquals("Cliffs", media.metadata.title)
        assertEquals("Ocean lookout", media.metadata.exif.description)
        assertEquals("2024:02:09 00:00:00", media.metadata.exif.date)
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
            MediaMetadata
                .Builder()
                .setTitle("From File")
                .setRecordingYear(2024)
                .setRecordingMonth(2)
                .setRecordingDay(9)
                .build()

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
            MediaMetadata
                .Builder()
                .setTitle("From File")
                .setDescription("From File Description")
                .setRecordingYear(2024)
                .build()

        val changed = applyVideoMetadataToMedia(media, metadata)

        assertFalse(changed)
        assertEquals("", media.metadata.title)
        assertEquals(null, media.metadata.exif.description)
        assertEquals(null, media.metadata.exif.date)
    }

    @Test
    fun `format video metadata for log only includes non-blank values`() {
        val metadata =
            MediaMetadata
                .Builder()
                .setTitle("Cliffs")
                .setArtist("  ") // Blank, should be ignored
                .setRecordingYear(2024)
                .setRecordingMonth(2)
                .setRecordingDay(9)
                .build()

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
