package com.neilturner.aerialviews.ui.core

import androidx.annotation.OptIn
import androidx.media3.common.Format
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.container.MdtaMetadataEntry
import androidx.media3.container.Mp4LocationData
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

data class ExtractedVideoMetadata(
    val title: String? = null,
    val description: String? = null,
    val date: String? = null,
    val offset: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val city: String? = null,
    val state: String? = null,
    val country: String? = null,
)

internal fun extractVideoMetadataFromTracks(tracks: Tracks): ExtractedVideoMetadata {
    val selectedFormats = mutableListOf<Format>()
    tracks.groups.forEach { group ->
        for (trackIndex in 0 until group.length) {
            if (!group.isTrackSelected(trackIndex)) continue
            selectedFormats.add(group.getTrackFormat(trackIndex))
        }
    }
    return extractVideoMetadataFromTrackFormats(selectedFormats)
}

@OptIn(UnstableApi::class)
internal fun extractVideoMetadataFromTrackFormats(
    formats: List<Format>,
): ExtractedVideoMetadata {
    val builder = MediaMetadata.Builder()
    var creationDate: ParsedExifDate? = null
    var latitude: Double? = null
    var longitude: Double? = null
    var city: String? = null
    var state: String? = null
    var country: String? = null

    formats.forEach { format ->
        val metadata = format.metadata ?: return@forEach

        for (entryIndex in 0 until metadata.length()) {
            val entry = metadata.get(entryIndex)
            entry.populateMediaMetadata(builder)

            when (entry) {
                is Mp4LocationData -> {
                    latitude = latitude ?: entry.latitude.toDouble()
                    longitude = longitude ?: entry.longitude.toDouble()
                }

                is MdtaMetadataEntry -> {
                    val mdtaData = parseMdtaMetadataEntry(entry)
                    if (creationDate == null && mdtaData.date != null) {
                        creationDate = mdtaData.date
                    }
                    if (latitude == null && mdtaData.latitude != null) {
                        latitude = mdtaData.latitude
                    }
                    if (longitude == null && mdtaData.longitude != null) {
                        longitude = mdtaData.longitude
                    }
                    if (city == null && !mdtaData.city.isNullOrBlank()) {
                        city = mdtaData.city
                    }
                    if (state == null && !mdtaData.state.isNullOrBlank()) {
                        state = mdtaData.state
                    }
                    if (country == null && !mdtaData.country.isNullOrBlank()) {
                        country = mdtaData.country
                    }
                }
            }
        }
    }

    val mediaMetadata = builder.build()
    if (creationDate == null) {
        buildMetadataDate(
            year = mediaMetadata.recordingYear,
            month = mediaMetadata.recordingMonth,
            day = mediaMetadata.recordingDay,
        )?.let { date ->
            creationDate = ParsedExifDate(date = date, offset = null)
        }
    }

    return ExtractedVideoMetadata(
        title = mediaMetadata.title.normalize(),
        description = mediaMetadata.description.normalize(),
        date = creationDate?.date,
        offset = creationDate?.offset,
        latitude = latitude,
        longitude = longitude,
        city = city,
        state = state,
        country = country,
    )
}

private data class ParsedExifDate(
    val date: String,
    val offset: String?,
)

private data class ParsedMdtaData(
    val date: ParsedExifDate? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val city: String? = null,
    val state: String? = null,
    val country: String? = null,
)

@OptIn(UnstableApi::class)
private fun parseMdtaMetadataEntry(entry: MdtaMetadataEntry): ParsedMdtaData {
    if (entry.typeIndicator != MdtaMetadataEntry.TYPE_INDICATOR_STRING) {
        return ParsedMdtaData()
    }

    val key = entry.key.lowercase(Locale.ROOT)
    val value = String(entry.value, StandardCharsets.UTF_8).trim()
    if (value.isBlank()) return ParsedMdtaData()

    val isCreationKey =
        key == "com.apple.quicktime.creationdate" ||
            key == "creation_time" ||
            key == "creationdate" ||
            (key.contains("creation") && key.contains("date"))
    if (isCreationKey) {
        parseCreationDate(value)?.let { parsed ->
            return ParsedMdtaData(date = parsed)
        }
    }

    if (key.contains("iso6709")) {
        parseIso6709(value)?.let { (lat, lon) ->
            return ParsedMdtaData(latitude = lat, longitude = lon)
        }
    }

    return when {
        key.contains("city") -> ParsedMdtaData(city = value)
        key.contains("state") || key.contains("province") -> ParsedMdtaData(state = value)
        key.contains("country") -> ParsedMdtaData(country = value)
        else -> ParsedMdtaData()
    }
}

private fun parseCreationDate(rawValue: String): ParsedExifDate? {
    val value = rawValue.trim().removePrefix("UTC ")
    val exifFormat = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss", Locale.ROOT)

    val offsetDateTimeFormats =
        listOf(
            DateTimeFormatter.ISO_OFFSET_DATE_TIME,
            DateTimeFormatter.ISO_ZONED_DATE_TIME,
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ssXXX", Locale.ROOT),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ssX", Locale.ROOT),
        )

    offsetDateTimeFormats.forEach { formatter ->
        try {
            val parsed = OffsetDateTime.parse(value, formatter)
            return ParsedExifDate(
                date = parsed.toLocalDateTime().format(exifFormat),
                offset = parsed.offset.id,
            )
        } catch (_: Exception) {
            // Continue with next format.
        }
    }

    try {
        val parsed = LocalDateTime.parse(value, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        return ParsedExifDate(date = parsed.format(exifFormat), offset = null)
    } catch (_: Exception) {
        // Continue with next format.
    }

    try {
        val parsed = LocalDateTime.parse(value, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT))
        return ParsedExifDate(date = parsed.format(exifFormat), offset = null)
    } catch (_: Exception) {
        // Continue with next format.
    }

    try {
        val parsed = LocalDate.parse(value, DateTimeFormatter.ISO_LOCAL_DATE)
        return ParsedExifDate(
            date = parsed.atStartOfDay().format(exifFormat),
            offset = null,
        )
    } catch (_: Exception) {
        return null
    }
}

private fun parseIso6709(value: String): Pair<Double, Double>? {
    val match = ISO_6709_REGEX.find(value.trim()) ?: return null
    val latitude = match.groupValues[1].toDoubleOrNull() ?: return null
    val longitude = match.groupValues[2].toDoubleOrNull() ?: return null
    return latitude to longitude
}

private fun CharSequence?.normalize(): String? = this?.toString()?.trim()?.takeIf { it.isNotBlank() }

private val ISO_6709_REGEX = Regex("""([+-]\d+(?:\.\d+)?)([+-]\d+(?:\.\d+)?)/?""")
