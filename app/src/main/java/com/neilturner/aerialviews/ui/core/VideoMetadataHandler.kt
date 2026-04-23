package com.neilturner.aerialviews.ui.core

import androidx.media3.common.MediaMetadata
import com.neilturner.aerialviews.models.enums.AerialMediaSource
import com.neilturner.aerialviews.models.videos.AerialMedia

internal fun applyVideoMetadataToMedia(
    media: AerialMedia,
    mediaMetadata: MediaMetadata,
): Boolean {
    var changed = false

    if (supportsEmbeddedVideoFileMetadata(media) && !mediaMetadata.title.isNullOrBlank()) {
        media.metadata.title = mediaMetadata.title.toString()
        changed = true
    }

    if (supportsEmbeddedVideoFileMetadata(media) && !mediaMetadata.description.isNullOrBlank()) {
        media.metadata.exif.description = mediaMetadata.description.toString()
        changed = true
    }

    val shouldIgnoreFileDate =
        media.source == AerialMediaSource.IMMICH &&
            !media.metadata.exif.date
                .isNullOrBlank()
    if (supportsEmbeddedVideoFileMetadata(media) && !shouldIgnoreFileDate) {
        buildMetadataDate(
            year = mediaMetadata.recordingYear,
            month = mediaMetadata.recordingMonth,
            day = mediaMetadata.recordingDay,
        )?.let { extractedDate ->
            media.metadata.exif.date = extractedDate
            changed = true
        }
    }

    return changed
}

internal fun formatVideoMetadataForLog(mediaMetadata: MediaMetadata): String {
    val logEntries = mutableListOf<String>()

    fun addEntry(label: String, value: CharSequence?) {
        value?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let {
            logEntries.add("$label=$it")
        }
    }

    addEntry("title", mediaMetadata.title)
    addEntry("artist", mediaMetadata.artist)
    addEntry("album", mediaMetadata.albumTitle)
    addEntry("albumArtist", mediaMetadata.albumArtist)
    addEntry("description", mediaMetadata.description)

    buildMetadataDate(
        year = mediaMetadata.recordingYear,
        month = mediaMetadata.recordingMonth,
        day = mediaMetadata.recordingDay,
    )?.let { logEntries.add("recordingDate=$it") }

    buildMetadataDate(
        year = mediaMetadata.releaseYear,
        month = mediaMetadata.releaseMonth,
        day = mediaMetadata.releaseDay,
    )?.let { logEntries.add("releaseDate=$it") }

    return logEntries.joinToString(", ")
}

private fun buildMetadataDate(
    year: Int?,
    month: Int?,
    day: Int?,
): String? {
    val safeYear = year ?: return null
    val safeMonth = month?.toString()?.padStart(2, '0') ?: "01"
    val safeDay = day?.toString()?.padStart(2, '0') ?: "01"
    return "$safeYear:$safeMonth:$safeDay 00:00:00"
}

internal fun supportsEmbeddedVideoFileMetadata(media: AerialMedia): Boolean =
    media.source in
        setOf(
            AerialMediaSource.UNKNOWN,
            AerialMediaSource.LOCAL,
            AerialMediaSource.SAMBA,
            AerialMediaSource.WEBDAV,
        )
