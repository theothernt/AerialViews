package com.neilturner.aerialviews.ui.core

import com.neilturner.aerialviews.models.enums.AerialMediaSource
import com.neilturner.aerialviews.models.videos.AerialMedia

internal fun applyVideoMetadataToMedia(
    media: AerialMedia,
    extractedMetadata: ExtractedVideoMetadata,
): Boolean {
    var changed = false

    if (supportsEmbeddedVideoFileMetadata(media) && !extractedMetadata.title.isNullOrBlank()) {
        media.metadata.title = extractedMetadata.title
        changed = true
    }

    val shouldIgnoreFileDate =
        media.source == AerialMediaSource.IMMICH &&
            !media.metadata.exif.date
                .isNullOrBlank()
    if (supportsEmbeddedVideoFileMetadata(media) && !shouldIgnoreFileDate) {
        extractedMetadata.date?.let { extractedDate ->
            media.metadata.exif.date = extractedDate
            if (!extractedMetadata.offset.isNullOrBlank()) {
                media.metadata.exif.offset = extractedMetadata.offset
            }
            changed = true
        }
    }

    if (supportsEmbeddedVideoFileMetadata(media)) {
        if (extractedMetadata.latitude != null) {
            media.metadata.exif.latitude = extractedMetadata.latitude
            changed = true
        }
        if (extractedMetadata.longitude != null) {
            media.metadata.exif.longitude = extractedMetadata.longitude
            changed = true
        }
        if (!extractedMetadata.city.isNullOrBlank()) {
            media.metadata.exif.city = extractedMetadata.city
            changed = true
        }
        if (!extractedMetadata.state.isNullOrBlank()) {
            media.metadata.exif.state = extractedMetadata.state
            changed = true
        }
        if (!extractedMetadata.country.isNullOrBlank()) {
            media.metadata.exif.country = extractedMetadata.country
            changed = true
        }
    }

    return changed
}

internal fun formatVideoMetadataForLog(metadata: ExtractedVideoMetadata): String {
    val logEntries = mutableListOf<String>()

    fun addEntry(
        label: String,
        value: String?,
    ) {
        value?.trim()?.takeIf { it.isNotBlank() }?.let {
            logEntries.add("$label=$it")
        }
    }

    addEntry("title", metadata.title)
    addEntry("description", metadata.description)
    addEntry("recordingDate", metadata.date)
    addEntry("recordingOffset", metadata.offset)
    metadata.latitude?.let { logEntries.add("latitude=$it") }
    metadata.longitude?.let { logEntries.add("longitude=$it") }
    addEntry("city", metadata.city)
    addEntry("state", metadata.state)
    addEntry("country", metadata.country)

    return logEntries.joinToString(", ")
}

internal fun buildMetadataDate(
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
            AerialMediaSource.LOCAL,
            AerialMediaSource.SAMBA,
            AerialMediaSource.WEBDAV,
        )
